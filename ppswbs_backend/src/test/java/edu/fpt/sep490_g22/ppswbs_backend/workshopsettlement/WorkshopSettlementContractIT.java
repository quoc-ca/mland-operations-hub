package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementHandoffIdempotencyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.HandoffIdempotencyStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementHandoffIdempotency;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.CancellationHandoffOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.OpenCancellationHandoffCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementFacadeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopSettlementContractIT {
    @Mock SettlementCancellationCaseRepository caseRepository;
    @Mock SettlementHandoffIdempotencyRepository idempotencyRepository;
    @Mock SettlementEvidenceConclusionRepository evidenceRepository;
    @Mock SettlementFinalBillRepository billRepository;
    @Mock SettlementChargeRepository chargeRepository;

    @Test
    void acceptedHandoffUsesTypedBoundaryAndPersistsNoEntityOutsideModule() {
        when(idempotencyRepository.findByOperationReference("OP-1")).thenReturn(Optional.empty());
        WorkshopSettlementFacadeImpl facade = new WorkshopSettlementFacadeImpl(
                caseRepository, idempotencyRepository, new ObjectMapper(), evidenceRepository, billRepository, chargeRepository);

        CancellationHandoffOutcome outcome = facade.openCancellationHandoff(
                new OpenCancellationHandoffCommand("SES-1", List.of("BK-1"),
                        "FACILITY_ISSUE", "OP-1", "CORR-1"));

        assertThat(outcome.status()).isEqualTo(CancellationHandoffOutcome.Status.ACCEPTED);
        assertThat(outcome.cancellationCaseReference()).startsWith("SETTLE-CASE-");
        verify(caseRepository).save(any());
        verify(idempotencyRepository).saveAndFlush(any());
    }

    @Test
    void sameOperationAndDifferentSemanticContentReturnsTypedConflict() {
        SettlementHandoffIdempotency existing = SettlementHandoffIdempotency.builder()
                .operationReference("OP-1")
                .requestFingerprint("different")
                .correlationReference("CORR-1")
                .lifecycleStatus(HandoffIdempotencyStatus.COMPLETED)
                .cancellationCaseReference("CASE-1")
                .build();
        when(idempotencyRepository.findByOperationReference("OP-1")).thenReturn(Optional.of(existing));
        WorkshopSettlementFacadeImpl facade = new WorkshopSettlementFacadeImpl(
                caseRepository, idempotencyRepository, new ObjectMapper(), evidenceRepository, billRepository, chargeRepository);

        CancellationHandoffOutcome outcome = facade.openCancellationHandoff(
                new OpenCancellationHandoffCommand("SES-1", List.of("BK-1"),
                        "FACILITY_ISSUE", "OP-1", "CORR-1"));

        assertThat(outcome.status()).isEqualTo(CancellationHandoffOutcome.Status.REJECTED);
        assertThat(outcome.rejectionReason()).isEqualTo("IDEMPOTENCY_CONFLICT");
        verifyNoInteractions(caseRepository);
    }
}
