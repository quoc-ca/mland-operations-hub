package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.FacadeCommandIdempotencyRecordRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.IdempotencyLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsRestIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopIdempotencyConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopIdempotencyInProgressException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationsRestIdempotencyServiceTest {

    @Mock FacadeCommandIdempotencyManager manager;
    @Mock FacadeCommandIdempotencyRecordRepository repository;

    private OperationsRestIdempotencyService service() {
        return new OperationsRestIdempotencyService(manager, repository, new ObjectMapper());
    }

    @Test
    void sameKeyAndSemanticCommandReplaysOriginalResponseWithoutSecondMutation() {
        when(repository.findFirstByOperationReference("key-1")).thenReturn(Optional.empty());
        when(manager.acquireClaim(anyString(), eq("key-1"), anyString(), anyString()))
                .thenReturn(FacadeCommandIdempotencyManager.ClaimResult.acquired(7L, 1L))
                .thenReturn(FacadeCommandIdempotencyManager.ClaimResult.alreadyCompleted(completed("key-1", "{\"value\":\"ok\"}")));

        AtomicInteger mutations = new AtomicInteger();
        assertThat(service().execute("key-1", "REST:PACKAGE_CREATE", "package", new Command("A"), Result.class,
                () -> { mutations.incrementAndGet(); return new Result("ok"); }).value()).isEqualTo("ok");

        when(repository.findFirstByOperationReference("key-1")).thenReturn(Optional.of(completed("key-1", "{\"value\":\"ok\"}")));
        assertThat(service().execute("key-1", "REST:PACKAGE_CREATE", "package", new Command("A"), Result.class,
                () -> { mutations.incrementAndGet(); return new Result("wrong"); }).value()).isEqualTo("ok");
        assertThat(mutations).hasValue(1);
        verify(manager, times(1)).completeClaim(eq(7L), eq(1L), any(), isNull(), eq("{\"value\":\"ok\"}"));
    }

    @Test
    void sameKeyWithChangedSemanticCommandIsRejected() {
        FacadeCommandIdempotencyRecord existing = completed("key-2", "{\"value\":\"ok\"}");
        existing.setFacadeAction("REST:OTHER");
        existing.setSemanticFingerprint("different");
        when(repository.findFirstByOperationReference("key-2")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().execute("key-2", "REST:PACKAGE_CREATE", "package", new Command("A"), Result.class,
                () -> new Result("new"))).isInstanceOf(WorkshopIdempotencyConflictException.class);
        verifyNoInteractions(manager);
    }

    @Test
    void activeEquivalentClaimReturnsTypedInProgress() {
        when(repository.findFirstByOperationReference("key-3")).thenReturn(Optional.empty());
        when(manager.acquireClaim(anyString(), eq("key-3"), anyString(), anyString()))
                .thenReturn(FacadeCommandIdempotencyManager.ClaimResult.inProgress());

        assertThatThrownBy(() -> service().execute("key-3", "REST:SESSION_UPDATE", "session/1", new Command("A"), Result.class,
                () -> new Result("new"))).isInstanceOf(WorkshopIdempotencyInProgressException.class);
    }

    @Test
    void failedMutationAbandonsClaimSoClientCanRetrySameCommand() {
        when(repository.findFirstByOperationReference("key-4")).thenReturn(Optional.empty());
        when(manager.acquireClaim(anyString(), eq("key-4"), anyString(), anyString()))
                .thenReturn(FacadeCommandIdempotencyManager.ClaimResult.acquired(9L, 2L));
        RuntimeException failure = new RuntimeException("business failure");

        assertThatThrownBy(() -> service().execute("key-4", "REST:SLOT_UPDATE", "slot/1", new Command("A"), Result.class,
                () -> { throw failure; })).isSameAs(failure);
        verify(manager).abandonClaim(9L, 2L);
        verify(manager, never()).completeClaim(anyLong(), anyLong(), any(), any(), any());
    }

    private FacadeCommandIdempotencyRecord completed(String key, String payload) {
        return FacadeCommandIdempotencyRecord.builder().id(1L).facadeAction("REST:PACKAGE_CREATE")
                .operationReference(key).correlationReference("scope").semanticFingerprint("unused")
                .lifecycleStatus(IdempotencyLifecycleStatus.COMPLETED).claimVersion(1L).responsePayload(payload).build();
    }

    record Command(String name) {}
    record Result(String value) {}
}
