package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentReviewQueued;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupPaymentEvidenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T094 / FR-040, FR-040a (decision 2026-11-13) on MySQL with Flyway: automatic re-confirmation all or none,
 * idempotent across IPN and QueryDR, capacity race with one winner, Manager queue and alert, CONFIRM limited to
 * conditions 4/5 with 1–3 rechecked, REFUND opening a durable 003.3 bank-refund request without VNPay Refund.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.booking.auth.allow-test-tokens=true"})
class LatePaymentReviewIT {

    private static final String BUYER_EMAIL = "late.buyer@example.com";
    private static final long AMOUNT = 150_000L;
    private static final long MANAGER_ID = 701L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;
    @Autowired private ApplicationEvents events;
    @Autowired private GroupPaymentEvidenceService evidence;
    @Autowired private edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupConfirmationService confirmation;
    @Autowired private BookingGroupRepository groups;
    @Autowired private BookingGroupLineRepository lines;
    @Autowired private TicketBookingRepository tickets;
    @Autowired private TicketPriceSnapshotRepository priceSnapshots;
    @Autowired private GroupPaymentAttemptRepository attempts;
    @Autowired private LatePaymentReviewRepository reviews;
    @Autowired private BookingGroupNotificationIntentRepository intents;
    @Autowired private GroupNotificationService notifications;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;
    @Autowired private WorkshopOperationsFacade operations;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;
    @MockBean private VnpayMerchantOperationsPort vnpay;
    @MockBean private edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.ManagerAlertRecipientSource managerRecipients;

