package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 003.3 T162 (SC-017, CHK038, FR-040) with a controllable clock: a {@code PENDING_ACCOUNT} request without a reply
 * gets exactly one reminder on day 3 and one on day 7 and none once the account is entered; CLOSED_UNREACHABLE is
 * refused before day 30 and allowed from day 30 for a Manager with a reason; reopening returns PENDING_ACCOUNT with
 * a fresh link and keeps the entitlement.
 */
class RefundReminderAndClosureClockTest {

    private static final Instant DAY0 = Instant.parse("2026-03-01T08:00:00Z");
    private static final String TICKET = "GRP-CLOCK-L0-T1";
    private static final Long MANAGER = 22L;

    private final List<SettlementRefundRequest> requestRows = new ArrayList<>();
    private final List<SettlementRefundAccountLink> linkRows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private final SettlementCustomerNotifier notifier = mock(SettlementCustomerNotifier.class);
    private SettlementRefundRequestRepository requests;
    private BankRefundService service;
    private RefundAccountReminderScheduler scheduler;
    private Instant now = DAY0;

    @BeforeAll
    static void key() {
        RefundAccountCipher.useKeyForTests("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeEach
    void setUp() {
        requests = mock(SettlementRefundRequestRepository.class);
        var links = mock(SettlementRefundAccountLinkRepository.class);
        var collections = mock(SettlementFolioCollectionRepository.class);
        var bills = mock(SettlementFinalBillRepository.class);
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            SettlementRefundRequest row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ids.getAndIncrement());
                row.setCreatedAt(now);
                requestRows.add(row);
            }
            return row;
        });
        when(requests.findByReasonAndSourceReference(any(), anyString())).thenReturn(java.util.Optional.empty());
        when(requests.findForUpdate(anyString())).thenAnswer(invocation -> requestRows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getRequestReference())).findFirst());
        when(requests.findUnansweredSince(any())).thenAnswer(invocation -> requestRows.stream()
                .filter(row -> row.getStatus() == RefundRequestStatus.PENDING_ACCOUNT)
                .filter(row -> !row.getCreatedAt().isAfter(invocation.getArgument(0))).toList());
        when(links.saveAndFlush(any())).thenAnswer(invocation -> {
            SettlementRefundAccountLink row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ids.getAndIncrement());
                linkRows.add(row);
            }
            return row;
        });
        when(links.findByRequestIdAndLifecycleStatus(any(), any())).thenAnswer(invocation -> linkRows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getRequestId())
                        && row.getLifecycleStatus() == invocation.getArgument(1)).toList());
        when(links.findByTokenHash(anyString())).thenAnswer(invocation -> linkRows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getTokenHash())).findFirst());
        when(requests.findById(any())).thenAnswer(invocation -> requestRows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getId())).findFirst());
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET)).thenReturn(List.of(
                SettlementFolioCollection.builder().billReference(TICKET).bookingReference(TICKET)
                        .method(FolioCollectionMethod.VNPAY).amountVnd(500_000)
                        .status(FolioCollectionStatus.CONFIRMED).build()));
        var submissions = mock(SettlementRefundLinkSubmissionRepository.class);

        service = new BankRefundService(requests, links, collections, bills, mock(SettlementEvidenceConclusionRepository.class),
                mock(SettlementBusinessAuditRepository.class), mock(org.springframework.context.ApplicationEventPublisher.class),
                notifier, submissions);
        scheduler = new RefundAccountReminderScheduler(requests, service);
        setNow(DAY0);
    }

    private void setNow(Instant instant) {
        now = instant;
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        service.useClockForTests(clock);
        scheduler.useClockForTests(clock);
    }

    private void advanceTo(Duration sinceOpening) {
        setNow(DAY0.plus(sinceOpening));
    }

    private SettlementRefundRequest open() {
        return service.open(TICKET, "GRP-CLOCK", RefundReason.MLAND_CANCELLATION, "case-clock", 200_000, "corr").request();
    }

    @Test
    void exactlyOneReminderOnDay3AndOneOnDay7ThenNone() {
        var request = open();

        advanceTo(Duration.ofDays(2).plusHours(23));
        scheduler.remindPayersWithoutAnAccount();
        verify(notifier, never()).notifyRefundAccountRequest(any(), anyString(), any(), eq(true), anyString());

        advanceTo(Duration.ofDays(3));
        scheduler.remindPayersWithoutAnAccount();
        scheduler.remindPayersWithoutAnAccount(); // a second sweep the same day sends nothing
        verify(notifier, times(1)).notifyRefundAccountRequest(eq(request), anyString(), any(), eq(true), anyString());
        assertThat(request.getReminderDay3At()).isEqualTo(DAY0.plus(Duration.ofDays(3)));

        advanceTo(Duration.ofDays(6));
        scheduler.remindPayersWithoutAnAccount();
        verify(notifier, times(1)).notifyRefundAccountRequest(eq(request), anyString(), any(), eq(true), anyString());

        advanceTo(Duration.ofDays(7));
        scheduler.remindPayersWithoutAnAccount();
        scheduler.remindPayersWithoutAnAccount();
        verify(notifier, times(2)).notifyRefundAccountRequest(eq(request), anyString(), any(), eq(true), anyString());
        assertThat(request.getReminderDay7At()).isEqualTo(DAY0.plus(Duration.ofDays(7)));

        advanceTo(Duration.ofDays(20));
        scheduler.remindPayersWithoutAnAccount();
        verify(notifier, times(2)).notifyRefundAccountRequest(eq(request), anyString(), any(), eq(true), anyString());
    }

    @Test
    void noReminderOnceTheAccountIsEntered() {
        var request = open();
        advanceTo(Duration.ofDays(2));
        service.submitAccountAtCounter(request.getRequestReference(), "BIDV", "12345678901", "A", true, 11L, "corr");

        advanceTo(Duration.ofDays(3));
        scheduler.remindPayersWithoutAnAccount();
        advanceTo(Duration.ofDays(8));
        scheduler.remindPayersWithoutAnAccount();

        verify(notifier, never()).notifyRefundAccountRequest(any(), anyString(), any(), eq(true), anyString());
        assertThat(request.getReminderDay3At()).isNull();
    }

    @Test
    void closingAsUnreachableIsRefusedBeforeDay30AndAllowedFromDay30WithAReason() {
        var request = open();
        String reference = request.getRequestReference();

        advanceTo(Duration.ofDays(29).plusHours(23));
        assertThatThrownBy(() -> service.closeUnreachable(reference, MANAGER, "Khong lien lac duoc", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("REFUND_NOT_UNREACHABLE_YET");

        advanceTo(Duration.ofDays(30));
        assertThatThrownBy(() -> service.closeUnreachable(reference, MANAGER, " ", "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        var closed = service.closeUnreachable(reference, MANAGER, "Khong lien lac duoc", "corr");

        assertThat(closed.getStatus()).isEqualTo(RefundRequestStatus.CLOSED_UNREACHABLE);
        assertThat(closed.getClosedAt()).isEqualTo(DAY0.plus(Duration.ofDays(30)));
        assertThat(closed.getClosureReason()).isEqualTo("Khong lien lac duoc");
        assertThat(closed.getClosedByAccountId()).isEqualTo(MANAGER);
    }

    @Test
    void reopeningReturnsToPendingAccountWithAFreshLinkKeepsTheEntitlementAndRestartsTheReminders() {
        var request = open();
        String reference = request.getRequestReference();
        advanceTo(Duration.ofDays(31));
        service.closeUnreachable(reference, MANAGER, "Khong lien lac duoc", "corr");
        long amount = request.getAmountVnd();
        int linksBefore = linkRows.size();

        advanceTo(Duration.ofDays(40));
        var reopened = service.reopen(reference, MANAGER, "corr");

        assertThat(reopened.request().getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
        assertThat(reopened.request().getAmountVnd()).isEqualTo(amount);
        assertThat(reopened.request().getReopenedAt()).isEqualTo(DAY0.plus(Duration.ofDays(40)));
        assertThat(reopened.rawAccountLinkToken()).isNotBlank();
        assertThat(linkRows).hasSize(linksBefore + 1);
        assertThat(linkRows.get(linkRows.size() - 1).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.ACTIVE);
        assertThat(linkRows.get(linkRows.size() - 1).getExpiresAt()).isEqualTo(DAY0.plus(Duration.ofDays(47)));

        // The reminders count from the reopening, not from the original request.
        advanceTo(Duration.ofDays(42));
        scheduler.remindPayersWithoutAnAccount();
        verify(notifier, never()).notifyRefundAccountRequest(any(), anyString(), any(), eq(true), anyString());
        advanceTo(Duration.ofDays(43));
        scheduler.remindPayersWithoutAnAccount();
        verify(notifier, times(1)).notifyRefundAccountRequest(any(), anyString(), any(), eq(true), anyString());
    }
}
