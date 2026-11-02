package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MagicLinkAccessRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GuestLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkAccess;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLookupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MagicLinkService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Integration coverage for customer lookup and persisted Magic-Link recovery state. */
@SpringBootTest(properties = "workshop.booking.auth.allow-test-tokens=true")
@ActiveProfiles("test")
class BookingLookupAndRecoveryIT {

    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private BookingSessionCommitmentRepository commitments;
    @Autowired private MagicLinkAccessRepository magicLinks;
    @Autowired private BookingLookupService bookingLookup;
    @Autowired private MagicLinkService magicLinkService;

    @MockBean private AccountFacade accountFacade;
    @MockBean private WorkshopOperationsFacade operationsFacade;

    @Test
    void guestProofProjectsPendingAndCancelledBookingsWithoutTicketOrContactDisclosure() {
        UUID pendingSession = UUID.randomUUID();
        WorkshopBooking pending = saveBooking(BookingStatus.PENDING_PAYMENT, null);
        saveCommitment(pending, pendingSession);
        when(operationsFacade.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                pendingSession, false, "SALES_PAUSED", 31L, Instant.parse("2026-11-15T09:00:00Z"),
                Instant.parse("2026-11-15T10:00:00Z"), 0, null));

        BookingSummaryResponse pendingProjection = bookingLookup.lookupGuest(GuestLookupRequest.builder()
                .bookingCode(pending.getBookingCode()).contactEmail(" guest@example.test ").build());

        assertThat(pendingProjection.getBookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(pendingProjection.getSessions()).hasSize(1);
        assertThat(pendingProjection.getTickets()).isEmpty();

        UUID cancelledSession = UUID.randomUUID();
        WorkshopBooking cancelled = saveBooking(BookingStatus.CANCELLED, null);
        saveCommitment(cancelled, cancelledSession);
        when(operationsFacade.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                cancelledSession, false, "CLOSED", 31L, Instant.parse("2026-11-16T09:00:00Z"),
                Instant.parse("2026-11-16T10:00:00Z"), 0, null));

        BookingSummaryResponse cancelledProjection = bookingLookup.lookupGuest(GuestLookupRequest.builder()
                .bookingCode(cancelled.getBookingCode()).contactPhone("0900000000").build());

        assertThat(cancelledProjection.getBookingStatus()).isEqualTo("CANCELLED");
        assertThat(cancelledProjection.getSessions()).hasSize(1);
        assertThat(cancelledProjection.getTickets()).isEmpty();
    }

    @Test
    void memberOwnershipIsResolvedFromCanonicalAccountAndRejectsAnotherMember() {
        WorkshopBooking booking = saveBooking(BookingStatus.PENDING_PAYMENT, 802L);
        UUID session = UUID.randomUUID();
        saveCommitment(booking, session);
        when(accountFacade.resolveAccount("member-uid")).thenReturn(Optional.of(new AccountSummary(
                802L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@example.test", null, null)));
        when(accountFacade.resolveAccount("other-uid")).thenReturn(Optional.of(new AccountSummary(
                803L, ActorRole.MEMBER, ActorStatus.ACTIVE, "other@example.test", null, null)));
        when(operationsFacade.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                session, true, "PUBLISHED", 31L, Instant.parse("2026-11-17T09:00:00Z"),
                Instant.parse("2026-11-17T10:00:00Z"), 3, null));

        assertThat(bookingLookup.lookupMember("Bearer test-uid:member-uid", booking.getBookingCode())
                .getBookingCode()).isEqualTo(booking.getBookingCode());
        assertThatThrownBy(() -> bookingLookup.lookupMember("Bearer test-uid:other-uid", booking.getBookingCode()))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void persistedMagicLinkCanBeReusedUntilExpiryAndExpiredOrRevokedLinksRevealNothing() {
        WorkshopBooking booking = saveBooking(BookingStatus.PENDING_PAYMENT, null);
        UUID session = UUID.randomUUID();
        saveCommitment(booking, session);
        when(operationsFacade.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                session, false, "CLOSED", 31L, Instant.parse("2026-11-18T09:00:00Z"),
                Instant.parse("2026-11-18T10:00:00Z"), 0, null));

        String reusableToken = "integration-reusable-" + UUID.randomUUID();
        persistLink(booking, reusableToken, MagicLinkLifecycleStatus.ACTIVE, Instant.now().plusSeconds(300));
        assertThat(magicLinkService.exchange(reusableToken).getBookingCode()).isEqualTo(booking.getBookingCode());
        assertThat(magicLinkService.exchange(reusableToken).getBookingCode()).isEqualTo(booking.getBookingCode());

        String expiredToken = "integration-expired-" + UUID.randomUUID();
        persistLink(booking, expiredToken, MagicLinkLifecycleStatus.ACTIVE, Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> magicLinkService.exchange(expiredToken)).isInstanceOf(BookingNotFoundException.class);

        String revokedToken = "integration-revoked-" + UUID.randomUUID();
        persistLink(booking, revokedToken, MagicLinkLifecycleStatus.REVOKED, Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> magicLinkService.exchange(revokedToken)).isInstanceOf(BookingNotFoundException.class);
    }

    private WorkshopBooking saveBooking(BookingStatus status, Long memberId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return bookings.save(WorkshopBooking.builder().bookingCode("BK-LOOK-" + suffix).packageId(77L)
                .packageNameSnapshot("Integration package").contactEmail("guest@example.test")
                .contactPhone("0900000000").memberAccountId(memberId).participantCount(1)
                .bookingStatus(status).paymentStatus(status == BookingStatus.CONFIRMED
                        ? PaymentStatus.DEPOSIT_PAID : PaymentStatus.UNPAID)
                .holdExpiresAt(Instant.now().plusSeconds(600)).build());
    }

    private void saveCommitment(WorkshopBooking booking, UUID session) {
        commitments.save(BookingSessionCommitment.builder().booking(booking).sessionReference(session.toString())
                .locationId(31L).ordinal(1).seatCount(booking.getParticipantCount()).build());
    }

    private void persistLink(WorkshopBooking booking, String token, MagicLinkLifecycleStatus status, Instant expiry) {
        magicLinks.save(MagicLinkAccess.builder().booking(booking).tokenHash(sha256(token))
                .lifecycleStatus(status).expiresAt(expiry).build());
    }

    private static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
