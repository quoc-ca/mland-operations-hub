package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.FacadeCommandIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.IdempotencyLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Regression: group ticket cancel sent a 74-char operation reference into a VARCHAR(64) column and got HTTP 409. */
@ExtendWith(MockitoExtension.class)
class FacadeCommandIdempotencyManagerTest {

    private static final String LONG_OP_REF =
            "CANCEL-GRP-2A7021232623405FAAE9-L0-T1-bccd6566-6b6e-48e0-919b-03af8a4f32d6";

    @Mock FacadeCommandIdempotencyRecordRepository repository;
    @Mock PlatformTransactionManager transactionManager;

    private FacadeCommandIdempotencyManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        manager = new FacadeCommandIdempotencyManager(repository, transactionManager);
        ReflectionTestUtils.setField(manager, "leaseDuration", Duration.ofMinutes(5));
    }

    @Test
    void longOperationReferenceIsLongerThanLegacyColumn() {
        assertThat(LONG_OP_REF.length()).isEqualTo(74);
    }

    @Test
    void entityAndMigrationAcceptOperationReferenceOf74Chars() throws Exception {
        int entityLength = FacadeCommandIdempotencyRecord.class
                .getDeclaredField("operationReference").getAnnotation(Column.class).length();
        assertThat(entityLength).isGreaterThanOrEqualTo(LONG_OP_REF.length());

        try (InputStream in = getClass().getResourceAsStream(
                "/db/migration/V185__widen_facade_idempotency_operation_reference.sql")) {
            assertThat(in).as("migration V185 must exist").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertThat(sql).contains("OPERATION_REFERENCE").contains("VARCHAR(128)");
        }
    }

    @Test
    void realDuplicateKeyIsStillTreatedAsConcurrentRace() {
        FacadeCommandIdempotencyRecord existing = FacadeCommandIdempotencyRecord.builder()
                .id(1L).facadeAction("CANCEL_TICKET").operationReference(LONG_OP_REF)
                .semanticFingerprint("fp").lifecycleStatus(IdempotencyLifecycleStatus.IN_PROGRESS)
                .claimVersion(1L).leaseExpiresAt(Instant.now().plusSeconds(60)).build();
        when(repository.findByFacadeActionAndOperationReference("CANCEL_TICKET", LONG_OP_REF))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        var result = manager.acquireClaim("CANCEL_TICKET", LONG_OP_REF, "corr", "fp");

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.IN_PROGRESS);
    }

    @Test
    void integrityErrorWithoutExistingRecordIsRethrownNotReportedAsRace() {
        when(repository.findByFacadeActionAndOperationReference("CANCEL_TICKET", LONG_OP_REF))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Data too long for column"));

        assertThatThrownBy(() -> manager.acquireClaim("CANCEL_TICKET", LONG_OP_REF, "corr", "fp"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Data too long");
    }
}
