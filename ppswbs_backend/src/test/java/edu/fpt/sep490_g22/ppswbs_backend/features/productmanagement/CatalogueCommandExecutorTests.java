package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueCommandReceipt;
import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuditWriter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor.CommandKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor.CommandResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T010: replay, digest conflict, concurrent duplicate and transaction ownership of the command executor. */
class CatalogueCommandExecutorTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private CatalogueCommandReceiptRepository receipts;
    private CatalogueAuditWriter audit;
    private CatalogueCommandExecutor executor;
    private final UUID commandId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        receipts = mock(CatalogueCommandReceiptRepository.class);
        audit = mock(CatalogueAuditWriter.class);
        executor = new CatalogueCommandExecutor(receipts, transactions, objectMapper, audit);
    }

    @Test
    void firstExecutionRunsWorkAndStoresReceiptInOneTransaction() {
        when(receipts.findByActorIdAndActionAndCommandId(anyString(), anyString(), any())).thenReturn(Optional.empty());
        AtomicInteger runs = new AtomicInteger();

        var outcome = executor.execute(key("d1"), () -> {
            runs.incrementAndGet();
            return new CommandResult(201, "product", "123", 0L, Map.of("productId", "123"));
        });

        assertThat(runs).hasValue(1);
        assertThat(outcome.replayed()).isFalse();
        assertThat(outcome.status()).isEqualTo(201);
        assertThat(outcome.body().get("productId").asText()).isEqualTo("123");
        verify(receipts).saveAndFlush(any(CatalogueCommandReceipt.class));
        assertThat(transactions.rollbacks).isZero();
    }

    @Test
    void sameKeyAndDigestReplaysWithoutRunningWorkAgain() {
        when(receipts.findByActorIdAndActionAndCommandId("staff-1", "product.create", commandId))
                .thenReturn(Optional.of(receipt("d1")));

        var outcome = executor.execute(key("d1"), () -> {
            throw new AssertionError("work must not run on replay");
        });

        assertThat(outcome.replayed()).isTrue();
        assertThat(outcome.body().get("productId").asText()).isEqualTo("123");
        verify(receipts, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyWithDifferentPayloadIsIdempotencyKeyReused() {
        when(receipts.findByActorIdAndActionAndCommandId("staff-1", "product.create", commandId))
                .thenReturn(Optional.of(receipt("d1")));

        assertThatThrownBy(() -> executor.execute(key("other-digest"), () -> null))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void concurrentLoserRollsBackThenReadsTheWinnersReceipt() {
        when(receipts.findByActorIdAndActionAndCommandId("staff-1", "product.create", commandId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(receipt("d1")));
        when(receipts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_catalogue_command_receipts_key"));

        var outcome = executor.execute(key("d1"),
                () -> new CommandResult(201, "product", "999", 0L, Map.of("productId", "999")));

        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(outcome.replayed()).isTrue();
        assertThat(outcome.resourceId()).isEqualTo("123");
    }

    @Test
    void businessFailureRollsBackAndStoresNoReceipt() {
        when(receipts.findByActorIdAndActionAndCommandId(anyString(), anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.execute(key("d1"), () -> {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        })).isInstanceOf(CatalogueException.class);

        assertThat(transactions.rollbacks).isEqualTo(1);
        verify(receipts, never()).saveAndFlush(any());
    }

    @Test
    void businessRejectionIsAuditedAfterTheRollbackWithoutRequestValues() {
        when(receipts.findByActorIdAndActionAndCommandId(anyString(), anyString(), any())).thenReturn(Optional.empty());
        AtomicInteger rollbacksWhenAudited = new AtomicInteger(-1);
        when(audit.recordRejection(any())).thenAnswer(call -> {
            rollbacksWhenAudited.set(transactions.rollbacks);
            return null;
        });

        assertThatThrownBy(() -> executor.execute(key("d1"), () -> {
            throw new CatalogueException(CatalogueErrorCode.VALIDATION_ERROR, List.of(
                    new ErrorResponse.FieldError("name", "secret typed value"),
                    new ErrorResponse.FieldError("name", "again"),
                    new ErrorResponse.FieldError("variants[0].size", "out of range")));
        })).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR));

        ArgumentCaptor<CatalogueAuditWriter.Entry> entry = ArgumentCaptor.forClass(CatalogueAuditWriter.Entry.class);
        verify(audit).recordRejection(entry.capture());
        assertThat(rollbacksWhenAudited).hasValue(1);
        assertThat(entry.getValue().actorId()).isEqualTo("staff-1");
        assertThat(entry.getValue().action()).isEqualTo("product.create");
        assertThat(entry.getValue().resourceType()).isEqualTo(CatalogueCommandExecutor.REJECTED_RESOURCE_TYPE);
        assertThat(entry.getValue().resourceId()).isEqualTo(commandId.toString());
        assertThat(entry.getValue().safeChanges()).containsEntry("code", "VALIDATION_ERROR")
                .containsEntry("fields", List.of("name", "variants[0].size"));
        assertThat(entry.getValue().safeChanges().toString()).doesNotContain("secret typed value");
        verify(audit, never()).recordSuccess(any());
    }

    @Test
    void reusedKeyWithAnotherDigestIsAuditedAsRejected() {
        when(receipts.findByActorIdAndActionAndCommandId("staff-1", "product.create", commandId))
                .thenReturn(Optional.of(receipt("d1")));

        assertThatThrownBy(() -> executor.execute(key("other"), () -> null))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED));
        verify(audit).recordRejection(any());
    }

    @Test
    void dependencyFailureIsNotABusinessRejection() {
        when(receipts.findByActorIdAndActionAndCommandId(anyString(), anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.execute(key("d1"), () -> {
            throw new CatalogueException(CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
        })).isInstanceOf(CatalogueException.class);
        verify(audit, never()).recordRejection(any());
    }

    @Test
    void auditFailureNeverReplacesTheOriginalRejection() {
        when(receipts.findByActorIdAndActionAndCommandId(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(audit.recordRejection(any())).thenThrow(new IllegalStateException("audit down"));

        assertThatThrownBy(() -> executor.execute(key("d1"), () -> {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        })).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.STALE_VERSION));
    }

    @Test
    void precheckRejectionIsAuditedLikeACommandRejection() {
        // T144: the branch scope check of the management API runs before execute and must still leave a REJECTED entry.
        assertThatThrownBy(() -> executor.precheck("staff-1", "product.edit", commandId, () -> {
            throw new CatalogueException(CatalogueErrorCode.BRANCH_OUT_OF_SCOPE);
        })).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.BRANCH_OUT_OF_SCOPE));

        ArgumentCaptor<CatalogueAuditWriter.Entry> entry = ArgumentCaptor.forClass(CatalogueAuditWriter.Entry.class);
        verify(audit).recordRejection(entry.capture());
        assertThat(entry.getValue().actorId()).isEqualTo("staff-1");
        assertThat(entry.getValue().action()).isEqualTo("product.edit");
        assertThat(entry.getValue().resourceType()).isEqualTo(CatalogueCommandExecutor.REJECTED_RESOURCE_TYPE);
        assertThat(entry.getValue().resourceId()).isEqualTo(commandId.toString());
        assertThat(entry.getValue().safeChanges()).containsEntry("code", "BRANCH_OUT_OF_SCOPE");
        verify(receipts, never()).saveAndFlush(any());
    }

    @Test
    void passingPrecheckWritesNothing() {
        AtomicInteger runs = new AtomicInteger();
        executor.precheck("manager-1", "product.edit", commandId, runs::incrementAndGet);
        assertThat(runs).hasValue(1);
        verify(audit, never()).recordRejection(any());
        assertThat(transactions.rollbacks).isZero();
    }

    @Test
    void precheckRefusesToRunInsideATransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> executor.precheck("staff-1", "product.edit", commandId, () -> { }))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void refusesToJoinAnOuterTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> executor.execute(key("d1"), () -> null))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void digestIgnoresMapKeyOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "Nhẫn");
        first.put("expectedVersion", 3);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("expectedVersion", 3);
        second.put("name", "Nhẫn");
        assertThat(CatalogueCommandExecutor.digest(first)).isEqualTo(CatalogueCommandExecutor.digest(second))
                .hasSize(64);
        second.put("name", "Nhẫn bạc");
        assertThat(CatalogueCommandExecutor.digest(first)).isNotEqualTo(CatalogueCommandExecutor.digest(second));
    }

    private CommandKey key(String digest) {
        return new CommandKey("staff-1", "product.create", commandId, digest);
    }

    private CatalogueCommandReceipt receipt(String digest) {
        return new CatalogueCommandReceipt("staff-1", "product.create", commandId, digest, "product", "123", 201, 0L,
                "{\"productId\":\"123\"}", Instant.parse("2026-10-26T00:00:00Z"));
    }

    /** Minimal transaction manager that only counts commits and rollbacks. */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
