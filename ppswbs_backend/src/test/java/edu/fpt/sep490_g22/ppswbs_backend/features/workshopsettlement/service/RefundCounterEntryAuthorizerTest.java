package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 003.3 T152 (FR-040, FR-032, CHK025): who may enter a refund account at the counter. */
class RefundCounterEntryAuthorizerTest {

    private static final String TICKET = "GRP-1-L0-T1";
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final WorkshopBookingSettlementAdapter booking = mock(WorkshopBookingSettlementAdapter.class);
    private final BankRefundService bankRefunds = mock(BankRefundService.class);
    private RefundCounterEntryAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        var authorization = new WorkshopSettlementAuthorizationService(mock(AccountFacade.class),
                mock(SettlementBusinessAuditRepository.class), operations);
        authorizer = new RefundCounterEntryAuthorizer(authorization, booking, bankRefunds);
        when(booking.getTicketFolioContext(TICKET)).thenReturn(Optional.of(new TicketFolioContext(TICKET, "GRP-1",
                "LINE-1", "PKG-1", 7L, List.of("S-1"), null)));
        when(operations.staffAssignedToBooking(11L, "LINE-1")).thenReturn(true);
        givenRequest(RefundReason.OTA_ADDON_OVERCHARGE, TICKET, "GRP-1");
    }

    private void givenRequest(RefundReason reason, String bookingReference, String group) {
        when(bankRefunds.require("REFUND-1")).thenReturn(SettlementRefundRequest.builder()
                .requestReference("REFUND-1").bookingReference(bookingReference).groupReference(group)
                .reason(reason).sourceReference("src").amountVnd(1).build());
    }

    private static CurrentAccountActor staff(long id, ActorStatus status, Long... locations) {
        return new CurrentAccountActor(id, ActorRole.STAFF, status, true, List.of(locations));
    }

    @Test
    void anAssignedStaffMemberInScopeAndAnyManagerMayEnterTheAccount() {
        var assigned = staff(11L, ActorStatus.ACTIVE, 7L);
        assertThat(authorizer.requireCounterEntry(assigned, "REFUND-1")).isSameAs(assigned);
        var manager = new CurrentAccountActor(22L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        assertThat(authorizer.requireCounterEntry(manager, "REFUND-1")).isSameAs(manager);
    }

    @Test
    void anUnassignedOutOfBranchOrSuspendedStaffMemberIsRefused() {
        assertThatThrownBy(() -> authorizer.requireCounterEntry(staff(12L, ActorStatus.ACTIVE, 7L), "REFUND-1"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessage("SESSION_ASSIGNMENT_REQUIRED");
        assertThatThrownBy(() -> authorizer.requireCounterEntry(staff(11L, ActorStatus.ACTIVE, 9L), "REFUND-1"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessage("LOCATION_SCOPE_DENIED");
        assertThatThrownBy(() -> authorizer.requireCounterEntry(staff(11L, ActorStatus.SUSPENDED, 7L), "REFUND-1"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessage("ACCOUNT_NOT_ACTIVE");
    }

    @Test
    void aGroupLevelRequestIsRefusedAtTheCounterForEveryRole() {
        givenRequest(RefundReason.LATE_PAYMENT_NOT_RECONFIRMED, "GRP-1", "GRP-1");
        var manager = new CurrentAccountActor(22L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());

        for (var actor : List.of(staff(11L, ActorStatus.ACTIVE, 7L), manager)) {
            assertThatThrownBy(() -> authorizer.requireCounterEntry(actor, "REFUND-1"))
                    .isInstanceOf(WorkshopSettlementAuthorizationException.class).hasMessage("COUNTER_ENTRY_NOT_ALLOWED");
        }
        verifyNoInteractions(booking);
    }
}
