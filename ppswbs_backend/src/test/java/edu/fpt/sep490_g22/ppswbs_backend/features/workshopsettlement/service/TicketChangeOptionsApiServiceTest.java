package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketChangeOptions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeOptionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeOptionsDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GuestChangeOptionLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.3 FR-044a / T177: who may read the change options, and what 003.3 adds to 003.2's list. */
class TicketChangeOptionsApiServiceTest {

    private static final String TICKET = "GRP-01-L1-T1";
    private static final String GROUP = "GRP-01";
    private static final Long BUYER = 100L;

    private final WorkshopBookingFacade booking = mock(WorkshopBookingFacade.class);
    private final WorkshopBookingSettlementAdapter access = mock(WorkshopBookingSettlementAdapter.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final SettlementFinalBillRepository finalBills = mock(SettlementFinalBillRepository.class);
    private final SettlementTicketChangeRepository changeRepository = mock(SettlementTicketChangeRepository.class);
    private final LoyaltyFacade loyalty = mock(LoyaltyFacade.class);
    private final SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
    private TicketChangeOptionsApiService service;

    private final UUID opsTicket = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID optionA = UUID.randomUUID();
    private final UUID optionB = UUID.randomUUID();
    private Instant firstStart;

    @BeforeEach
    void setUp() {
        TicketChangeService changes = new TicketChangeService(booking, operations, finalBills, changeRepository,
                mock(FolioBalanceService.class), audits, loyalty,
                new WorkshopSettlementAuthorizationService(mock(AccountFacade.class), audits, operations),
                mock(SettlementTicketChangeRequestRepository.class));
        service = new TicketChangeOptionsApiService(booking, access, operations, finalBills, changeRepository, changes,
                new WorkshopSettlementAuthorizationService(mock(AccountFacade.class), audits, operations));
        firstStart = Instant.now().plus(72, ChronoUnit.HOURS);
        when(booking.getTicketFolioContext(TICKET)).thenReturn(Optional.of(context(0L)));
        when(finalBills.findByBookingReference(TICKET)).thenReturn(Optional.of(folio(SettlementFinalBillLifecycleStatus.OPEN)));
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET)).thenReturn(0L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET, "MANAGER_GRANTED")).thenReturn(0L);
        when(operations.ticketChangeOptions(eq(opsTicket), any(), any())).thenReturn(found(1, true));
    }

    private TicketFolioContext context(long voucher) {
        return new TicketFolioContext(TICKET, GROUP, "L1", "7", 10L, List.of(first.toString(), second.toString()),
                BUYER, opsTicket.toString(), firstStart, 0L, "L1-T1", voucher);
    }

    private SettlementFinalBill folio(SettlementFinalBillLifecycleStatus status) {
        return SettlementFinalBill.builder().billReference("BILL-" + TICKET).bookingReference(TICKET)
                .groupReference(GROUP).postPromotionAmountVnd(200_000L).acceptedDepositAmountVnd(100_000L)
                .remainingAmountVnd(100_000L).currencyCode("VND").lifecycleStatus(status.name()).build();
    }

    /** First session changeable with two candidates: A fine, B full. */
    private TicketChangeOptions found(int quotaA, boolean changeable) {
        Instant start = firstStart;
        TicketChangeOptions.FromSession entry = new TicketChangeOptions.FromSession(first, start,
                start.plus(2, ChronoUnit.HOURS), changeable, changeable ? null : "INVALID_REPLACEMENT",
                changeable ? List.of(
                        new TicketChangeOptions.Option(optionA, start.plus(24, ChronoUnit.HOURS),
                                start.plus(26, ChronoUnit.HOURS), 10L, 4, quotaA, null),
                        new TicketChangeOptions.Option(optionB, start.plus(48, ChronoUnit.HOURS),
                                start.plus(50, ChronoUnit.HOURS), 10L, 0, 1, "INSUFFICIENT_CAPACITY"))
                        : List.of());
        return new TicketChangeOptions(TicketChangeOptions.Status.OK, List.of(entry), List.of());
    }

    private static CurrentAccountActor actor(Long id, ActorRole role, Long... locations) {
        return new CurrentAccountActor(id, role, ActorStatus.ACTIVE, false, List.of(locations));
    }

    @Test
    @DisplayName("the owner Member sees exact seats and a self-serve option with no Manager rule")
    void memberOwnerSelfServe() {
        ChangeOptionsDto result = service.forActor(TICKET, TicketChangeType.ADVANCE, List.of(first),
                actor(BUYER, ActorRole.MEMBER, 10L));

        assertThat(result.canSelfServe()).isTrue();
        assertThat(result.windowOpen()).isTrue();
        assertThat(result.advanceDeadline()).isEqualTo(firstStart.minus(24, ChronoUnit.HOURS));
        assertThat(result.allowance().left()).isEqualTo(1);
        List<ChangeOptionDto> options = result.perFromSession().get(0).options();
        assertThat(options.get(0).selectable()).isTrue();
        assertThat(options.get(0).requiresManager()).isFalse();
        assertThat(options.get(0).remainingCapacity()).isEqualTo(4);
        assertThat(options.get(0).priceIncreaseAmountVnd()).isEqualTo("0");
        assertThat(options.get(1).selectable()).isFalse();
        assertThat(options.get(1).reasonCode()).isEqualTo("INSUFFICIENT_CAPACITY");
        assertThat(options.get(1).requiresManager()).isFalse();
        verify(operations).ticketChangeOptions(opsTicket, TicketReplacementType.CUSTOMER_ADVANCE, List.of(first));
    }

    @Test
    @DisplayName("the reschedule quota left of a session is passed on and needs no Manager")
    void quotaLeftIsReported() {
        when(operations.ticketChangeOptions(eq(opsTicket), any(), any())).thenReturn(found(2, true));

        ChangeOptionDto option = service.forActor(TICKET, TicketChangeType.ADVANCE, List.of(first),
                actor(BUYER, ActorRole.MEMBER, 10L)).perFromSession().get(0).options().get(0);

        assertThat(option.selectable()).isTrue();
        assertThat(option.remainingRescheduleQuota()).isEqualTo(2);
        assertThat(option.requiresManager()).isFalse();
    }

    @Test
    @DisplayName("the used allowance and a closed 24 h window put their rule names on every selectable option")
    void allowanceAndWindowRules() {
        firstStart = Instant.now().plus(10, ChronoUnit.HOURS);
        when(operations.ticketChangeOptions(eq(opsTicket), any(), any())).thenReturn(found(1, true));
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET)).thenReturn(1L);

        ChangeOptionsDto result = service.forActor(TICKET, TicketChangeType.ADVANCE, List.of(first),
                actor(BUYER, ActorRole.MEMBER, 10L));

        assertThat(result.allowance().exhausted()).isTrue();
        assertThat(result.windowOpen()).isFalse();
        assertThat(result.canSelfServe()).isFalse();
        assertThat(result.perFromSession().get(0).options().get(0).managerRules())
                .containsExactly("CHANGE_ALLOWANCE_EXHAUSTED", "ADVANCE_CHANGE_WINDOW_EXPIRED");
        assertThat(result.perFromSession().get(0).options().get(1).managerRules()).isEmpty();
    }

    @Test
    @DisplayName("a Manager-granted change raises the allowance total")
    void managerGrantRaisesAllowance() {
        when(changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(TICKET)).thenReturn(1L);
        when(changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(TICKET, "MANAGER_GRANTED")).thenReturn(1L);

        ChangeOptionsDto result = service.forActor(TICKET, TicketChangeType.ADVANCE, List.of(first),
                actor(BUYER, ActorRole.MEMBER, 10L));

        assertThat(result.allowance().total()).isEqualTo(2);
        assertThat(result.allowance().left()).isEqualTo(1);
        assertThat(result.canSelfServe()).isTrue();
    }

    @Test
    @DisplayName("each option shows the voucher discount the ticket would lose, asked for the ticket's whole new set")
    void priceIncreasePerOption() {
        when(booking.getTicketFolioContext(TICKET)).thenReturn(Optional.of(context(50_000L)));
        when(operations.saleEligibility(any())).thenAnswer(invocation -> new SaleEligibilitySnapshot(
                invocation.getArgument(0), true, "PUBLISHED", 20L, firstStart, firstStart.plus(2, ChronoUnit.HOURS), 3,
                LocalDate.now().plusDays(3)));
        when(loyalty.checkTicketVoucherScope(any()))
                .thenReturn(new TicketVoucherScope(true, false, BigInteger.valueOf(50_000L)));

        ChangeOptionDto option = service.forActor(TICKET, TicketChangeType.ADVANCE, List.of(first),
                actor(BUYER, ActorRole.MEMBER, 10L)).perFromSession().get(0).options().get(0);

        assertThat(option.priceIncreaseAmountVnd()).isEqualTo("50000");
        ArgumentCaptor<edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScopeQuery> query =
                ArgumentCaptor.forClass(edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScopeQuery.class);
        verify(loyalty, org.mockito.Mockito.atLeastOnce()).checkTicketVoucherScope(query.capture());
        assertThat(query.getAllValues().get(0).proposedSessions()).extracting(session -> session.sessionRef())
                .containsExactlyInAnyOrder(optionA.toString(), second.toString());
    }

    @Test
    @DisplayName("a Member who is not the buyer, an unknown ticket and an out-of-scope Staff are refused")
    void authorizationRefusals() {
        assertThatThrownBy(() -> service.forActor(TICKET, TicketChangeType.ADVANCE, null,
                actor(999L, ActorRole.MEMBER, 10L))).isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> service.forActor(TICKET, TicketChangeType.ADVANCE, null,
                actor(200L, ActorRole.STAFF, 99L))).isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> service.forActor(TICKET, TicketChangeType.ADVANCE, null, null))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> service.forActor("NOPE", TicketChangeType.ADVANCE, null,
                actor(BUYER, ActorRole.MEMBER, 10L))).isInstanceOf(WorkshopSettlementNotFoundException.class);
        verify(operations, never()).ticketChangeOptions(any(), any(), any());
    }

    @Test
    @DisplayName("Staff in the ticket's location but not assigned to a session of the ticket is refused")
    void unassignedStaffIsRefused() {
        when(operations.staffAssignedToBooking(200L, "L1")).thenReturn(false);

        assertThatThrownBy(() -> service.forActor(TICKET, TicketChangeType.ADVANCE, null,
                actor(200L, ActorRole.STAFF, 10L)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessageContaining("SESSION_ASSIGNMENT_REQUIRED");
        verify(operations, never()).ticketChangeOptions(any(), any(), any());
    }

    @Test
    @DisplayName("Staff in the ticket's location and assigned to a session of the ticket, and a Manager, may read")
    void staffAndManagerMayRead() {
        when(operations.staffAssignedToBooking(200L, "L1")).thenReturn(true);
        assertThat(service.forActor(TICKET, TicketChangeType.ADVANCE, null, actor(200L, ActorRole.STAFF, 10L)))
                .isNotNull();
        assertThat(service.forActor(TICKET, TicketChangeType.ADVANCE, null, actor(300L, ActorRole.MANAGER)))
                .isNotNull();
    }

    @Test
    @DisplayName("a folio that is not OPEN is 409, an OTA ticket is 409, a from session of another ticket is 422")
    void conflictsAndValidation() {
        when(finalBills.findByBookingReference(TICKET))
                .thenReturn(Optional.of(folio(SettlementFinalBillLifecycleStatus.FINALIZED)));
        assertThatThrownBy(() -> service.forActor(TICKET, TicketChangeType.ADVANCE, null,
                actor(BUYER, ActorRole.MEMBER, 10L)))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("TICKET_FOLIO_NOT_OPEN");

        when(finalBills.findByBookingReference(TICKET)).thenReturn(Optional.of(folio(SettlementFinalBillLifecycleStatus.OPEN)));
        when(operations.ticketChangeOptions(eq(opsTicket), any(), any()))
                .thenReturn(TicketChangeOptions.refused(TicketChangeOptions.Status.OTA_TICKET_NOT_SUPPORTED));
        assertThatThrownBy(() -> service.forActor(TICKET, TicketChangeType.ADVANCE, null,
                actor(BUYER, ActorRole.MEMBER, 10L)))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessageContaining("OTA_TICKET_NOT_SUPPORTED");

        assertThatThrownBy(() -> service.forActor(TICKET, TicketChangeType.ADVANCE, List.of(UUID.randomUUID()),
                actor(BUYER, ActorRole.MEMBER, 10L)))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("FROM_SESSION_NOT_OF_TICKET");
    }

    @Test
    @DisplayName("Guest: one right contact reads; a wrong proof is the same 404; two contacts is 422-class validation")
    void guestProof() {
        when(access.verifyCustomerAccess(TICKET, null, "buyer@example.com", null))
                .thenReturn(new CustomerBookingAccessDecision(true));
        when(access.verifyCustomerAccess(TICKET, null, "other@example.com", null))
                .thenReturn(new CustomerBookingAccessDecision(false));

        ChangeOptionsDto result = service.forGuest(TICKET, new GuestChangeOptionLookupRequest(
                "buyer@example.com", null, TicketChangeType.ADVANCE, List.of(first)));
        assertThat(result.perFromSession()).hasSize(1);

        assertThatThrownBy(() -> service.forGuest(TICKET, new GuestChangeOptionLookupRequest(
                "other@example.com", null, TicketChangeType.ADVANCE, null)))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> service.forGuest("NOPE", new GuestChangeOptionLookupRequest(
                "buyer@example.com", null, TicketChangeType.ADVANCE, null)))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> service.forGuest(TICKET, new GuestChangeOptionLookupRequest(
                "buyer@example.com", "0900000000", TicketChangeType.ADVANCE, null)))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }
}
