package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.PreviousLocationAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreviousLocationAddOnServiceTest {

    private static final String TICKET = "GRP-01-L1-T1";

    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final SettlementFinalBillRepository bills = mock(SettlementFinalBillRepository.class);
    private final SettlementChargeRepository charges = mock(SettlementChargeRepository.class);
    private final WorkshopSettlementAuthorizationService authorization =
            mock(WorkshopSettlementAuthorizationService.class);
    private PreviousLocationAddOnService service;

    private final CurrentAccountActor manager = new CurrentAccountActor(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, false, List.of());

    @BeforeEach
    void setUp() {
        service = new PreviousLocationAddOnService(authorization, booking, operations, bills, charges);
        when(authorization.requireManager(any(CurrentAccountActor.class))).thenAnswer(i -> i.getArgument(0));
        // The ticket is now at location 20.
        when(booking.getTicketFolioContext(TICKET)).thenReturn(Optional.of(new TicketFolioContext(TICKET, "GRP-01", "L1",
                "7", 20L, List.of(), 100L, "ops", Instant.now(), 0L, "L1-T1", 0L)));
        when(bills.findByBookingReference(TICKET)).thenReturn(Optional.of(
                SettlementFinalBill.builder().billReference("BILL-1").bookingReference(TICKET).build()));
    }

    private SettlementCharge charge(String ref, String category, String reason) {
        return SettlementCharge.builder().chargeReference(ref).sourceCategory(category).description("d-" + ref)
                .amountVnd(50_000L).reasonReference(reason).lifecycleStatus("CONFIRMED").build();
    }

    @Test
    @DisplayName("T187: an add-on sold at the old location is listed; one at the current location and a variable charge are not")
    void listsOnlyAddOnsOfAnotherLocation() {
        when(charges.findByBillReference("BILL-1")).thenReturn(List.of(
                charge("C-OLD", "CATALOGUE_ADD_ON", "CATALOGUE-ADDON:AO-A"),
                charge("C-NEW", "CATALOGUE_ADD_ON", "CATALOGUE-ADDON:AO-B"),
                charge("C-VAR", "VARIABLE", "something")));
        when(operations.addOnLocation("AO-A")).thenReturn(10L);
        when(operations.addOnLocation("AO-B")).thenReturn(20L);

        var result = service.list(TICKET, manager);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chargeReference()).isEqualTo("C-OLD");
        assertThat(result.get(0).addOnLocationReference()).isEqualTo(10L);
        assertThat(result.get(0).ticketLocationReference()).isEqualTo(20L);
        assertThat(result.get(0).amount()).isEqualTo("50000");
    }

    @Test
    @DisplayName("T187: only a Manager may read it")
    void staffAndMembersAreRefused() {
        CurrentAccountActor staff = new CurrentAccountActor(2L, ActorRole.STAFF, ActorStatus.ACTIVE, false, List.of(20L));
        when(authorization.requireManager(staff)).thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));

        assertThatThrownBy(() -> service.list(TICKET, staff)).isInstanceOf(WorkshopSettlementAuthorizationException.class);
    }
}
