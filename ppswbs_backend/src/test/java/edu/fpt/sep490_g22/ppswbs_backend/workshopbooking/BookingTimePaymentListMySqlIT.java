package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.TimezoneConfiguration;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingTimePaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupConfirmationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupPaymentEvidenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T150 on MySQL: {@code WorkshopBookingFacade.listBookingTimePayments} over stored group payment attempts, with
 * the day and location filters, the three statuses, a multi-location group counted once, excess money left out, a
 * mismatched IPN never listed with the received amount, and no VNPay call and no write.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class BookingTimePaymentListMySqlIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EMAIL = "list.buyer@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkshopBookingFacade facade;
    @Autowired private GroupConfirmationService confirmation;
    @Autowired private GroupPaymentEvidenceService evidence;
    @Autowired private BookingGroupRepository groups;
    @Autowired private GroupPaymentAttemptRepository attempts;
    @Autowired private GroupExcessPaymentRepository excess;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private VnpayMerchantOperationsPort vnpay;

    private WorkshopLocation one;
    private WorkshopLocation two;
    private int hourOffset;
    private final LocalDate today = LocalDate.now(TimezoneConfiguration.ZONE_ID_VIETNAM);

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "BookingTimePaymentListMySqlIT requires real MySQL evidence");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("group_excess_payments", "late_payment_reviews",
                "booking_group_notification_intents", "group_payment_attempts", "booking_group_magic_links",
                "ticket_price_snapshots", "ticket_bookings", "booking_group_line_sessions", "booking_group_lines",
                "booking_groups", "workshop_cart_quotes", "booking_idempotency_records", "booking_audit_evidence",
                "facade_command_idempotency_records", "ticket_session_entitlements", "attendance_tickets",
                "capacity_holds", "operations_audit_events", "workshop_sessions", "workshop_slots",
                "workshop_packages", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        one = locations.save(WorkshopLocation.builder().code("LOC-P1").displayName("Pay Hub 1")
                .addressSnapshot("1 Pay St").active(true).build());
        two = locations.save(WorkshopLocation.builder().code("LOC-P2").displayName("Pay Hub 2")
                .addressSnapshot("2 Pay St").active(true).build());
        hourOffset = 0;
    }

    @Test
    @DisplayName("T150-1: a confirmed online payment is listed for its location and day, not for another location or day")
    void confirmedPaymentFilteredByDayAndLocation() throws Exception {
        String code = pendingGroup(List.of(new Line(3_000_000, one, 2)));
        long collected = groups.findByGroupCode(code).orElseThrow().getGroupCollectionVnd();
        confirmation.confirmPaidGroup(code, collected, "ipn-" + code, "op-" + code, "corr");
        GroupPaymentAttempt attempt = attempt(code, collected, PaymentAttemptStatus.SUCCESS, Instant.now());

        List<BookingTimePayment> listed = facade.listBookingTimePayments(one.getId(), today);

        assertThat(listed).hasSize(1);
        BookingTimePayment p = listed.get(0);
        assertThat(p.bookingGroupCode()).isEqualTo(code);
        assertThat(p.amountVnd()).isEqualTo(collected);
        assertThat(p.verificationStatus()).isEqualTo(BookingTimePaymentStatus.CONFIRMED);
        assertThat(p.method()).isEqualTo(BookingTimePayment.METHOD_VNPAY_ONLINE);
        assertThat(p.merchantReference()).isEqualTo(attempt.getMerchantReference());
        assertThat(p.attemptReference()).isEqualTo(attempt.getAttemptReference());
        assertThat(p.ticketCodes()).hasSize(2);
        assertThat(p.locationIds()).containsExactly(one.getId());
        assertThat(p.confirmedAt()).isEqualTo(groups.findByGroupCode(code).orElseThrow().getConfirmedAt());
        assertThat(facade.listBookingTimePayments(two.getId(), today)).isEmpty();
        assertThat(facade.listBookingTimePayments(one.getId(), today.minusDays(1))).isEmpty();
        assertThat(facade.listBookingTimePayments(one.getId(), today.plusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("T150-2: the three statuses show the stored amount; confirmedAt is set only for CONFIRMED")
    void threeStatuses() throws Exception {
        String confirmed = pendingGroup(List.of(new Line(100_000, one, 1)));
        confirmation.confirmPaidGroup(confirmed, 100_000, "ipn-" + confirmed, "op-" + confirmed, "corr");
        attempt(confirmed, 100_000, PaymentAttemptStatus.SUCCESS, Instant.now());
        String late = pendingGroup(List.of(new Line(200_000, one, 1)));
        attempt(late, 200_000, PaymentAttemptStatus.SUCCESS, Instant.now());
        String unverified = pendingGroup(List.of(new Line(300_000, one, 1)));
        attempt(unverified, 300_000, PaymentAttemptStatus.PENDING, Instant.now());

        List<BookingTimePayment> listed = facade.listBookingTimePayments(one.getId(), today);

        assertThat(listed).hasSize(3);
        assertThat(by(listed, confirmed).verificationStatus()).isEqualTo(BookingTimePaymentStatus.CONFIRMED);
        assertThat(by(listed, confirmed).confirmedAt()).isNotNull();
        assertThat(by(listed, late).verificationStatus()).isEqualTo(BookingTimePaymentStatus.LATE_PENDING_REVIEW);
        assertThat(by(listed, late).amountVnd()).isEqualTo(200_000);
        assertThat(by(listed, late).confirmedAt()).isNull();
        assertThat(by(listed, unverified).verificationStatus()).isEqualTo(BookingTimePaymentStatus.UNVERIFIED);
        assertThat(by(listed, unverified).amountVnd()).isEqualTo(300_000);
        assertThat(by(listed, unverified).confirmedAt()).isNull();
    }

    @Test
    @DisplayName("T150-3: a three-ticket group at two locations appears under both with both locationIds and counts once")
    void multiLocationGroupCountsOnce() throws Exception {
        String code = pendingGroup(List.of(new Line(100_000, one, 2), new Line(50_000, two, 1)));
        attempt(code, 250_000, PaymentAttemptStatus.PENDING, Instant.now());

        List<BookingTimePayment> atOne = facade.listBookingTimePayments(one.getId(), today);
        List<BookingTimePayment> atTwo = facade.listBookingTimePayments(two.getId(), today);

        assertThat(atOne).hasSize(1);
        assertThat(atTwo).hasSize(1);
        assertThat(atOne.get(0).locationIds()).containsExactlyInAnyOrder(one.getId(), two.getId());
        assertThat(atTwo.get(0).locationIds()).containsExactlyInAnyOrder(one.getId(), two.getId());
        assertThat(atOne.get(0).ticketCodes()).hasSize(3);
        // whole-system day total: each payment once, by merchant reference
        long total = java.util.stream.Stream.concat(atOne.stream(), atTwo.stream())
                .collect(java.util.stream.Collectors.toMap(BookingTimePayment::merchantReference,
                        BookingTimePayment::amountVnd, (a, b) -> a)).values().stream().mapToLong(Long::longValue).sum();
        assertThat(total).isEqualTo(250_000);
    }

    @Test
    @DisplayName("T150-4: an unverified or late attempt is dated by its creation time, in Vietnam time")
    void unverifiedDatedByCreation() throws Exception {
        String code = pendingGroup(List.of(new Line(100_000, one, 1)));
        Instant fiveDaysAgo = Instant.now().minus(Duration.ofDays(5));
        attempt(code, 100_000, PaymentAttemptStatus.PENDING, fiveDaysAgo);

        assertThat(facade.listBookingTimePayments(one.getId(), today)).isEmpty();
        LocalDate createdDay = fiveDaysAgo.atZone(TimezoneConfiguration.ZONE_ID_VIETNAM).toLocalDate();
        assertThat(facade.listBookingTimePayments(one.getId(), createdDay)).hasSize(1);
    }

    @Test
    @DisplayName("T150-5: excess money recorded in group_excess_payments is not listed as a booking-time payment")
    void excessAttemptNotListed() throws Exception {
        String code = pendingGroup(List.of(new Line(100_000, one, 1)));
        confirmation.confirmPaidGroup(code, 100_000, "ipn-" + code, "op-" + code, "corr");
        attempt(code, 100_000, PaymentAttemptStatus.SUCCESS, Instant.now());
        GroupPaymentAttempt second = attempt(code, 100_000, PaymentAttemptStatus.SUCCESS, Instant.now());
        excess.save(GroupExcessPayment.builder().group(groups.findByGroupCode(code).orElseThrow())
                .verifiedEvidenceReference(second.getMerchantReference()).merchantReference(second.getMerchantReference())
                .amountVnd(100_000).receivedAt(Instant.now()).refundRequestReference("RR-" + UUID.randomUUID())
                .refundOutcome("ACCEPTED").build());

        List<BookingTimePayment> listed = facade.listBookingTimePayments(one.getId(), today);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).merchantReference()).isNotEqualTo(second.getMerchantReference());
    }

    @Test
    @DisplayName("T150-6: an IPN with a mismatched amount is rejected and the payment stays UNVERIFIED at the requested amount")
    void mismatchedIpnNotListedWithReceivedAmount() throws Exception {
        String code = pendingGroup(List.of(new Line(100_000, one, 1)));
        GroupPaymentAttempt attempt = attempt(code, 100_000, PaymentAttemptStatus.PENDING, Instant.now());

        var outcome = evidence.apply(code, attempt.getMerchantReference(), 99_000, "VND", "ipn-" + code,
                "op-" + code, "corr");

        assertThat(outcome.reason()).isEqualTo(BookingRejectionReason.PAYMENT_MISMATCH);
        List<BookingTimePayment> listed = facade.listBookingTimePayments(one.getId(), today);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).amountVnd()).isEqualTo(100_000);
        assertThat(listed.get(0).verificationStatus()).isEqualTo(BookingTimePaymentStatus.UNVERIFIED);
        assertThat(listed).noneMatch(p -> p.amountVnd() == 99_000);
    }

    @Test
    @DisplayName("T150-7: listing calls no VNPay endpoint and writes nothing")
    void readOnlyAndNoVnpayCall() throws Exception {
        String code = pendingGroup(List.of(new Line(100_000, one, 1)));
        confirmation.confirmPaidGroup(code, 100_000, "ipn-" + code, "op-" + code, "corr");
        attempt(code, 100_000, PaymentAttemptStatus.SUCCESS, Instant.now());
        String before = fingerprint();

        facade.listBookingTimePayments(one.getId(), today);
        facade.listBookingTimePayments(two.getId(), today);

        assertThat(fingerprint()).isEqualTo(before);
        assertThat(mockingDetails(vnpay).getInvocations()).isEmpty();
    }

    // ---- helpers ----

    private record Line(long price, WorkshopLocation location, int participants) { }

    private BookingTimePayment by(List<BookingTimePayment> list, String code) {
        return list.stream().filter(p -> p.bookingGroupCode().equals(code)).findFirst().orElseThrow();
    }

    private String fingerprint() {
        return jdbc.queryForObject("""
                SELECT CONCAT((SELECT COUNT(*) FROM group_payment_attempts), '|',
                    (SELECT COALESCE(SUM(UNIX_TIMESTAMP(updated_at)),0) FROM group_payment_attempts), '|',
                    (SELECT COUNT(*) FROM booking_groups), '|',
                    (SELECT COALESCE(SUM(UNIX_TIMESTAMP(updated_at)),0) FROM booking_groups), '|',
                    (SELECT COUNT(*) FROM booking_audit_evidence), '|',
                    (SELECT COUNT(*) FROM group_excess_payments))""", String.class);
    }

    private GroupPaymentAttempt attempt(String code, long amount, PaymentAttemptStatus status, Instant createdAt) {
        BookingGroup group = groups.findByGroupCode(code).orElseThrow();
        return attempts.save(GroupPaymentAttempt.builder().group(group).attemptReference("ATT-" + UUID.randomUUID())
                .merchantReference("MER-" + UUID.randomUUID().toString().substring(0, 12)).amountVnd(amount)
                .attemptStatus(status).expiresAt(Instant.now().plusSeconds(600)).createdAt(createdAt).build());
    }

    private String pendingGroup(List<Line> lines) throws Exception {
        StringBuilder json = new StringBuilder("{\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            Long pkg = packages.save(WorkshopPackage.builder().name("List package " + UUID.randomUUID())
                    .description("fixture").publicPrice(line.price()).minParticipants(1).maxParticipants(10)
                    .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                    .state(WorkshopPackageState.PUBLISHED).build()).getId();
            WorkshopSession session = session(line.location(), 10);
            if (i > 0) json.append(',');
            json.append("{\"packageId\":").append(pkg).append(",\"sessionReferences\":[\"")
                    .append(session.getReference()).append("\"],\"participantCount\":")
                    .append(line.participants()).append('}');
        }
        json.append("]}");
        MvcResult quote = mockMvc.perform(post("/api/v1/workshop-cart-quotes").contentType(MediaType.APPLICATION_JSON)
                .content(json.toString())).andExpect(status().isCreated()).andReturn();
        String quoteRef = JSON.readTree(quote.getResponse().getContentAsString()).get("quoteRef").asText();
        MvcResult created = mockMvc.perform(post("/api/v1/workshop-booking-groups")
                        .header("Idempotency-Key", "list-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"" + EMAIL + "\",\"contactPhone\":\"+84900000150\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(created.getResponse().getContentAsString()).get("groupCode").asText();
    }

    private WorkshopSession session(WorkshopLocation location, int daysAhead) {
        Instant start = Instant.now().plus(Duration.ofDays(daysAhead)).plus(hourOffset++, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.MINUTES);
        var zone = TimezoneConfiguration.ZONE_ID_VIETNAM;
        LocalTime startTime = start.atZone(zone).toLocalTime();
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(10)
                .active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED)
                .build());
    }
}
