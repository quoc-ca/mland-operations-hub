package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBookingMlandRefundPolicySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.time.Duration;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class BookingConfirmationIT {

    @Autowired private BookingLifecycleService lifecycle;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private BookingCapacityHoldRepository holds;
    @Autowired private BookingTicketRepository tickets;
    @Autowired private BookingTicketSessionEntitlementRepository entitlements;
    @Autowired private BookingNotificationIntentRepository notificationIntents;
    @Autowired private WorkshopBookingNotificationFacade bookingNotificationFacade;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationListener notificationListener;
    @Autowired private SettlementBookingMlandRefundPolicySnapshotRepository mlandPolicySnapshots;

    @MockBean private WorkshopOperationsFacade operations;
    @MockBean private AccountFacade accountFacade;

    private Long locationId;

    @BeforeEach
    void setUp() {
        notificationListener.clear();
        locationId = jdbc.queryForList("SELECT id FROM workshop_locations LIMIT 1", Long.class)
                .stream().findFirst().orElseGet(() -> {
                    jdbc.update("INSERT INTO workshop_locations (code, display_name, address_snapshot, active, created_at, updated_at) "
                            + "VALUES ('LOC-CONFIRM-IT', 'Confirmation Test', 'Test address', 1, NOW(), NOW())");
                    return jdbc.queryForObject("SELECT id FROM workshop_locations WHERE code = 'LOC-CONFIRM-IT'", Long.class);
                });
    }

    @Test
    void silverclayConfirmationIssuesOneSafeTicketPerParticipantWithBothSessionEntitlements() throws Exception {
        String code = "BK-SILVER-" + UUID.randomUUID().toString().substring(0, 8);
        List<UUID> sessions = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> holdRefs = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<SessionEntitlementProjection> safeEntitlements = List.of(
                entitlement(sessions.get(0), 1), entitlement(sessions.get(1), 2));
        List<DeliverySafeTicket> safeTickets = List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-" + code + "-1", "TK-" + code + "-1", safeEntitlements),
                new DeliverySafeTicket(UUID.randomUUID(), "QR-" + code + "-2", "TK-" + code + "-2", safeEntitlements));
        configureReservation(sessions, holdRefs);
        when(operations.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(operations.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(safeTickets));
        createPending(code, "Silverclay", sessions, 2);
        int publicationsBeforeConfirmation = countMlandConfirmationPublications();

        lifecycle.confirmBookingPaymentAndCommitHolds(code, "EVT-" + code, "VNPAY", 200_000L,
                "HASH-" + code, "OP-" + code, "CORR-" + code);

        WorkshopBooking confirmed = bookings.findByBookingCode(code).orElseThrow();
        assertThat(confirmed.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(mlandPolicySnapshots.findByBookingReference(code)).isPresent());
        var policySnapshot = mlandPolicySnapshots.findByBookingReference(code).orElseThrow();
        assertThat(policySnapshot.getConfirmedAt()).isEqualTo(confirmed.getConfirmedAt());
        assertThat(policySnapshot.getPolicyReference()).isEqualTo("MLAND-CANCELLATION-DEFAULT-100");
        assertThat(policySnapshot.getRefundPercent()).isEqualTo((short) 100);
        assertThat(notificationListener.mlandConfirmations()).containsExactly(
                new MlandBookingConfirmedEvent(code, confirmed.getConfirmedAt()));
        assertThat(countMlandConfirmationPublications()).isEqualTo(publicationsBeforeConfirmation + 1);
        lifecycle.confirmBookingPaymentAndCommitHolds(code, "EVT-REPLAY-" + code, "VNPAY", 200_000L,
                "HASH-REPLAY-" + code, "OP-REPLAY-" + code, "CORR-REPLAY-" + code);
        assertThat(mlandPolicySnapshots.findByBookingReference(code).orElseThrow().getSnapshotReference())
                .isEqualTo(policySnapshot.getSnapshotReference());
        assertThat(notificationListener.mlandConfirmations()).containsExactly(
                new MlandBookingConfirmedEvent(code, confirmed.getConfirmedAt()));
        assertThat(countMlandConfirmationPublications()).isEqualTo(publicationsBeforeConfirmation + 1);
        assertThat(holds.findByBookingId(confirmed.getId())).hasSize(2)
                .allSatisfy(hold -> assertThat(hold.getHoldStatus()).isEqualTo("COMMITTED"));
        List<BookingTicket> issued = tickets.findByBookingId(confirmed.getId());
        assertThat(issued).hasSize(2);
        assertThat(issued).extracting(BookingTicket::getTicketCode)
                .containsExactlyInAnyOrder("TK-" + code + "-1", "TK-" + code + "-2");
        for (BookingTicket ticket : issued) {
            assertThat(ticket.getQrReference()).isIn(safeTickets.stream()
                    .map(DeliverySafeTicket::qrReference).toList());
            assertThat(entitlements.findByBookingTicketId(ticket.getId())).hasSize(2);
        }

        BookingNotificationIntent intent = notificationIntents.findByBookingId(confirmed.getId()).getFirst();
        BookingNotificationDeliveryPayload payload = bookingNotificationFacade.getBookingNotificationDeliveryPayload(
                new BookingNotificationDeliveryPayloadQuery(intent.getNotificationReference()));
        String safePayload = objectMapper.writeValueAsString(payload);
        assertThat(payload.renderedTicketArtifacts()).containsExactlyInAnyOrder("TK-" + code + "-1", "TK-" + code + "-2");
        assertThat(safePayload).doesNotContain("attendance", "checkedIn", "ATTENDANCE_FINALIZED");
        assertThat(safePayload).doesNotContain(safeTickets.getFirst().ticketReference().toString());
    }

    @Test
    void rejectedAllHoldsCommitLeavesBookingPendingAndIssuesNoTicket() {
        String code = "BK-COMMIT-REJECT-" + UUID.randomUUID().toString().substring(0, 8);
        List<UUID> sessions = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> holdRefs = List.of(UUID.randomUUID(), UUID.randomUUID());
        configureReservation(sessions, holdRefs);
        when(operations.commitBookingHolds(any())).thenReturn(
                CapacityCommitOutcome.rejected(RejectionReason.HOLD_MISSING_OR_EXPIRED));
        WorkshopBooking pending = createPending(code, "Silverclay", sessions, 2);

        assertThatThrownBy(() -> lifecycle.confirmBookingPaymentAndCommitHolds(code, "EVT-" + code,
                "VNPAY", 200_000L, "HASH-" + code, "OP-" + code, "CORR-" + code))
                .isInstanceOf(BookingConflictException.class);

        assertThat(bookings.findByBookingCode(code).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(holds.findByBookingId(pending.getId())).hasSize(2)
                .allSatisfy(hold -> assertThat(hold.getHoldStatus()).isEqualTo("RESERVED"));
        assertThat(tickets.findByBookingId(pending.getId())).isEmpty();
        assertThat(notificationIntents.findByBookingId(pending.getId())).isEmpty();
        verify(operations, never()).getBookingTicketProjection(any());
    }

    @Test
    void postCommitDeliveryFailureDoesNotReverseConfirmation() {
        String code = "BK-DELIVERY-FAIL-" + UUID.randomUUID().toString().substring(0, 8);
        List<UUID> sessions = List.of(UUID.randomUUID());
        List<UUID> holdRefs = List.of(UUID.randomUUID());
        configureReservation(sessions, holdRefs);
        when(operations.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(operations.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-" + code, "TK-" + code,
                        List.of(entitlement(sessions.getFirst(), 1))))));
        WorkshopBooking pending = createPending(code, "Basic Ring", sessions, 1);
        notificationListener.failNextDelivery();

        lifecycle.confirmBookingPaymentAndCommitHolds(code, "EVT-" + code, "VNPAY", 100_000L,
                "HASH-" + code, "OP-" + code, "CORR-" + code);

        assertThat(bookings.findByBookingCode(code).orElseThrow().getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(holds.findByBookingId(pending.getId())).singleElement()
                .extracting(BookingCapacityHold::getHoldStatus).isEqualTo("COMMITTED");
        assertThat(tickets.findByBookingId(pending.getId())).hasSize(1);
        assertThat(notificationIntents.findByBookingId(pending.getId())).hasSize(1);
        assertThat(notificationListener.deliveryFailures()).isEqualTo(1);
    }

    private WorkshopBooking createPending(String code, String packageName, List<UUID> sessions, int participants) {
        return lifecycle.createPendingBookingReservation(code, 42L, packageName, "buyer@example.test", "0900000000",
                7L, participants, sessions, 200_000L, 200_000L * participants,
                "OP-RES-" + code, "CORR-RES-" + code);
    }

    private void configureReservation(List<UUID> sessions, List<UUID> holdRefs) {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        when(operations.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(true, "PUBLISHED",
                locationId, start, start.plusSeconds(3600), 10));
        when(operations.reserveBookingCapacity(any())).thenReturn(
                CapacityHoldOutcome.accepted(holdRefs, Instant.now().plus(15, ChronoUnit.MINUTES)));
    }

    private SessionEntitlementProjection entitlement(UUID sessionReference, int dayOffset) {
        Instant start = Instant.now().plus(dayOffset, ChronoUnit.DAYS);
        return new SessionEntitlementProjection(sessionReference, locationId, start, start.plusSeconds(3600));
    }

    private int countMlandConfirmationPublications() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE event_type = ?",
                Integer.class, MlandBookingConfirmedEvent.class.getName());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean NotificationListener notificationListener() { return new NotificationListener(); }
    }

    static class NotificationListener {
        private final AtomicBoolean failNext = new AtomicBoolean();
        private final CopyOnWriteArrayList<BookingNotificationReady> received = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<MlandBookingConfirmedEvent> mlandConfirmations = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failed = new AtomicBoolean();

        @EventListener
        public void onReady(BookingNotificationReady event) {
            received.add(event);
            if (failNext.compareAndSet(true, false)) {
                failed.set(true);
                throw new IllegalStateException("Simulated post-commit notification failure");
            }
        }

        @EventListener
        public void onMlandBookingConfirmed(MlandBookingConfirmedEvent event) {
            mlandConfirmations.add(event);
        }

        void failNextDelivery() { failed.set(false); failNext.set(true); }
        int deliveryFailures() { return failed.get() ? 1 : 0; }
        List<MlandBookingConfirmedEvent> mlandConfirmations() { return List.copyOf(mlandConfirmations); }
        void clear() { failNext.set(false); failed.set(false); received.clear(); mlandConfirmations.clear(); }
    }
}
