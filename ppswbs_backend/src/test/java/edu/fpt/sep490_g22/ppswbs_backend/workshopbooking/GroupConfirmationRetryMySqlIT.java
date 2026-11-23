package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.TimezoneConfiguration;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupConfirmationRetryScheduler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupHoldExpiryScheduler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupLoyaltyOutcomeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T145 on MySQL through the real IPN route: verified evidence that arrives inside the hold while 003.2 (hold
 * commit) or 006 (benefit settlement) is unavailable is kept (ingress receipt, attempt SUCCESS, a WAITING retry row)
 * instead of being rolled back; a scheduler confirms the group once when the dependency is back; nothing is released
 * while it waits; at {@code holdExpiresAt} the late-payment review is created and the group released.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class GroupConfirmationRetryMySqlIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String IPN = "/api/v1/integrations/payments/vnpay/events";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentOwnerRegistry owners;
    @Autowired private BookingGroupRepository groups;
    @Autowired private GroupPaymentAttemptRepository attempts;
    @Autowired private GroupConfirmationRetryRepository retryRows;
    @Autowired private LatePaymentReviewRepository reviews;
    @Autowired private GroupConfirmationRetryScheduler retryScheduler;
    @Autowired private GroupHoldExpiryScheduler expiry;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private VnpayChecksumService checksum;
    @SpyBean private WorkshopOperationsAdapter operationsAdapter;
    @SpyBean private GroupLoyaltyOutcomeService loyaltyOutcome;

    private WorkshopLocation location;
    private int hourOffset;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "GroupConfirmationRetryMySqlIT requires real MySQL evidence");
        }
        Mockito.reset(operationsAdapter, loyaltyOutcome);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("group_confirmation_retries", "group_excess_payments",
                "payment_ingress_receipts", "payment_owner_registrations", "late_payment_reviews",
                "booking_group_notification_intents", "group_payment_attempts", "booking_group_magic_links",
                "ticket_price_snapshots", "ticket_bookings", "booking_group_line_sessions", "booking_group_lines",
                "booking_groups", "workshop_cart_quotes", "booking_idempotency_records", "booking_audit_evidence",
                "facade_command_idempotency_records", "ticket_session_entitlements", "attendance_tickets",
                "capacity_holds", "operations_audit_events", "workshop_sessions", "workshop_slots",
                "workshop_packages", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        location = locations.save(WorkshopLocation.builder().code("LOC-RTY").displayName("Retry Hub")
                .addressSnapshot("1 Retry St").active(true).build());
        hourOffset = 0;
        when(checksum.valid(anyMap(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("T145-a: 003.2 down keeps the evidence, then up confirms once; a replay changes nothing")
    void operationsDownThenUp() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        operationsDown();

        ipn(pay, "TX-OPS").andExpect(jsonPath("$.RspCode").value("00"));

        assertWaiting(code, pay, "DEPENDENCY_UNAVAILABLE:OPERATIONS");
        long heldBefore = count("capacity_holds WHERE state = 'ACTIVE'");

        Mockito.reset(operationsAdapter);
        makeDue();
        retryScheduler.retryWaitingConfirmations();

        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(count("attendance_tickets")).isEqualTo(2);
        assertThat(retryRows.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getRetryStatus()).isEqualTo(GroupConfirmationRetry.Status.CONFIRMED));
        assertThat(kindCount(code, "GROUP_CONFIRMATION")).isEqualTo(1);
        assertThat(heldBefore).isPositive();

        ipn(pay, "TX-OPS").andExpect(jsonPath("$.RspCode").value("02"));
        retryScheduler.retryWaitingConfirmations();
        assertThat(count("attendance_tickets")).isEqualTo(2);
        assertThat(kindCount(code, "GROUP_CONFIRMATION")).isEqualTo(1);
        assertThat(count("group_confirmation_retries")).isEqualTo(1);
    }

    @Test
    @DisplayName("T145-b: 006 down keeps the evidence, then up confirms once")
    void loyaltyDownThenUp() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        doThrow(new LoyaltyException(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE, "Loyalty is down"))
                .when(loyaltyOutcome).settleConfirmedGroup(any(), any());

        ipn(pay, "TX-LOY").andExpect(jsonPath("$.RspCode").value("00"));

        assertWaiting(code, pay, "DEPENDENCY_UNAVAILABLE:LOYALTY");

        Mockito.reset(loyaltyOutcome);
        makeDue();
        retryScheduler.retryWaitingConfirmations();

        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(count("attendance_tickets")).isEqualTo(2);
        assertThat(kindCount(code, "GROUP_CONFIRMATION")).isEqualTo(1);
        assertThat(retryRows.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getRetryStatus()).isEqualTo(GroupConfirmationRetry.Status.CONFIRMED));
    }

    @Test
    @DisplayName("T145-c: a failing retry counts and reschedules; nothing is released or alerted while the hold runs")
    void nothingReleasedWhileWaiting() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        operationsDown();
        ipn(pay, "TX-WAIT").andExpect(jsonPath("$.RspCode").value("00"));
        long held = count("capacity_holds WHERE state = 'ACTIVE'");

        makeDue();
        retryScheduler.retryWaitingConfirmations();
        expiry.releaseExpiredGroups();

        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(count("capacity_holds WHERE state = 'ACTIVE'")).isEqualTo(held).isPositive();
        GroupConfirmationRetry row = retryRows.findAll().getFirst();
        assertThat(row.getRetryStatus()).isEqualTo(GroupConfirmationRetry.Status.WAITING);
        assertThat(row.getRetriesMade()).isEqualTo(1);
        assertThat(row.getNextRetryAt()).isAfter(Instant.now());
        assertThat(count("late_payment_reviews")).isZero();
        assertThat(count("booking_group_notification_intents")).isZero();
        assertThat(count("booking_audit_evidence WHERE booking_code = '" + code + "' AND action LIKE '%ALERT%'"))
                .isZero();
    }

    @Test
    @DisplayName("T145-d: still failing at holdExpiresAt: one late review first (real arrival time), then the group is released")
    void handedToLateReviewAtExpiry() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        operationsDown();
        ipn(pay, "TX-EXP").andExpect(jsonPath("$.RspCode").value("00"));
        Instant received = retryRows.findAll().getFirst().getReceivedAt();
        expireHold(code);

        expiry.releaseExpiredGroups();
        expiry.releaseExpiredGroups();

        assertThat(count("late_payment_reviews")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT verified_evidence_reference FROM late_payment_reviews", String.class))
                .isEqualTo(pay.merchant);
        assertThat(reviews.findByVerifiedEvidenceReference(pay.merchant).orElseThrow().getArrivedAt()).isBetween(received.minusSeconds(1), received.plusSeconds(1));
        GroupConfirmationRetry row = retryRows.findAll().getFirst();
        assertThat(row.getRetryStatus()).isEqualTo(GroupConfirmationRetry.Status.HANDED_TO_LATE_REVIEW);
        assertThat(row.getLatePaymentReviewReference()).isEqualTo(
                jdbc.queryForObject("SELECT review_reference FROM late_payment_reviews", String.class));
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(count("capacity_holds WHERE state = 'ACTIVE'")).isZero();
        assertThat(count("attendance_tickets")).isZero();
    }

    @Test
    @DisplayName("T145-e: the receipt and the SUCCESS attempt are never lost when the dependency throws")
    void receiptNeverLost() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        operationsDown();

        ipn(pay, "TX-RCPT").andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(count("payment_ingress_receipts WHERE delivery_reference = '" + pay.merchant + "|TX-RCPT|00|00'"))
                .isEqualTo(1);
        assertThat(attempts.findByMerchantReference(pay.merchant).orElseThrow().getAttemptStatus())
                .isEqualTo(PaymentAttemptStatus.SUCCESS);
        // a replay while waiting adds no second row
        ipn(pay, "TX-RCPT").andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(count("group_confirmation_retries")).isEqualTo(1);
    }

    // ---- helpers ----

    private record Pay(String merchant, long amount) { }

    private void operationsDown() {
        doReturn(null).when(operationsAdapter).commitBookingHolds(any(), any(), any(), any());
        doReturn(null).when(operationsAdapter).reserveGroupCapacity(any(), any(), any(), any());
    }

    private void assertWaiting(String code, Pay pay, String failure) {
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(attempts.findByMerchantReference(pay.merchant).orElseThrow().getAttemptStatus())
                .isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(count("payment_ingress_receipts")).isEqualTo(1);
        assertThat(retryRows.findAll()).singleElement().satisfies(r -> {
            assertThat(r.getRetryStatus()).isEqualTo(GroupConfirmationRetry.Status.WAITING);
            assertThat(r.getLastFailureCode()).isEqualTo(failure);
            assertThat(r.getAmountVnd()).isEqualTo(pay.amount);
        });
        assertThat(count("attendance_tickets")).isZero();
        assertThat(kindCount(code, "GROUP_CONFIRMATION")).isZero();
    }

    private void makeDue() {
        for (GroupConfirmationRetry row : retryRows.findAll()) {
            row.setNextRetryAt(Instant.now().minusSeconds(1));
            retryRows.saveAndFlush(row);
        }
    }

    private void expireHold(String code) {
        BookingGroup group = groups.findByGroupCode(code).orElseThrow();
        group.setHoldExpiresAt(Instant.now().minusSeconds(5));
        groups.saveAndFlush(group);
    }

    private long kindCount(String code, String kind) {
        return count("booking_group_notification_intents i JOIN booking_groups g ON g.id = i.group_id "
                + "WHERE g.group_code = '" + code + "' AND i.notification_kind = '" + kind + "'");
    }

    private org.springframework.test.web.servlet.ResultActions ipn(Pay pay, String transaction) throws Exception {
        return mockMvc.perform(get(IPN).param("vnp_TxnRef", pay.merchant)
                        .param("vnp_Amount", String.valueOf(pay.amount * 100)).param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionStatus", "00").param("vnp_TransactionNo", transaction)
                        .param("vnp_SecureHash", "hash"))
                .andExpect(status().isOk());
    }

    private Pay startPayment(String code) {
        BookingGroup group = groups.findByGroupCode(code).orElseThrow();
        String merchant = "GRP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long amount = group.getGroupCollectionVnd();
        attempts.save(GroupPaymentAttempt.builder().group(group).attemptReference("ATT-" + UUID.randomUUID())
                .merchantReference(merchant).amountVnd(amount).attemptStatus(PaymentAttemptStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(600)).createdAt(Instant.now()).build());
        owners.register(merchant, code, PaymentOwnerKind.DEPOSIT, amount, "VND");
        return new Pay(merchant, amount);
    }

    private String pendingGroup() throws Exception {
        Long pkg = packages.save(WorkshopPackage.builder().name("Retry package " + UUID.randomUUID())
                .description("fixture").publicPrice(150_000L).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();
        WorkshopSession session = session(10);
        MvcResult quote = mockMvc.perform(post("/api/v1/workshop-cart-quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"packageId\":" + pkg + ",\"sessionReferences\":[\"" + session.getReference()
                                + "\"],\"participantCount\":2}]}"))
                .andExpect(status().isCreated()).andReturn();
        String quoteRef = JSON.readTree(quote.getResponse().getContentAsString()).get("quoteRef").asText();
        MvcResult created = mockMvc.perform(post("/api/v1/workshop-booking-groups")
                        .header("Idempotency-Key", "retry-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"retry.buyer@example.com\","
                                + "\"contactPhone\":\"+84900000145\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(created.getResponse().getContentAsString()).get("groupCode").asText();
    }

    private WorkshopSession session(int daysAhead) {
        Instant start = Instant.now().plus(Duration.ofDays(daysAhead)).plus(hourOffset++, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.MINUTES);
        var zone = TimezoneConfiguration.ZONE_ID_VIETNAM;
        LocalTime startTime = start.atZone(zone).toLocalTime();
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1))
                .active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED)
                .build());
    }

    private int count(String fromClause) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
        return n == null ? 0 : n;
    }
}
