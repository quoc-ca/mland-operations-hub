package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationReady;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingRecoveryTransactionServiceTest {

    @Mock private WorkshopBookingRepository bookings;
    @Mock private BookingSessionCommitmentRepository commitments;
    @Mock private BookingTicketRepository tickets;
    @Mock private MagicLinkAccessRepository magicLinks;
    @Mock private BookingNotificationIntentRepository notifications;
    @Mock private BookingAuditEvidenceRepository auditEvidenceRepository;
    @Mock private BookingLatePaymentEvidenceRepository latePaymentEvidenceRepository;
    @Mock private WorkshopBookingAuthorizationService authorization;
    @Mock private WorkshopBookingFacade bookingFacade;
    @Mock private BookingIdempotencyService idempotency;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy private ObjectMapper mapper = new ObjectMapper();

    @InjectMocks private BookingRecoveryTransactionService service;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void revokeMakesAllUsableLinksInactiveAndAuditsOnlyReferences() {
        WorkshopBooking booking = booking();
        MagicLinkAccess link = activeLink(booking, "old-hash");
        AccountSummary manager = manager();
        stubBase(booking, manager, List.of(link));

        BookingRecoveryCommandResult result = service.applyMagicLinkIncident("Bearer manager", booking.getBookingCode(),
                "REVOKE", "suspected exposure", "corr-1", claim());

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(link.getLifecycleStatus()).isEqualTo(MagicLinkLifecycleStatus.REVOKED);
        assertThat(link.getRevocationReason()).isEqualTo("suspected exposure");
        assertThat(link.getRevokedByAccountId()).isEqualTo(9L);
        verify(authorization).recordAudit(booking.getBookingCode(), 3L, "MANAGER", 9L,
                "MAGIC_LINK_REVOKE", "REVOKED", "corr-1", "suspected exposure");
        verifyNoInteractions(eventPublisher, notifications);
        verify(idempotency).completeClaimWithinTransaction(5L, 2L, 200, result.responsePayload());
    }

    @Test
    void rotateStoresReplacementBearerOnlyInProtectedSnapshotAndPublishesReferenceAfterCommit() throws Exception {
        WorkshopBooking booking = booking();
        MagicLinkAccess oldLink = activeLink(booking, "old-hash");
        AccountSummary manager = manager();
        stubBase(booking, manager, List.of(oldLink));
        BookingTicket ticket = BookingTicket.builder().ticketCode("TICKET-1").build();
        when(tickets.findByBookingId(booking.getId())).thenReturn(List.of(ticket));
        TransactionSynchronizationManager.initSynchronization();

        BookingRecoveryCommandResult result = service.applyMagicLinkIncident("Bearer manager", booking.getBookingCode(),
                "ROTATE", "link was forwarded", "corr-2", claim());

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(oldLink.getLifecycleStatus()).isEqualTo(MagicLinkLifecycleStatus.REVOKED);
        var linksCaptor = org.mockito.ArgumentCaptor.forClass(MagicLinkAccess.class);
        verify(magicLinks, times(2)).save(linksCaptor.capture());
        MagicLinkAccess replacement = linksCaptor.getAllValues().get(1);
        assertThat(replacement.getLifecycleStatus()).isEqualTo(MagicLinkLifecycleStatus.ACTIVE);
        assertThat(replacement.getExpiresAt()).isEqualTo(oldLink.getExpiresAt());

        var notificationCaptor = org.mockito.ArgumentCaptor.forClass(BookingNotificationIntent.class);
        verify(notifications).save(notificationCaptor.capture());
        BookingNotificationIntent intent = notificationCaptor.getValue();
        JsonNode snapshot = new ObjectMapper().readTree(intent.getDeliverySnapshot());
        String accessLink = snapshot.path("bookingAccessLink").asText();
        assertThat(accessLink).startsWith("/workshop-booking/access#");
        String rawToken = accessLink.substring(accessLink.indexOf('#') + 1);
        assertThat(replacement.getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(snapshot.path("storedRecipient").asText()).isEqualTo(booking.getContactEmail());
        assertThat(snapshot.path("renderedTicketArtifacts").get(0).asText()).isEqualTo("TICKET-1");
        assertThat(intent.getNotificationKind()).isEqualTo("MAGIC_LINK_ROTATION");

        verify(eventPublisher, never()).publishEvent(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof BookingNotificationReady ready
                && ready.notificationReference().equals(intent.getNotificationReference())
                && ready.notificationKind().equals("MAGIC_LINK_ROTATION")));
        verify(authorization).recordAudit(booking.getBookingCode(), 3L, "MANAGER", 9L,
                "MAGIC_LINK_ROTATE", "ROTATED", "corr-2", "link was forwarded");
    }

    @Test
    void internalResendIsRateLimitedAfterFiveAcceptedRequestsInFifteenMinutes() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "internalResendLimit", 5);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "internalResendWindowSeconds", 900L);
        WorkshopBooking booking = confirmedBooking();
        when(bookings.findLockedByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(
                BookingSessionCommitment.builder().locationId(3L).ordinal(1).build()));
        when(authorization.authenticate("Bearer manager")).thenReturn(manager());
        when(authorization.requireConfirmationResendAuthority(anyString(), eq(3L), eq(booking.getBookingCode()),
                anyString())).thenReturn(manager());
        when(auditEvidenceRepository.countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                eq(booking.getBookingCode()), eq("CONFIRMATION_RESEND"), eq("ACCEPTED"), anyCollection(), any()))
                .thenReturn(5L);

        BookingRecoveryCommandResult result = service.requestConfirmationDelivery("Bearer manager", booking.getBookingCode(),
                null, null, "MANAGER", 9L, "corr-limit", claim());

        assertThat(result.statusCode()).isEqualTo(429);
        var actors = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(auditEvidenceRepository).countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                eq(booking.getBookingCode()), eq("CONFIRMATION_RESEND"), eq("ACCEPTED"), actors.capture(), any());
        assertThat(actors.getValue()).containsExactlyInAnyOrder("STAFF", "MANAGER");
        verifyNoInteractions(tickets, notifications, eventPublisher);
        verify(authorization, never()).recordAudit(anyString(), any(), anyString(), any(), anyString(), anyString(), anyString(), any());
        verify(idempotency).completeClaimWithinTransaction(5L, 2L, 429, result.responsePayload());
    }

    @Test
    void internalResendBelowLimitQueuesExistingTicketSetAndAuditsAcceptedAction() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "internalResendLimit", 5);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "internalResendWindowSeconds", 900L);
        WorkshopBooking booking = confirmedBooking();
        when(bookings.findLockedByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(
                BookingSessionCommitment.builder().locationId(3L).ordinal(1).build()));
        when(authorization.authenticate("Bearer manager")).thenReturn(manager());
        when(authorization.requireConfirmationResendAuthority(anyString(), eq(3L), eq(booking.getBookingCode()),
                anyString())).thenReturn(manager());
        when(auditEvidenceRepository.countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                eq(booking.getBookingCode()), eq("CONFIRMATION_RESEND"), eq("ACCEPTED"), anyCollection(), any()))
                .thenReturn(4L);
        when(tickets.findByBookingId(booking.getId())).thenReturn(List.of(
                BookingTicket.builder().ticketCode("TICKET-REC-1").build()));
        String token = "access-token-for-test";
        MagicLinkAccess link = activeLink(booking, sha256(token));
        when(magicLinks.findByBookingIdAndLifecycleStatusOrderByIdAsc(booking.getId(), MagicLinkLifecycleStatus.ACTIVE))
                .thenReturn(List.of(link));
        when(notifications.findByBookingId(booking.getId())).thenReturn(List.of(BookingNotificationIntent.builder()
                .deliverySnapshot("{\"bookingAccessLink\":\"/workshop-booking/access#" + token
                        + "\",\"storedRecipient\":\"stored@example.test\"}").build()));
        TransactionSynchronizationManager.initSynchronization();

        BookingRecoveryCommandResult result = service.requestConfirmationDelivery("Bearer manager", booking.getBookingCode(),
                null, null, "MANAGER", 9L, "corr-resend", claim());

        assertThat(result.statusCode()).isEqualTo(202);
        var notification = org.mockito.ArgumentCaptor.forClass(BookingNotificationIntent.class);
        verify(notifications).save(notification.capture());
        assertThat(notification.getValue().getNotificationKind()).isEqualTo("CONFIRMATION_RESEND");
        JsonNode snapshot = mapper.readTree(notification.getValue().getDeliverySnapshot());
        assertThat(snapshot.path("renderedTicketArtifacts").get(0).asText()).isEqualTo("TICKET-REC-1");
        verify(authorization).recordAudit(booking.getBookingCode(), 3L, "MANAGER", 9L,
                "CONFIRMATION_RESEND", "ACCEPTED", "corr-resend", null);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof BookingNotificationReady ready
                && ready.notificationKind().equals("CONFIRMATION_RESEND")));
    }

    @Test
    void selfServiceResendIsRateLimitedAfterThreeAcceptedRequestsInOneHour() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "selfServiceResendLimit", 3);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "selfServiceResendWindowSeconds", 3600L);
        WorkshopBooking booking = confirmedBooking();
        when(bookings.findLockedByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(
                BookingSessionCommitment.builder().locationId(3L).ordinal(1).build()));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision(true));
        when(auditEvidenceRepository.countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                eq(booking.getBookingCode()), eq("CONFIRMATION_RESEND"), eq("ACCEPTED"), anyCollection(), any()))
                .thenReturn(3L);

        BookingRecoveryCommandResult result = service.requestConfirmationDelivery(null, booking.getBookingCode(),
                "buyer@example.test", null, "GUEST", null, "corr-self-limit", claim());

        assertThat(result.statusCode()).isEqualTo(429);
        var actors = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(auditEvidenceRepository).countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                eq(booking.getBookingCode()), eq("CONFIRMATION_RESEND"), eq("ACCEPTED"), actors.capture(), any());
        assertThat(actors.getValue()).containsExactlyInAnyOrder("GUEST", "MEMBER");
        verifyNoInteractions(tickets, notifications, eventPublisher, authorization);
    }

    @Test
    void guestSelfServiceResendWithStoredProofQueuesExistingTicketsAndWritesRedactedAudit() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "selfServiceResendLimit", 3);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "selfServiceResendWindowSeconds", 3600L);
        WorkshopBooking booking = confirmedBooking();
        when(bookings.findLockedByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(
                BookingSessionCommitment.builder().locationId(3L).ordinal(1).build()));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision(true));
        when(auditEvidenceRepository.countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                eq(booking.getBookingCode()), eq("CONFIRMATION_RESEND"), eq("ACCEPTED"), anyCollection(), any()))
                .thenReturn(0L);
        when(tickets.findByBookingId(booking.getId())).thenReturn(List.of(
                BookingTicket.builder().ticketCode("TICKET-GUEST-1").build()));
        String token = "guest-resend-access-token";
        when(magicLinks.findByBookingIdAndLifecycleStatusOrderByIdAsc(booking.getId(), MagicLinkLifecycleStatus.ACTIVE))
                .thenReturn(List.of(activeLink(booking, sha256(token))));
        when(notifications.findByBookingId(booking.getId())).thenReturn(List.of(BookingNotificationIntent.builder()
                .deliverySnapshot("{\"bookingAccessLink\":\"/workshop-booking/access#" + token
                        + "\",\"storedRecipient\":\"stored@example.test\"}").build()));
        TransactionSynchronizationManager.initSynchronization();
        Instant before = Instant.now();

        BookingRecoveryCommandResult result = service.requestConfirmationDelivery(null, booking.getBookingCode(),
                "buyer@example.test", null, "GUEST", null, "corr-guest-resend", claim());

        assertThat(result.statusCode()).isEqualTo(202);
        var recentActors = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        var cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(auditEvidenceRepository).countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                eq(booking.getBookingCode()), eq("CONFIRMATION_RESEND"), eq("ACCEPTED"),
                recentActors.capture(), cutoff.capture());
        assertThat(recentActors.getValue()).containsExactlyInAnyOrder("GUEST", "MEMBER");
        assertThat(cutoff.getValue()).isBetween(before.minusSeconds(3600), Instant.now().minusSeconds(3599));

        var notification = org.mockito.ArgumentCaptor.forClass(BookingNotificationIntent.class);
        verify(notifications).save(notification.capture());
        JsonNode snapshot = mapper.readTree(notification.getValue().getDeliverySnapshot());
        assertThat(notification.getValue().getNotificationKind()).isEqualTo("CONFIRMATION_RESEND");
        assertThat(snapshot.path("renderedTicketArtifacts").get(0).asText()).isEqualTo("TICKET-GUEST-1");
        verify(authorization).recordAudit(booking.getBookingCode(), 3L, "GUEST", null,
                "CONFIRMATION_RESEND", "ACCEPTED", "corr-guest-resend", null);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof BookingNotificationReady ready
                && ready.notificationKind().equals("CONFIRMATION_RESEND")));
    }

    @Test
    void latePaymentFollowupRequiresVerifiedEvidenceAndAuditsManagerDecisionWithoutChangingBooking() {
        WorkshopBooking booking = booking();
        when(bookings.findLockedByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(
                BookingSessionCommitment.builder().locationId(3L).ordinal(1).build()));
        when(authorization.requireLatePaymentFollowupAuthority(anyString(), eq(3L), eq(booking.getBookingCode()),
                eq("RECORD"), eq("verified bank payment after expiry"), eq("corr-late"))).thenReturn(manager());
        when(latePaymentEvidenceRepository.existsByBookingCode(booking.getBookingCode())).thenReturn(false, true);

        BookingRecoveryCommandResult missing = service.handleLatePaymentFollowup("Bearer manager", booking.getBookingCode(),
                "RECORD", "verified bank payment after expiry", "corr-late", claim());
        assertThat(missing.statusCode()).isEqualTo(409);
        verify(authorization, never()).recordAudit(anyString(), any(), anyString(), any(), anyString(), anyString(), anyString(), any());

        BookingRecoveryCommandResult accepted = service.handleLatePaymentFollowup("Bearer manager", booking.getBookingCode(),
                "RECORD", "verified bank payment after expiry", "corr-late", claim());
        assertThat(accepted.statusCode()).isEqualTo(200);
        verify(authorization).recordAudit(booking.getBookingCode(), 3L, "MANAGER", 9L,
                "LATE_PAYMENT_RECORD", "ACCEPTED", "corr-late", "verified bank payment after expiry");

        when(authorization.requireLatePaymentFollowupAuthority(anyString(), eq(3L), eq(booking.getBookingCode()),
                eq("ESCALATE"), eq("requires manual follow-up"), eq("corr-escalate"))).thenReturn(manager());
        BookingRecoveryCommandResult escalated = service.handleLatePaymentFollowup("Bearer manager", booking.getBookingCode(),
                "ESCALATE", "requires manual follow-up", "corr-escalate", claim());
        assertThat(escalated.statusCode()).isEqualTo(200);
        verify(authorization).recordAudit(booking.getBookingCode(), 3L, "MANAGER", 9L,
                "LATE_PAYMENT_ESCALATE", "ACCEPTED", "corr-escalate", "requires manual follow-up");
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    private void stubBase(WorkshopBooking booking, AccountSummary manager, List<MagicLinkAccess> active) {
        when(bookings.findLockedByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId())).thenReturn(List.of(
                BookingSessionCommitment.builder().locationId(3L).ordinal(1).build()));
        when(authorization.requireMagicLinkIncidentAuthority(anyString(), eq(3L), eq(booking.getBookingCode()),
                anyString(), anyString(), anyString())).thenReturn(manager);
        when(magicLinks.findByBookingIdAndLifecycleStatusOrderByIdAsc(booking.getId(), MagicLinkLifecycleStatus.ACTIVE))
                .thenReturn(active);
    }

    private WorkshopBooking booking() {
        return WorkshopBooking.builder().id(12L).bookingCode("BK-REC-12").contactEmail("stored@example.test")
                .contactPhone("0900000000").participantCount(1).bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID).build();
    }

    private WorkshopBooking confirmedBooking() {
        WorkshopBooking booking = booking();
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
        booking.setMemberAccountId(15L);
        return booking;
    }

    private MagicLinkAccess activeLink(WorkshopBooking booking, String hash) {
        return MagicLinkAccess.builder().id(1L).booking(booking).tokenHash(hash)
                .lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE).expiresAt(Instant.now().plusSeconds(600)).build();
    }

    private AccountSummary manager() {
        return new AccountSummary(9L, ActorRole.MANAGER, ActorStatus.ACTIVE, "manager@example.test", null, null);
    }

    private BookingIdempotencyClaim claim() { return BookingIdempotencyClaim.live(5L, 2L); }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
