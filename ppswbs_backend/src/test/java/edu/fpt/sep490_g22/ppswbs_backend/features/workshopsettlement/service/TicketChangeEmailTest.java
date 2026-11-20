package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.3 FR-045b: "changed", "refused" and "expired" emails for a ticket change (owner decision 2026-11-18). */
class TicketChangeEmailTest {

    private final SettlementNotificationHandoffService handoffs = mock(SettlementNotificationHandoffService.class);
    private final WorkshopBookingSettlementAdapter bookingAdapter = mock(WorkshopBookingSettlementAdapter.class);
    private final SettlementCustomerNotifier notifier = new SettlementCustomerNotifier(handoffs, bookingAdapter);

    private final ArgumentCaptor<String> kind = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);

    private void created(int times) {
        verify(handoffs, times(times)).create(anyString(), kind.capture(), anyString(), to.capture(), body.capture(),
                subject.capture(), eq("/workshop-booking/lookup"), eq(null));
    }

    private void nothingCreated() {
        verify(handoffs, never()).create(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any());
    }

    // ---- SettlementCustomerNotifier -------------------------------------------------------------------------

    @Test
    void changedGoesToBuyerAndTicketRecipientButOnlyTheBuyerSeesThePriceRise() {
        when(bookingAdapter.notificationRecipient("T-1")).thenReturn("buyer@example.test");
        when(bookingAdapter.ticketRecipientEmail("T-1")).thenReturn("guest@example.test");
        when(handoffs.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("SETTLE-NOTIF-1");

        String reference = notifier.notifyTicketChanged("T-1", "TCHG-1", "01/12/2026 09:00–11:00",
                "02/12/2026 14:00–16:00", "Mland Quận 1", 150_000L, "CORR");

        assertThat(reference).isEqualTo("SETTLE-NOTIF-1");
        created(2);
        assertThat(kind.getAllValues()).containsExactly("TICKET_CHANGED", "TICKET_CHANGED_RECIPIENT");
        assertThat(to.getAllValues()).containsExactly("buyer@example.test", "guest@example.test");
        assertThat(subject.getValue()).isEqualTo("MLAND – Đã đổi lịch vé T-1");
        assertThat(body.getAllValues().get(0)).contains("01/12/2026 09:00–11:00").contains("02/12/2026 14:00–16:00")
                .contains("Mland Quận 1").contains("150.000").contains("QR");
        assertThat(body.getAllValues().get(1)).contains("02/12/2026 14:00–16:00").doesNotContain("150.000");
    }

    @Test
    void changedWithoutPriceRiseOrRecipientSendsOnlyTheBuyerAndNoPriceSentence() {
        when(bookingAdapter.notificationRecipient("T-1")).thenReturn("buyer@example.test");
        when(bookingAdapter.ticketRecipientEmail("T-1")).thenReturn(null);

        notifier.notifyTicketChanged("T-1", "TCHG-1", "A", "B", null, 0L, "CORR");

        created(1);
        assertThat(body.getValue()).doesNotContain("thanh toán thêm").contains("sang B.");
    }

    @Test
    void aRecipientWithTheBuyersAddressGetsNoSecondEmail() {
        when(bookingAdapter.notificationRecipient("T-1")).thenReturn("Buyer@Example.test");
        when(bookingAdapter.ticketRecipientEmail("T-1")).thenReturn("buyer@example.test");

        notifier.notifyTicketChanged("T-1", "TCHG-1", "A", "B", "X", 0L, "CORR");

        created(1);
    }

    @Test
    void anOrderWithoutEmailSkipsTheBuyerButStillTellsTheTicketRecipient() {
        when(bookingAdapter.notificationRecipient("T-1")).thenReturn(" ");
        when(bookingAdapter.ticketRecipientEmail("T-1")).thenReturn("guest@example.test");

        assertThat(notifier.notifyTicketChanged("T-1", "TCHG-1", "A", "B", "X", 0L, "CORR")).isNull();

        created(1);
        assertThat(to.getValue()).isEqualTo("guest@example.test");
    }

    @Test
    void noEmailAnywhereCreatesNothing() {
        when(bookingAdapter.notificationRecipient("T-1")).thenReturn(null);
        when(bookingAdapter.ticketRecipientEmail("T-1")).thenReturn("");

        assertThat(notifier.notifyTicketChanged("T-1", "TCHG-1", "A", "B", "X", 0L, "CORR")).isNull();
        assertThat(notifier.notifyTicketChangeRejected("T-1", "REQ-1", "A", "B", "X", "CORR")).isNull();
        assertThat(notifier.notifyTicketChangeExpired("T-1", "REQ-1", "A", "B", "X", "CORR")).isNull();
        nothingCreated();
    }

    @Test
    void failedLookupsNeverThrow() {
        when(bookingAdapter.notificationRecipient("T-1")).thenThrow(new IllegalStateException("boom"));
        when(bookingAdapter.ticketRecipientEmail("T-1")).thenThrow(new IllegalStateException("boom"));

        assertThat(notifier.notifyTicketChanged("T-1", "TCHG-1", "A", "B", "X", 0L, "CORR")).isNull();
        nothingCreated();
    }

    @Test
    void rejectedAndExpiredKeepTheOldScheduleAndTheQr() {
        when(bookingAdapter.notificationRecipient("T-1")).thenReturn("buyer@example.test");
        when(bookingAdapter.ticketRecipientEmail("T-1")).thenReturn("guest@example.test");

        notifier.notifyTicketChangeRejected("T-1", "REQ-1", "OLD", "WANTED", "Mland Quận 1", "CORR");
        notifier.notifyTicketChangeExpired("T-1", "REQ-2", "OLD", "WANTED", "Mland Quận 1", "CORR");

        created(4);
        assertThat(kind.getAllValues()).containsExactly("TICKET_CHANGE_REJECTED", "TICKET_CHANGE_REJECTED_RECIPIENT",
                "TICKET_CHANGE_EXPIRED", "TICKET_CHANGE_EXPIRED_RECIPIENT");
        assertThat(body.getAllValues().get(0)).contains("không được chấp nhận").contains("OLD").contains("WANTED")
                .contains("QR");
        assertThat(body.getAllValues().get(2)).contains("đã hết hạn xử lý").contains("OLD");
        assertThat(subject.getAllValues().get(0)).contains("T-1");
    }

    // ---- TicketChangeNotifier -------------------------------------------------------------------------------

    private final SettlementCustomerNotifier customer = mock(SettlementCustomerNotifier.class);
    private final WorkshopOperationsSettlementAdapter operations = mock(WorkshopOperationsSettlementAdapter.class);
    private final WorkshopBookingFacade booking = mock(WorkshopBookingFacade.class);
    private final TicketChangeNotifier facts = new TicketChangeNotifier(customer, operations, booking);

    private final UUID oldSession = UUID.randomUUID();
    private final UUID newSession = UUID.randomUUID();

    private SaleEligibilitySnapshot snapshot(Instant start) {
        return new SaleEligibilitySnapshot(true, "OPEN", 7L, start, start.plus(2, ChronoUnit.HOURS), 5);
    }

    private TicketFolioContext ctx() {
        return new TicketFolioContext("T-1", "G-1", "LINE-1", "10", 7L, List.of(oldSession.toString()), 100L);
    }

    private void projection() {
        DeliverySafeTicket ticket = new DeliverySafeTicket(UUID.randomUUID(), "QR", "T-1",
                List.of(new SessionEntitlementProjection(newSession, 7L, Instant.now(), Instant.now(), "Mland Quận 1")));
        when(operations.ticketProjection("LINE-1")).thenReturn(new DeliverySafeTicketSet(List.of(ticket)));
    }

    @Test
    void appliedDescribesOldAndWholeNewScheduleWithTheBranchName() {
        projection();
        when(operations.saleEligibility(oldSession)).thenReturn(snapshot(Instant.parse("2026-12-01T02:00:00Z")));
        when(operations.saleEligibility(newSession)).thenReturn(snapshot(Instant.parse("2026-12-02T07:00:00Z")));

        facts.applied("T-1", ctx(), List.of(newSession), 50_000L, "TCHG-1", "CORR");

        verify(customer).notifyTicketChanged("T-1", "TCHG-1", "01/12/2026 09:00–11:00", "02/12/2026 14:00–16:00",
                "Mland Quận 1", 50_000L, "CORR");
    }

    @Test
    void appliedStillSendsWhenTheProjectionIsUnavailable() {
        when(operations.ticketProjection("LINE-1")).thenThrow(new IllegalStateException("down"));
        when(operations.saleEligibility(oldSession)).thenReturn(snapshot(Instant.parse("2026-12-01T02:00:00Z")));
        when(operations.saleEligibility(newSession)).thenReturn(snapshot(Instant.parse("2026-12-02T07:00:00Z")));

        facts.applied("T-1", ctx(), List.of(newSession), 0L, "TCHG-1", "CORR");

        verify(customer).notifyTicketChanged(eq("T-1"), eq("TCHG-1"), anyString(), eq("02/12/2026 14:00–16:00"),
                eq(null), anyLong(), eq("CORR"));
    }

    @Test
    void aFailingLookupNeverEscapesTheChange() {
        when(operations.ticketProjection("LINE-1")).thenThrow(new IllegalStateException("down"));
        when(operations.saleEligibility(any())).thenThrow(new IllegalStateException("down"));
        when(booking.getTicketFolioContext("T-1")).thenThrow(new IllegalStateException("down"));

        facts.applied("T-1", ctx(), List.of(newSession), 0L, "TCHG-1", "CORR");
        facts.rejected(request("PENDING", Instant.now()), "CORR");
        facts.expired(request("PENDING", Instant.now()), "CORR");

        verify(customer, never()).notifyTicketChanged(any(), any(), any(), any(), any(), anyLong(), any());
        verify(customer, never()).notifyTicketChangeRejected(any(), any(), any(), any(), any(), any());
        verify(customer, never()).notifyTicketChangeExpired(any(), any(), any(), any(), any(), any());
    }

    private SettlementTicketChangeRequest request(String status, Instant expiresAt) {
        return SettlementTicketChangeRequest.builder().requestReference("REQ-1").idempotencyKey("k")
                .ticketCode("T-1").groupCode("G-1").locationReference(7L).changeType("ADVANCE")
                .targetSessionReferences(newSession.toString()).fromSessionReferences(oldSession.toString())
                .blockedRules("CHANGE_ALLOWANCE_EXHAUSTED").status(status).requesterRole("MEMBER").channel("ONLINE")
                .expiresAt(expiresAt).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void rejectedAndExpiredNameTheCurrentAndTheRequestedSchedule() {
        when(booking.getTicketFolioContext("T-1")).thenReturn(Optional.of(ctx()));
        when(operations.saleEligibility(oldSession)).thenReturn(snapshot(Instant.parse("2026-12-01T02:00:00Z")));
        when(operations.saleEligibility(newSession)).thenReturn(snapshot(Instant.parse("2026-12-02T07:00:00Z")));
        SettlementTicketChangeRequest request = request("PENDING", Instant.now());

        facts.rejected(request, "CORR");
        facts.expired(request, "CORR");

        verify(customer).notifyTicketChangeRejected("T-1", "REQ-1", "01/12/2026 09:00–11:00",
                "02/12/2026 14:00–16:00", null, "CORR");
        verify(customer).notifyTicketChangeExpired("T-1", "REQ-1", "01/12/2026 09:00–11:00",
                "02/12/2026 14:00–16:00", null, "CORR");
    }

    // ---- expiry on time ---------------------------------------------------------------------------------------

    private final SettlementTicketChangeRequestRepository requests = mock(SettlementTicketChangeRequestRepository.class);
    private final TicketChangeNotifier changeNotifier = mock(TicketChangeNotifier.class);
    private final TicketChangeService changes = new TicketChangeService(booking, operations, null, null, null,
            mock(SettlementBusinessAuditRepository.class), null, null, requests, changeNotifier);

    @Test
    void expireIfDueExpiresOnceAndEmailsOnce() {
        Instant now = Instant.now();
        SettlementTicketChangeRequest due = request("PENDING", now.minusSeconds(5));
        when(requests.findByRequestReferenceForUpdate("REQ-1")).thenReturn(Optional.of(due));

        assertThat(changes.expireIfDue("REQ-1", now)).isTrue();

        assertThat(due.getStatus()).isEqualTo("EXPIRED");
        verify(changeNotifier).expired(eq(due), anyString());
    }

    @Test
    void expireIfDueIgnoresAlreadyDecidedOrNotYetDueRequests() {
        Instant now = Instant.now();
        when(requests.findByRequestReferenceForUpdate("DONE")).thenReturn(Optional.of(request("APPROVED", now.minusSeconds(5))));
        when(requests.findByRequestReferenceForUpdate("LATER")).thenReturn(Optional.of(request("PENDING", now.plusSeconds(60))));
        when(requests.findByRequestReferenceForUpdate("GONE")).thenReturn(Optional.empty());

        assertThat(changes.expireIfDue("DONE", now)).isFalse();
        assertThat(changes.expireIfDue("LATER", now)).isFalse();
        assertThat(changes.expireIfDue("GONE", now)).isFalse();
        verify(changeNotifier, never()).expired(any(), any());
    }

    @Test
    void theSchedulerExpiresEachDueRequestAndOneFailureDoesNotStopTheRest() {
        Instant now = Instant.now();
        SettlementTicketChangeRequest first = request("PENDING", now.minusSeconds(5));
        SettlementTicketChangeRequest second = SettlementTicketChangeRequest.builder().requestReference("REQ-2")
                .status("PENDING").expiresAt(now.minusSeconds(5)).build();
        when(requests.findByStatusAndExpiresAtLessThanEqual("PENDING", now)).thenReturn(List.of(first, second));
        TicketChangeService service = mock(TicketChangeService.class);
        when(service.expireIfDue(eq("REQ-1"), any())).thenThrow(new IllegalStateException("boom"));
        when(service.expireIfDue(eq("REQ-2"), any())).thenReturn(true);

        assertThat(new TicketChangeRequestExpiryScheduler(requests, service).sweep(now)).isEqualTo(1);
        verify(service).expireIfDue(eq("REQ-2"), any());
    }
}