    private WorkshopLocation location;
    private Long packageId;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "LatePaymentReviewIT requires real MySQL evidence");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("settlement_notification_handoffs", "settlement_business_audits", "notification_deliveries",
                "settlement_refund_account_links", "settlement_refund_requests",
                "late_payment_reviews", "booking_group_notification_intents", "group_payment_attempts",
                "booking_group_magic_links", "ticket_price_snapshots", "ticket_bookings", "booking_group_line_sessions",
                "booking_group_lines", "booking_groups", "booking_idempotency_records", "booking_audit_evidence",
                "facade_command_idempotency_records", "ticket_session_entitlements", "attendance_tickets",
                "capacity_holds", "operations_audit_events", "workshop_sessions", "workshop_slots",
                "workshop_packages", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        // A throw-away test key: the account number is stored encrypted (WORKSHOP_REFUND_ACCOUNT_KEY in real environments).
        var useKey = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountCipher.class
                .getDeclaredMethod("useKeyForTests", String.class);
        useKey.setAccessible(true);
        useKey.invoke(null, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        location = locations.save(WorkshopLocation.builder().code("LOC-LATE").displayName("Late Hub")
                .addressSnapshot("1 Late St").active(true).build());
        packageId = packages.save(WorkshopPackage.builder().name("Late package").description("fixture")
                .publicPrice(AMOUNT).minParticipants(1).maxParticipants(10).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0).state(WorkshopPackageState.PUBLISHED).build()).getId();

        when(accountFacade.resolveAccount("manager")).thenReturn(Optional.of(new AccountSummary(MANAGER_ID,
                ActorRole.MANAGER, ActorStatus.ACTIVE, "manager@mland.test", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(MANAGER_ID)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(tokenVerificationService.verify("test-uid:manager")).thenReturn(
                new VerifiedIdentity("manager", "manager@mland.test", true, Instant.now()));
        when(accountFacade.resolveActor("manager", true)).thenReturn(Optional.of(
                new CurrentAccountActor(MANAGER_ID, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(location.getId()))));
        when(accountFacade.resolveAccount("staff")).thenReturn(Optional.of(new AccountSummary(702L,
                ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.test", Instant.now(), Instant.now())));
        when(tokenVerificationService.verify("test-uid:staff")).thenReturn(
                new VerifiedIdentity("staff", "staff@mland.test", true, Instant.now()));
        when(accountFacade.resolveActor("staff", true)).thenReturn(Optional.of(
                new CurrentAccountActor(702L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(location.getId()))));
    }

    @Test
    @DisplayName("T094-1: all five conditions hold -> re-confirmed automatically, once, across IPN and QueryDR")
    void allConditionsHold_autoReconfirms_idempotently() {
        WorkshopSession session = session(Duration.ofDays(3), 10);
        Fixture group = pendingGroup(session, 2, Duration.ofMinutes(10), BUYER_EMAIL);

        apply(group);
        apply(group);

        LatePaymentReview review = onlyReview();
        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.AUTO_RECONFIRMED);
        assertThat(groups.findByGroupCode(group.code()).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.id()))
                .allSatisfy(t -> {
                    assertThat(t.getTicketStatus()).isEqualTo(BookingStatus.CONFIRMED);
                    assertThat(t.getQrReference()).isNotBlank();
                });
        assertThat(count("attendance_tickets")).isEqualTo(2);
        assertThat(count("booking_audit_evidence WHERE action = 'LATE_PAYMENT_RECONFIRMED'")).isEqualTo(1);
        assertThat(events.stream(LatePaymentReviewQueued.class)).isEmpty();
        List<BookingGroupNotificationIntent> sent = intents.findByGroupIdOrderByCreatedAtAsc(group.id());
        assertThat(sent).extracting(BookingGroupNotificationIntent::getNotificationKind)
                .containsExactly(GroupNotificationService.KIND_GROUP_CONFIRMATION);
        var confirmationEmail = notifications.readPayload(sent.getFirst().getNotificationReference());
        assertThat(confirmationEmail.storedRecipient()).isEqualTo(BUYER_EMAIL);
        assertThat(confirmationEmail.renderedBookingContent()).contains("Thanh toán của bạn đến sau thời hạn");
        assertThat(confirmationEmail.renderedTicketArtifacts()).hasSize(2).allSatisfy(t -> assertThat(t).contains("QR: QR-"));
    }

    @Test
    @DisplayName("T094-2: arrival after 60 minutes -> PENDING_MANAGER, payer email, reference-only alert, 24h deadline")
    void lateBeyondWindow_queuesForManager_withAlertAndDeadline() throws Exception {
        WorkshopSession session = session(Duration.ofHours(10), 10);
        Fixture group = pendingGroup(session, 1, Duration.ofMinutes(90), BUYER_EMAIL);
        when(managerRecipients.activeManagerEmails()).thenReturn(List.of("m1@mland.test", " M1@mland.test", "m2@mland.test"));

        apply(group);

        LatePaymentReview review = onlyReview();
        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).isEqualTo("ARRIVAL_WINDOW");
        assertThat(review.getResultDueAt()).isEqualTo(session.getScheduledStartAt());
        assertThat(groups.findByGroupCode(group.code()).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(count("capacity_holds WHERE state = 'ACTIVE'")).isZero();
        assertThat(intents.findByGroupIdOrderByCreatedAtAsc(group.id())).extracting(BookingGroupNotificationIntent::getNotificationKind)
                .containsExactlyInAnyOrder(GroupNotificationService.KIND_LATE_PAYMENT_PENDING,
                        GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT, GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT);
        List<String> alertRecipients = new ArrayList<>();
        for (BookingGroupNotificationIntent intent : intents.findByGroupIdOrderByCreatedAtAsc(group.id())) {
            if (!GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT.equals(intent.getNotificationKind())) continue;
            var payload = notifications.readPayload(intent.getNotificationReference());
            alertRecipients.add(payload.storedRecipient());
            assertThat(payload.renderedSubject()).contains(group.code()).contains("Cần xử lý trước");
            assertThat(payload.renderedBookingContent()).contains("Tiền về sau hơn 60 phút")
                    .contains("Có thể xác nhận chỗ").doesNotContain(BUYER_EMAIL).doesNotContain("0900000000")
                    .doesNotContain(group.merchant());
        }
        assertThat(alertRecipients).containsExactlyInAnyOrder("m1@mland.test", "m2@mland.test");
        List<LatePaymentReviewQueued> alerts = events.stream(LatePaymentReviewQueued.class).toList();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().toString()).doesNotContain(BUYER_EMAIL).doesNotContain(group.merchant());

        mockMvc.perform(get("/api/v1/late-payment-reviews").header("Authorization", "Bearer test-uid:manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewId").value(review.getReviewReference()))
                .andExpect(jsonPath("$[0].confirmAllowed").value(true))
                .andExpect(jsonPath("$[0].failedConditions[0]").value("ARRIVAL_WINDOW"));
        mockMvc.perform(get("/api/v1/late-payment-reviews").header("Authorization", "Bearer test-uid:staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T094-3: one line without capacity -> nothing confirmed, no hold left (never partial)")
    void oneLineWithoutCapacity_confirmsNothing() {
        WorkshopSession roomy = session(Duration.ofDays(3), 10);
        WorkshopSession tight = session(Duration.ofDays(3), 1);
        Fixture group = pendingGroup(List.of(roomy, tight), 1, Duration.ofMinutes(10), BUYER_EMAIL);
        expire(group);
        occupy(tight, 1);

        apply(group);

        LatePaymentReview review = onlyReview();
        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).contains("CAPACITY");
        assertThat(groups.findByGroupCode(group.code()).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.id()))
                .noneMatch(t -> t.getTicketStatus() == BookingStatus.CONFIRMED);
        // No Manager connected: no alert email, the review still waits in the queue.
        assertThat(intents.findByGroupIdOrderByCreatedAtAsc(group.id())).extracting(BookingGroupNotificationIntent::getNotificationKind)
                .containsExactly(GroupNotificationService.KIND_LATE_PAYMENT_PENDING);
    }

    @Test
    @DisplayName("T094-4: two late groups race for the last seat -> exactly one winner")
    void capacityRace_hasExactlyOneWinner() throws Exception {
        WorkshopSession session = session(Duration.ofDays(3), 2);
        Fixture first = pendingGroup(session, 1, Duration.ofMinutes(5), "first@example.com");
        Fixture second = pendingGroup(session, 1, Duration.ofMinutes(5), "second@example.com");
        expire(first);
        expire(second);
        occupy(session, 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> runs = new ArrayList<>();
        for (Fixture group : List.of(first, second)) {
            runs.add(pool.submit(() -> {
                start.await();
                apply(group);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> run : runs) run.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        List<String> outcomes = reviews.findAll().stream().map(LatePaymentReview::getOutcome).sorted().toList();
        assertThat(outcomes).containsExactly(LatePaymentReview.AUTO_RECONFIRMED, LatePaymentReview.PENDING_MANAGER);
        assertThat(count("booking_groups WHERE group_status = 'CONFIRMED'")).isEqualTo(1);
    }

    @Test
    @DisplayName("T094-5: Manager CONFIRM when only the window failed and 1-3 still hold")
    void managerConfirm_whenOnlyWindowFailed() throws Exception {
        WorkshopSession session = session(Duration.ofDays(3), 10);
        Fixture group = pendingGroup(session, 1, Duration.ofMinutes(90), BUYER_EMAIL);
        apply(group);
        LatePaymentReview review = onlyReview();

        decide(group, review, "CONFIRM", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("MANAGER_CONFIRMED"));

        assertThat(groups.findByGroupCode(group.code()).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reviews.findByReviewReference(review.getReviewReference()).orElseThrow().getDecidedByAccountId())
                .isEqualTo(MANAGER_ID);
        decide(group, review, "REFUND", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LATE_PAYMENT_REVIEW_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("T094-6: Manager CONFIRM is refused when capacity is gone now or condition 2 failed")
    void managerConfirm_refusedWhenConditionsOneToThreeFail() throws Exception {
        WorkshopSession session = session(Duration.ofDays(3), 1);
        Fixture group = pendingGroup(session, 1, Duration.ofMinutes(90), BUYER_EMAIL);
        apply(group);
        LatePaymentReview review = onlyReview();
        occupy(session, 1);

        decide(group, review, "CONFIRM", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LATE_PAYMENT_CONFIRM_NOT_ALLOWED"));
        assertThat(reviews.findByReviewReference(review.getReviewReference()).orElseThrow().getOutcome())
                .isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(groups.findByGroupCode(group.code()).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);

        WorkshopSession soon = session(Duration.ofMinutes(90), 10);
        Fixture early = pendingGroup(soon, 1, Duration.ofMinutes(5), "early@example.com");
        apply(early);
        LatePaymentReview earlyReview = reviews.findAll().stream()
                .filter(r -> r.getVerifiedEvidenceReference().equals(early.merchant())).findFirst().orElseThrow();
        assertThat(earlyReview.getFailedConditions()).isEqualTo("START_TIME");
        decide(early, earlyReview, "CONFIRM", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LATE_PAYMENT_CONFIRM_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("T094-7: Manager REFUND opens one durable bank-refund request and never calls VNPay Refund")
    void managerRefund_opensDurableBankRefund_once() throws Exception {
        WorkshopSession session = session(Duration.ofHours(1), 10);
        Fixture group = pendingGroup(session, 1, Duration.ofMinutes(5), BUYER_EMAIL);
        apply(group);
        LatePaymentReview review = onlyReview();
        String key = key();

        decide(group, review, "REFUND", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REFUND_REQUESTED"))
                .andExpect(jsonPath("$.refundRequestReference").isNotEmpty());
        decide(group, review, "REFUND", key).andExpect(status().isOk());

        List<Map<String, Object>> requests = jdbc.queryForList(
                "SELECT reason, source_reference, amount_vnd, status FROM settlement_refund_requests");
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().get("reason")).isEqualTo("LATE_PAYMENT_NOT_RECONFIRMED");
        assertThat(requests.getFirst().get("source_reference")).isEqualTo(review.getReviewReference());
        assertThat(((Number) requests.getFirst().get("amount_vnd")).longValue()).isEqualTo(AMOUNT);
        assertThat(requests.getFirst().get("status")).isEqualTo("PENDING_ACCOUNT");
        assertThat(reviews.findByReviewReference(review.getReviewReference()).orElseThrow().getOutcome())
                .isEqualTo(LatePaymentReview.REFUND_REQUESTED);
        verifyNoInteractions(vnpay);

        // Email 7 to the payer with the private 7-day link, then the customer answers it once.
        List<Map<String, Object>> emails = jdbc.queryForList("SELECT notification_kind, stored_recipient, rendered_subject, "
                + "rendered_settlement_content, payment_action_link FROM settlement_notification_handoffs");
        assertThat(emails).hasSize(1);
        Map<String, Object> email = emails.getFirst();
        assertThat(email.get("notification_kind")).isEqualTo("REFUND_ACCOUNT_REQUEST");
        assertThat(email.get("stored_recipient")).isEqualTo(BUYER_EMAIL);
        assertThat((String) email.get("rendered_subject")).contains("150.000 VND").contains(group.code());
        String link = (String) email.get("payment_action_link");
        assertThat(link).startsWith("/refund-account#");
        String token = link.substring("/refund-account#".length());
        assertThat((String) email.get("rendered_settlement_content")).doesNotContain(token);
        assertThat(String.join("|", jdbc.queryForList("SELECT CONCAT_WS('|', action_code, IFNULL(actor_reference,''), IFNULL(evidence_reference,''), IFNULL(reason_reference,'')) FROM settlement_business_audits", String.class)))
                .doesNotContain(token);

        String account = "{\"bankCode\":\"VCB\",\"accountNumber\":\"0123456789\",\"holderName\":\"Nguyen Van A\"}";
        mockMvc.perform(post("/api/v1/refund-account-links/{t}", "unknown-token").contentType(MediaType.APPLICATION_JSON).content(account))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/refund-account-links/{t}", token).contentType(MediaType.APPLICATION_JSON).content(account))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/refund-account-links/{t}", token).contentType(MediaType.APPLICATION_JSON).content(account))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("REFUND_ACCOUNT_LINK_NOT_USABLE"));
        Map<String, Object> request = jdbc.queryForMap("SELECT status, recipient_account_masked FROM settlement_refund_requests");
        assertThat(request.get("status")).isEqualTo("PENDING_APPROVAL");
        assertThat((String) request.get("recipient_account_masked")).doesNotContain("0123456789");
    }

    @Test
    @DisplayName("T094-8: the same buyer already holding these sessions -> DUPLICATE_BOOKING, Manager may confirm")
    void duplicateBooking_isCondition5() {
        WorkshopSession session = session(Duration.ofDays(3), 10);
        Fixture other = pendingGroup(session, 1, Duration.ofMinutes(-5), "Late.Buyer@example.com");
        Fixture group = pendingGroup(session, 1, Duration.ofMinutes(10), BUYER_EMAIL);
        assertThat(other.code()).isNotEqualTo(group.code());

        apply(group);

        LatePaymentReview review = onlyReview();
        assertThat(review.getFailedConditions()).isEqualTo("DUPLICATE_BOOKING");
    }

    @Test
    @DisplayName("T094-9: package price changed -> condition 3 AMOUNT fails, queued for Manager")
    void packagePriceChanged_failsCondition3Amount() {
        WorkshopSession session = session(Duration.ofDays(3), 10);
        Fixture group = pendingGroup(session, 1, Duration.ofMinutes(10), BUYER_EMAIL);

        // Change package price in Operations
        WorkshopPackage pkg = packages.findById(packageId).orElseThrow();
        pkg.setPublicPrice(AMOUNT + 50_000L);
        packages.save(pkg);

        apply(group);

        LatePaymentReview review = onlyReview();
        assertThat(review.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
        assertThat(review.getFailedConditions()).contains("AMOUNT");
    }

    // ------------------------------------------------------------------ helpers

    record Fixture(Long id, String code, String merchant) { }

    private void expire(Fixture group) {
        tx.executeWithoutResult(status -> confirmation.releaseExpiredGroup(group.code(), "op-exp-" + group.code(), "corr"));
    }

    private void apply(Fixture group) {
        tx.executeWithoutResult(status -> evidence.apply(group.code(), group.merchant(), AMOUNT, "VND",
                "VNPAY-" + group.merchant(), "op-" + UUID.randomUUID(), "corr-late"));
    }

    private ResultActions decide(Fixture group, LatePaymentReview review, String decision, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/workshop-booking-groups/{g}/late-payment-reviews/{r}/decisions",
                        group.code(), review.getReviewReference())
                .header("Authorization", "Bearer test-uid:manager")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\",\"reason\":\"Buyer called the shop\"}"));
    }

    private LatePaymentReview onlyReview() {
        List<LatePaymentReview> all = reviews.findAll();
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    private static String key() {
        return "idem-" + UUID.randomUUID();
    }

    private int count(String fromClause) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
        return n == null ? 0 : n;
    }

    private WorkshopSession session(Duration startsIn, int capacity) {
        Instant start = Instant.now().plus(startsIn).truncatedTo(ChronoUnit.MINUTES);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalTime startTime = start.atZone(zone).toLocalTime();
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(capacity)
                .active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(capacity).state(SessionState.PUBLISHED)
                .build());
    }

    /** Takes seats with a committed single booking so the late group finds less capacity. */
    private void occupy(WorkshopSession session, int seats) {
        String ref = "OCC-" + UUID.randomUUID().toString().substring(0, 8);
        CapacityHoldOutcome hold = operations.reserveBookingCapacity(new ReserveBookingCapacityCommand(ref,
                String.valueOf(packageId), List.of(UUID.fromString(session.getReference())), seats,
                "op-o-" + UUID.randomUUID(), "corr"));
        assertThat(hold.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(operations.commitBookingHolds(new CommitBookingHoldsCommand(ref, hold.holdReferences(),
                "op-oc-" + UUID.randomUUID(), "corr")).status()).isEqualTo(OutcomeStatus.ACCEPTED);
    }

    private Fixture pendingGroup(WorkshopSession session, int ticketCount, Duration paidAfterExpiry, String email) {
        return pendingGroup(List.of(session), ticketCount, paidAfterExpiry, email);
    }

    /**
     * A VNPay group whose hold ran out {@code paidAfterExpiry} ago, one line per session, with real Operations
     * holds. A negative duration leaves the hold running (an active group of the same buyer).
     */
    private Fixture pendingGroup(List<WorkshopSession> lineSessions, int ticketCount, Duration paidAfterExpiry,
                                 String email) {
        long collection = AMOUNT;
        long perTicket = collection / ((long) ticketCount * lineSessions.size());
        Long linePackageId = packageId;
        if (perTicket != AMOUNT) {
            linePackageId = packages.save(WorkshopPackage.builder().name("Late package " + perTicket)
                    .description("fixture").publicPrice(perTicket).minParticipants(1).maxParticipants(10)
                    .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                    .state(WorkshopPackageState.PUBLISHED).build()).getId();
        }
        String code = "GRP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        List<ReserveGroupCapacityCommand.Line> commandLines = new ArrayList<>();
        for (int i = 0; i < lineSessions.size(); i++) {
            commandLines.add(new ReserveGroupCapacityCommand.Line(code + "-L" + i, String.valueOf(linePackageId),
                    List.of(UUID.fromString(lineSessions.get(i).getReference())), ticketCount));
        }
        GroupCapacityOutcome held = operations.reserveGroupCapacity(new ReserveGroupCapacityCommand(code, commandLines,
                "CREATE-" + code, "corr"));
        assertThat(held.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        BookingGroup group = groups.save(BookingGroup.builder().groupCode(code).contactEmail(email)
                .contactPhone("0900000000").groupStatus(BookingStatus.PENDING_PAYMENT).paymentMode(GroupPaymentMode.VNPAY)
                .groupCollectionVnd(collection).holdExpiresAt(Instant.now().minus(paidAfterExpiry))
                .paymentDeadlineAt(Instant.now().minus(paidAfterExpiry).minusSeconds(120)).build());
        long allocated = 0;
        int ticketIndex = 0;
        int totalTickets = ticketCount * lineSessions.size();
        for (int i = 0; i < lineSessions.size(); i++) {
            WorkshopSession session = lineSessions.get(i);
            BookingGroupLine line = BookingGroupLine.builder().group(group).lineIndex(i).lineReference(code + "-L" + i)
                    .packageId(linePackageId).packageNameSnapshot("Late package").pricePerParticipantVnd(perTicket)
                    .ticketCount(ticketCount).build();
            line.getSessions().add(BookingGroupLineSession.builder().line(line).ordinal(1)
                    .sessionReference(session.getReference()).locationReference(location.getId())
                    .scheduledStart(session.getScheduledStartAt())
                    .holdReference(held.lineHoldReferences().get(i).getFirst().toString())
                    .holdStatus(LineHoldStatus.RESERVED).build());
            lines.save(line);
            for (int p = 1; p <= ticketCount; p++) {
                ticketIndex++;
                long price = ticketIndex == totalTickets ? collection - allocated : perTicket;
                allocated += price;
                TicketBooking ticket = tickets.save(TicketBooking.builder().ticketCode(code + "-T" + ticketIndex)
                        .group(group).lineIndex(i).participantOrdinal(p).packageId(linePackageId)
                        .packageNameSnapshot("Late package").build());
                priceSnapshots.save(TicketPriceSnapshot.builder().ticket(ticket).packagePriceVnd(price)
                        .voucherDiscountVnd(0).pointsUsed(0).pointDiscountVnd(0).finalPriceVnd(price)
                        .collectedAtBookingVnd(price).collectionKind(TicketCollectionKind.FULL).remainingVnd(0)
                        .nonRefundableDisclosed(true).build());
            }
        }
        String merchant = "M-" + code;
        attempts.save(GroupPaymentAttempt.builder().group(group).attemptReference(UUID.randomUUID().toString())
                .merchantReference(merchant).amountVnd(collection).attemptStatus(PaymentAttemptStatus.PENDING)
                .expiresAt(group.getHoldExpiresAt()).build());
        return new Fixture(group.getId(), code, merchant);
    }
}
