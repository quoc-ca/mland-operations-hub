package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.TimezoneConfiguration;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupHoldExpiryScheduler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupMismatchedEvidenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T144 on MySQL through the real IPN route: unsigned, duplicate, amount-mismatched and unknown-reference VNPay
 * notifications for a {@code PENDING_PAYMENT} group never confirm it or settle a ticket; verified mismatched money is
 * recorded (ingress receipt, one audit entry, one WARN) and, at {@code holdExpiresAt}, handed to the late-payment
 * review where the amount condition fails (FR-010f, F14). A second verified right-amount payment on a confirmed group
 * is excess money, not a late review, and the SC-002 query excludes it.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class GroupPaymentIpnMySqlIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String IPN = "/api/v1/integrations/payments/vnpay/events";
    private static final String MISMATCH_WARN = "does not match its payment attempt";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentOwnerRegistry owners;
    @Autowired private BookingGroupRepository groups;
    @Autowired private GroupPaymentAttemptRepository attempts;
    @Autowired private GroupHoldExpiryScheduler expiry;
    @Autowired private GroupMismatchedEvidenceService mismatched;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private VnpayChecksumService checksum;
    @MockBean private VnpayMerchantOperationsPort vnpay;

    private WorkshopLocation location;
    private int hourOffset;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "GroupPaymentIpnMySqlIT requires real MySQL evidence");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("group_excess_payments", "payment_ingress_receipts", "payment_owner_registrations",
                "late_payment_reviews", "booking_group_notification_intents", "group_payment_attempts",
                "booking_group_magic_links", "ticket_price_snapshots", "ticket_bookings",
                "booking_group_line_sessions", "booking_group_lines", "booking_groups", "workshop_cart_quotes",
                "booking_idempotency_records", "booking_audit_evidence", "facade_command_idempotency_records",
                "ticket_session_entitlements", "attendance_tickets", "capacity_holds", "operations_audit_events",
                "workshop_sessions", "workshop_slots", "workshop_packages", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        location = locations.save(WorkshopLocation.builder().code("LOC-IPN").displayName("IPN Hub")
                .addressSnapshot("1 IPN St").active(true).build());
        hourOffset = 0;
        when(checksum.valid(anyMap(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("T144-1: an unsigned notification is refused, stores nothing and leaves the group unconfirmed")
    void unsignedNotification() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        when(checksum.valid(anyMap(), any())).thenReturn(false);

        ipn(pay.merchant, pay.amount, "00", "TX-UNSIGNED").andExpect(jsonPath("$.RspCode").value("97"));

        assertUnconfirmed(code);
        assertThat(count("payment_ingress_receipts")).isZero();
        assertThat(attemptStatus(pay.merchant)).isEqualTo(PaymentAttemptStatus.PENDING);
    }

    @Test
    @DisplayName("T144-2: a reference that matches no attempt changes no group")
    void unknownMerchantReference() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);

        ipn("GRP-NOT-AN-ATTEMPT-" + UUID.randomUUID(), pay.amount, "00", "TX-UNKNOWN")
                .andExpect(jsonPath("$.RspCode").value("01"));

        assertUnconfirmed(code);
        assertThat(count("payment_ingress_receipts")).isZero();
        assertThat(count("booking_audit_evidence WHERE action = '" + GroupMismatchedEvidenceService.ACTION + "'"))
                .isZero();
    }

    @Test
    @DisplayName("T144-3: a verified payment of the wrong amount is recorded once, never confirms, and a replay adds nothing")
    void mismatchedAmountIsRecordedOnce(CapturedOutput output) throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        long wrong = pay.amount - 1_000;

        ipn(pay.merchant, wrong, "00", "TX-WRONG").andExpect(jsonPath("$.RspCode").value("04"));
        ipn(pay.merchant, wrong, "00", "TX-WRONG").andExpect(jsonPath("$.RspCode").value("04"));

        assertUnconfirmed(code);
        assertThat(attemptStatus(pay.merchant)).isEqualTo(PaymentAttemptStatus.PENDING);
        assertThat(jdbc.queryForObject("SELECT amount_vnd FROM payment_ingress_receipts WHERE delivery_reference = ?",
                Long.class, pay.merchant + "|TX-WRONG|00|00")).isEqualTo(wrong);
        assertThat(count("payment_ingress_receipts")).isEqualTo(1);
        assertThat(count("booking_audit_evidence WHERE booking_code = '" + code + "' AND action = '"
                + GroupMismatchedEvidenceService.ACTION + "'")).isEqualTo(1);
        assertThat(occurrences(output.getAll(), MISMATCH_WARN)).isEqualTo(1);
        assertThat(count("late_payment_reviews")).isZero();
    }

    @Test
    @DisplayName("T144-4: at holdExpiresAt the mismatched evidence reaches the late-payment review, amount condition failed")
    void mismatchedEvidenceHandedOverAtExpiry() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        long wrong = pay.amount - 1_000;
        ipn(pay.merchant, wrong, "00", "TX-LATE").andExpect(jsonPath("$.RspCode").value("04"));
        expireHold(code);

        expiry.releaseExpiredGroups();
        expiry.releaseExpiredGroups();

        assertThat(count("late_payment_reviews")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT verified_evidence_reference FROM late_payment_reviews", String.class))
                .isEqualTo(pay.merchant + "|TX-LATE|00|00");
        assertThat(jdbc.queryForObject("SELECT outcome FROM late_payment_reviews", String.class))
                .isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(jdbc.queryForObject("SELECT failed_conditions FROM late_payment_reviews", String.class))
                .contains("AMOUNT");
        assertThat(jdbc.queryForObject("SELECT amount_vnd FROM late_payment_reviews", Long.class)).isEqualTo(wrong);
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(count("attendance_tickets")).isZero();
    }

    @Test
    @DisplayName("T144-5: mismatched evidence that arrives after the hold goes to the late-payment review at once")
    void mismatchedEvidenceAfterExpiry() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);
        expireHold(code);

        ipn(pay.merchant, pay.amount - 1_000, "00", "TX-AFTER").andExpect(jsonPath("$.RspCode").value("04"));

        assertThat(count("late_payment_reviews")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT failed_conditions FROM late_payment_reviews", String.class))
                .contains("AMOUNT");
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    @DisplayName("T144-6: a duplicate right-amount notification confirms once; a second payment is excess, not a late review (SC-002)")
    void duplicateAndExcessPayments() throws Exception {
        String code = pendingGroup();
        Pay pay = startPayment(code);

        ipn(pay.merchant, pay.amount, "00", "TX-OK").andExpect(jsonPath("$.RspCode").value("00"));
        int tickets = count("attendance_tickets");
        ipn(pay.merchant, pay.amount, "00", "TX-OK").andExpect(jsonPath("$.RspCode").value("02"));

        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(count("attendance_tickets")).isEqualTo(tickets).isPositive();

        Pay second = startPayment(code);
        ipn(second.merchant, second.amount, "00", "TX-EXCESS").andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(count("group_excess_payments")).isEqualTo(1);
        assertThat(count("late_payment_reviews")).isZero();
        // SC-002: one confirming SUCCESS attempt per group once the excess payment is excluded
        assertThat(count("group_payment_attempts a WHERE a.attempt_status = 'SUCCESS' AND NOT EXISTS ("
                + "SELECT 1 FROM group_excess_payments x WHERE x.merchant_reference = a.merchant_reference)"))
                .isEqualTo(1);
    }

    // ---- helpers ----

    private record Pay(String merchant, long amount) { }

    private org.springframework.test.web.servlet.ResultActions ipn(String merchant, long amountVnd, String response,
                                                                   String transaction) throws Exception {
        return mockMvc.perform(get(IPN).param("vnp_TxnRef", merchant).param("vnp_Amount", String.valueOf(amountVnd * 100))
                        .param("vnp_ResponseCode", response).param("vnp_TransactionStatus", response)
                        .param("vnp_TransactionNo", transaction).param("vnp_SecureHash", "hash"))
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

    private void expireHold(String code) {
        BookingGroup group = groups.findByGroupCode(code).orElseThrow();
        group.setHoldExpiresAt(Instant.now().minusSeconds(5));
        groups.saveAndFlush(group);
    }

    private PaymentAttemptStatus attemptStatus(String merchant) {
        return attempts.findByMerchantReference(merchant).orElseThrow().getAttemptStatus();
    }

    private void assertUnconfirmed(String code) {
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(count("attendance_tickets")).isZero();
        assertThat(count("booking_group_notification_intents WHERE notification_kind = '"
                + "GROUP_CONFIRMATION'")).isZero();
    }

    private static int occurrences(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    private String pendingGroup() throws Exception {
        Long pkg = packages.save(WorkshopPackage.builder().name("IPN package " + UUID.randomUUID())
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
                        .header("Idempotency-Key", "ipn-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"ipn.buyer@example.com\","
                                + "\"contactPhone\":\"0900000144\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(created.getResponse().getContentAsString()).get("groupCode").asText();
    }

    private WorkshopSession session(int daysAhead) {
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

    private int count(String fromClause) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
        return n == null ? 0 : n;
    }
}
