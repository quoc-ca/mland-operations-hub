package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkshopFinalBillCustomerAccessTest {
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementChargeRepository charges = mock(SettlementChargeRepository.class);
    private final SettlementEvidenceConclusionRepository evidence = mock(SettlementEvidenceConclusionRepository.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final WorkshopSettlementAuthorizationService authorization = mock(WorkshopSettlementAuthorizationService.class);
    private final SettlementIdempotencyService idempotency = mock(SettlementIdempotencyService.class);
    private final FinalBookingBillService billService = mock(FinalBookingBillService.class);
    private final VariableChargeService variableChargeService = mock(VariableChargeService.class);
    private final CatalogueAddOnService catalogueAddOnService = mock(CatalogueAddOnService.class);

    private WorkshopFinalBillApiService service;

    @BeforeEach
    void setUp() {
        service = new WorkshopFinalBillApiService(bills, charges, evidence, booking, operations,
                authorization, idempotency, billService, variableChargeService, catalogueAddOnService);
    }

    @Test
    void guestMustSupplyExactlyOneProofAndDeniedProofIsIndistinguishable() {
        String email = "guest-private@example.test";
        String phone = "0900000000";
        assertThrows(WorkshopSettlementNotFoundException.class,
                () -> service.read("BK-1", null, null, null));
        assertThrows(WorkshopSettlementNotFoundException.class,
                () -> service.read("BK-1", email, phone, null));
        verifyNoInteractions(booking);

        when(booking.verifyCustomerAccess("BK-1", null, email, null))
                .thenReturn(new CustomerBookingAccessDecision(false));
        WorkshopSettlementNotFoundException denied = assertThrows(WorkshopSettlementNotFoundException.class,
                () -> service.read("BK-1", email, null, null));
        assertEquals("RESOURCE_NOT_FOUND", denied.getMessage());
        verify(booking).verifyCustomerAccess("BK-1", null, email, null);
        verifyNoInteractions(bills, charges, evidence);
    }

    @Test
    void memberOwnershipDecisionUsesServerActorAndDoesNotAcceptGuestProof() {
        Authentication authentication = mock(Authentication.class);
        CurrentAccountActor actor = new CurrentAccountActor(42L, ActorRole.MEMBER, ActorStatus.ACTIVE,
                true, List.of());
        when(authentication.getPrincipal()).thenReturn(actor);
        when(booking.verifyCustomerAccess("BK-2", 42L, null, null))
                .thenReturn(new CustomerBookingAccessDecision(false));

        assertThrows(WorkshopSettlementNotFoundException.class,
                () -> service.read("BK-2", "untrusted@example.test", null, authentication));
        verify(booking).verifyCustomerAccess("BK-2", 42L, null, null);
        verifyNoInteractions(bills, charges, evidence);
    }

    @Test
    void authenticatedStaffCannotFallBackToGuestContactProof() {
        Authentication authentication = mock(Authentication.class);
        CurrentAccountActor actor = new CurrentAccountActor(7L, ActorRole.STAFF, ActorStatus.ACTIVE,
                true, List.of());
        when(authentication.getPrincipal()).thenReturn(actor);

        assertThrows(WorkshopSettlementNotFoundException.class,
                () -> service.read("BK-3", "guest-private@example.test", null, authentication));
        verifyNoInteractions(booking, bills, charges, evidence);
    }
}
