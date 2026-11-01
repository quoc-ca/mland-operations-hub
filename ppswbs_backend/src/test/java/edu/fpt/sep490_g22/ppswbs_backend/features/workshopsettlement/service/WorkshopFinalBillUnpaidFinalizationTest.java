package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FinalizationCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkshopFinalBillUnpaidFinalizationTest {
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementChargeRepository charges = mock(SettlementChargeRepository.class);
    private final SettlementEvidenceConclusionRepository evidence = mock(SettlementEvidenceConclusionRepository.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private final FinalBookingBillService billService = mock(FinalBookingBillService.class);
    private final VariableChargeService variableCharges = mock(VariableChargeService.class);
    private final CatalogueAddOnService catalogueAddOns = mock(CatalogueAddOnService.class);

    @Test
    void bothManagerAndStaffAreDeniedWhileBalanceRemains() {
        SettlementFinalBill bill = openBill();
        when(bills.findByBookingReference("BK-1")).thenReturn(Optional.of(bill));
        when(booking.confirmedProjection("BK-1")).thenReturn(confirmedProjection());
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc("BILL-1", "CONFIRMED"))
                .thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion("BK-1", "FINAL_PAYMENT_VERIFIED"))
                .thenReturn(List.of());

        for (ActorRole role : List.of(ActorRole.MANAGER, ActorRole.STAFF)) {
            Authentication authentication = authentication(role);
            WorkshopFinalBillApiService service = service();

            assertThatThrownBy(() -> service.finalizeBill("BK-1", "key-" + role, null,
                    new FinalizationCommandDto(), authentication))
                    .isInstanceOf(WorkshopSettlementConflictException.class)
                    .hasMessage("OUTSTANDING_BALANCE");
        }

        verify(billService, never()).finalizeAuthorized(anyString(), anyLong());
        verifyNoInteractions(idempotency);
        assertThat(bill.getLifecycleStatus()).isEqualTo("OPEN");
    }

    private WorkshopFinalBillApiService service() {
        return new WorkshopFinalBillApiService(bills, charges, evidence, booking, operations,
                authorization, idempotency, billService, variableCharges, catalogueAddOns);
    }

    private static Authentication authentication(ActorRole role) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new CurrentAccountActor(
                7L, role, ActorStatus.ACTIVE, true, List.of(1L)));
        return authentication;
    }

    private static ConfirmedBookingFinancialProjection confirmedProjection() {
        return new ConfirmedBookingFinancialProjection("PKG-1", 100_000L, 80_000L,
                40_000L, 40_000L, 1, List.of("SESSION-1"), 1L);
    }

    private static SettlementFinalBill openBill() {
        return SettlementFinalBill.builder().billReference("BILL-1").bookingReference("BK-1")
                .postPromotionAmountVnd(80_000L).acceptedDepositAmountVnd(40_000L)
                .remainingAmountVnd(40_000L).currencyCode("VND").lifecycleStatus("OPEN").build();
    }
}
