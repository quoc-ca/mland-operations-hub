package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.TimezoneConfiguration;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 Phase 19 (T171) on MySQL through the real IPN, payment and release routes: a customer who cancels at VNPay
 * leaves a {@code FAILED} attempt only for a verified definite-failure IPN (FR-032); the buyer can then release the hold
 * (FR-048, 204) or pay again with a new merchant reference and the same deadline; a late verified success follows the
 * group's state (confirm, FR-040 or FR-013a). Release giving back voucher/points (T171j) is covered by
 * {@code GroupBuyerReleaseTest} (the same release path, no MySQL state involved).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
class GroupPaymentFailureMySqlIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String IPN = "/api/v1/integrations/payments/vnpay/events";
    private static final String EMAIL = "ipn.buyer@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentOwnerRegistry owners;
    @Autowired private BookingGroupRepository groups;
    @Autowired private GroupPaymentAttemptRepository attempts;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private VnpayChecksumService checksum;
    @MockBean private VnpayMerchantOperationsPort vnpay;
    @MockBean private PaymentGatewayCheckoutPort gateway;

    private WorkshopLocation location;
    private int hourOffset;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "GroupPaymentFailureMySqlIT requires real MySQL evidence");
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
        location = locations.save(WorkshopLocation.builder().code("LOC-FAIL").displayName("Fail Hub")
                .addressSnapshot("1 Fail St").active(true).build());
        hourOffset = 0;
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(gateway.createDepositUrl(anyString(), anyString(), anyLong(), any(), any(), any()))
                .thenAnswer(invocation -> "https://pay.test/" + invocation.getArgument(0));
    }

    @ParameterizedTest(name = "T171a: a verified IPN with code {0} sets the attempt FAILED")
    @ValueSource(strings = {"09", "10", "11", "12", "13", "24", "51", "65", "79"})
    void listedCodesFailTheAttempt(String code) throws Exception {
        String group = pendingGroup();
        Pay pay = startPayment(group);

        ipn(pay.merchant, pay.amount, code, "01", "TX-" + code).andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(attemptStatus(pay.merchant)).isEqualTo(PaymentAttemptStatus.FAILED);
        assertGroupUntouched(group);
    }

    @Test
    @DisplayName("T171b: vnp_TransactionStatus=02 fails the attempt; a repeated IPN is acknowledged")
    void transactionStatus02() throws Exception {
        String group = pendingGroup();
        Pay pay = startPayment(group);

        ipn(pay.merchant, pay.amount, "99", "02", "TX-02").andExpect(jsonPath("$.RspCode").value("00"));
        ipn(pay.merchant, pay.amount, "99", "02", "TX-02").andExpect(jsonPath("$.RspCode").value("02"));

        assertThat(attemptStatus(pay.merchant)).isEqualTo(PaymentAttemptStatus.FAILED);
        assertGroupUntouched(group);
    }

    @Test
    @DisplayName("T171c: a wrong signature or a wrong amount leaves the attempt PENDING")
    void badSignatureAndWrongAmountChangeNothing() throws Exception {
        String group = pendingGroup();
        Pay pay = startPayment(group);

        when(checksum.valid(anyMap(), any())).thenReturn(false);
        ipn(pay.merchant, pay.amount, "24", "02", "TX-SIG").andExpect(jsonPath("$.RspCode").value("97"));
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        ipn(pay.merchant, pay.amount - 1_000, "24", "02", "TX-AMT").andExpect(jsonPath("$.RspCode").value("04"));

        assertThat(attemptStatus(pay.merchant)).isEqualTo(PaymentAttemptStatus.PENDING);
    }

    @ParameterizedTest(name = "T171d: code {0} leaves the attempt PENDING")
    @ValueSource(strings = {"01", "07", "75", "99", "42"})
    void nonFailureCodesChangeNothing(String code) throws Exception {
        String group = pendingGroup();
        Pay pay = startPayment(group);

        ipn(pay.merchant, pay.amount, code, "01", "TX-" + code).andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(attemptStatus(pay.merchant)).isEqualTo(PaymentAttemptStatus.PENDING);
    }

    @Test
    @DisplayName("T171e: release is 409 while an attempt is PENDING and 204 once it is FAILED")
    void releaseDependsOnALiveAttempt() throws Exception {
        String group = pendingGroup();
        Pay pay = startPayment(group);

        release(group).andExpect(status().isConflict());
        ipn(pay.merchant, pay.amount, "24", "02", "TX-CANCEL").andExpect(jsonPath("$.RspCode").value("00"));
        release(group).andExpect(status().isNoContent());

        assertThat(groups.findByGroupCode(group).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("T171f: paying again after FAILED creates a new merchant reference and never extends the hold")
    void payAgainAfterFailed() throws Exception {
        String group = pendingGroup();
        Instant holdBefore = groups.findByGroupCode(group).orElseThrow().getHoldExpiresAt();
        String first = initiate(group);
        long amount = groups.findByGroupCode(group).orElseThrow().getGroupCollectionVnd();
        ipn(first, amount, "24", "02", "TX-F").andExpect(jsonPath("$.RspCode").value("00"));

        String second = initiate(group);

        assertThat(second).isNotEqualTo(first);
        assertThat(attemptStatus(first)).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(attemptStatus(second)).isEqualTo(PaymentAttemptStatus.PENDING);
        assertThat(groups.findByGroupCode(group).orElseThrow().getHoldExpiresAt()).isEqualTo(holdBefore);
        assertThat(initiate(group)).isEqualTo(second);
    }

    @Test
    @DisplayName("T171h: two concurrent payment requests after FAILED create one live attempt")
    void concurrentRequestsCreateOneLiveAttempt() throws Exception {
        String group = pendingGroup();
        String first = initiate(group);
        ipn(first, groups.findByGroupCode(group).orElseThrow().getGroupCollectionVnd(), "24", "02", "TX-C")
                .andExpect(jsonPath("$.RspCode").value("00"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> call = () -> initiate(group);
            Future<String> a = pool.submit(call);
            Future<String> b = pool.submit(call);
            assertThat(a.get()).isEqualTo(b.get());
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("group_payment_attempts WHERE attempt_status IN ('INITIATED','PENDING')")).isEqualTo(1);
        assertThat(count("group_payment_attempts")).isEqualTo(2);
    }

    @Test
    @DisplayName("T171i: a verified success on a FAILED attempt confirms a PENDING_PAYMENT group")
    void successAfterFailedConfirmsAPendingGroup() throws Exception {
        String group = pendingGroup();
        Pay pay = startPayment(group);
        ipn(pay.merchant, pay.amount, "24", "02", "TX-F1").andExpect(jsonPath("$.RspCode").value("00"));

        ipn(pay.merchant, pay.amount, "00", "00", "TX-OK").andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(groups.findByGroupCode(group).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(attemptStatus(pay.merchant)).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(count("late_payment_reviews")).isZero();
    }

    @Test
    @DisplayName("T171g/i: a verified success on a FAILED attempt of a released group enters the FR-040 review")
    void successAfterFailedOnAReleasedGroupIsLateEvidence() throws Exception {
        String group = pendingGroup();
        Pay pay = startPayment(group);
        ipn(pay.merchant, pay.amount, "24", "02", "TX-F2").andExpect(jsonPath("$.RspCode").value("00"));
        release(group).andExpect(status().isNoContent());

        ipn(pay.merchant, pay.amount, "00", "00", "TX-LATE").andExpect(jsonPath("$.RspCode").value("00"));

        // FR-040: the money is evaluated as late evidence (here it passes every condition and re-confirms).
        assertThat(count("late_payment_reviews")).isEqualTo(1);
    }

    @Test
    @DisplayName("T171g/i: a verified success on a FAILED attempt of a CONFIRMED group is excess money (FR-013a)")
    void successAfterFailedOnAConfirmedGroupIsExcess() throws Exception {
        String group = pendingGroup();
        Pay failed = startPayment(group);
        ipn(failed.merchant, failed.amount, "24", "02", "TX-F3").andExpect(jsonPath("$.RspCode").value("00"));
        Pay paid = startPayment(group);
        ipn(paid.merchant, paid.amount, "00", "00", "TX-PAID").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(groups.findByGroupCode(group).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);

        ipn(failed.merchant, failed.amount, "00", "00", "TX-EXTRA").andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(count("group_excess_payments")).isEqualTo(1);
        assertThat(count("late_payment_reviews")).isZero();
    }

    // ---- helpers ----

    private record Pay(String merchant, long amount) { }

    private org.springframework.test.web.servlet.ResultActions ipn(String merchant, long amountVnd, String response,
                                                                   String transactionStatus, String transaction)
            throws Exception {
        return mockMvc.perform(get(IPN).param("vnp_TxnRef", merchant).param("vnp_Amount", String.valueOf(amountVnd * 100))
                        .param("vnp_ResponseCode", response).param("vnp_TransactionStatus", transactionStatus)
                        .param("vnp_TransactionNo", transaction).param("vnp_SecureHash", "hash"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions release(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/workshop-booking-groups/" + code + "/pending-hold-release")
                .header("Idempotency-Key", "rel-key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactEmail\":\"" + EMAIL + "\",\"reason\":\"Customer cancelled at VNPay\"}"));
    }

    private String initiate(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workshop-booking-groups/" + code + "/payment-attempts")
                        .header("Idempotency-Key", "pay-key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("merchantReference").asText();
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

    private PaymentAttemptStatus attemptStatus(String merchant) {
        return attempts.findByMerchantReference(merchant).orElseThrow().getAttemptStatus();
    }

    private void assertGroupUntouched(String code) {
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(count("attendance_tickets")).isZero();
    }

    private String pendingGroup() throws Exception {
        Long pkg = packages.save(WorkshopPackage.builder().name("Fail package " + UUID.randomUUID())
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
                        .header("Idempotency-Key", "fail-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"" + EMAIL + "\","
                                + "\"contactPhone\":\"+84900000144\"}"))
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
