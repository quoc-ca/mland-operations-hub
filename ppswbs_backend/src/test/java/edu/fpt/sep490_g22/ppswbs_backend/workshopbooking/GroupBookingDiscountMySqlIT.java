package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T097 on MySQL, the automatic-discount cases of T105 with a real 006 programme in the schema: a Guest cart
 * through confirmation, a Member voucher replacing the automatic discount, a cart below the discount minimum,
 * largest-remainder allocation per ticket, and late-payment condition 3 (recomputed for an automatic discount,
 * still failing for a voucher).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.booking.auth.allow-test-tokens=true"})
class GroupBookingDiscountMySqlIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EMAIL = "discount.buyer@example.com";
    private static final long MEMBER_ID = 8_100L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;
    @Autowired private GroupConfirmationService confirmation;
    @Autowired private GroupPaymentEvidenceService evidence;
    @Autowired private BookingGroupRepository groups;
    @Autowired private TicketBookingRepository tickets;
    @Autowired private TicketPriceSnapshotRepository snapshots;
    @Autowired private GroupPaymentAttemptRepository attempts;
    @Autowired private LatePaymentReviewRepository reviews;
    @Autowired private WorkshopLocationRepository locations;
    @Autowired private WorkshopSlotRepository slots;
    @Autowired private WorkshopPackageRepository packages;
    @Autowired private WorkshopSessionRepository sessions;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;

    @MockBean private AccountFacade accountFacade;
    @MockBean private TokenVerificationService tokenVerificationService;

    private WorkshopLocation location;
    private int hourOffset;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "GroupBookingDiscountMySqlIT requires real MySQL evidence");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of("late_payment_reviews", "booking_group_notification_intents",
                "group_payment_attempts", "booking_group_magic_links", "ticket_price_snapshots", "ticket_bookings",
                "booking_group_line_sessions", "booking_group_lines", "booking_groups", "workshop_cart_quotes",
                "booking_idempotency_records", "booking_audit_evidence", "facade_command_idempotency_records",
                "ticket_session_entitlements", "attendance_tickets", "capacity_holds", "operations_audit_events",
                "workshop_sessions", "workshop_slots", "workshop_locations")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        // DELETE, not TRUNCATE: package ids keep growing, so a programme left by an earlier run never targets a new package.
        jdbc.execute("DELETE FROM workshop_packages");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        location = locations.save(WorkshopLocation.builder().code("LOC-DISC").displayName("Discount Hub")
                .addressSnapshot("1 Disc St").active(true).build());
        hourOffset = 0;
        when(accountFacade.resolveAccount("member")).thenReturn(Optional.of(new AccountSummary(MEMBER_ID,
                ActorRole.MEMBER, ActorStatus.ACTIVE, "member@example.com", Instant.now(), Instant.now())));
        when(accountFacade.getAccountStatus(MEMBER_ID)).thenReturn(Optional.of(ActorStatus.ACTIVE));
        when(tokenVerificationService.verify("test-uid:member")).thenReturn(
                new VerifiedIdentity("member", "member@example.com", true, Instant.now()));
        when(accountFacade.resolveActor("member", true)).thenReturn(Optional.of(new CurrentAccountActor(
                MEMBER_ID, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
    }

    @Test
    @DisplayName("T097-d1: a Guest cart gets the automatic discount, pays the discounted total and confirms")
    void guestCartWithAutomaticDiscountThroughConfirmation() throws Exception {
        Long pkg = packageOf(200_000);
        autoDiscount(List.of(pkg), 50_000, 0);
        JsonNode quote = quote(null, line(pkg, session(10), 2), null);
        assertThat(quote.get("voucherDiscountVnd").asLong()).isEqualTo(50_000);
        assertThat(quote.get("finalTotalVnd").asLong()).isEqualTo(350_000);
        assertThat(quote.get("groupCollectionVnd").asLong()).isEqualTo(350_000);

        String code = createGroup(null, quote.get("quoteRef").asText()).get("groupCode").asText();
        confirmation.confirmPaidGroup(code, 350_000, "ipn-" + code, "op-" + code, "corr");

        BookingGroup group = groups.findByGroupCode(code).orElseThrow();
        assertThat(group.getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(group.getBuyerMemberAccountId()).isNull();
        assertThat(discountOf(code)).isEqualTo(50_000);
    }

    @Test
    @DisplayName("T097-d2: a Member voucher replaces the automatic discount, they are never added")
    void memberVoucherReplacesTheAutomaticDiscount() throws Exception {
        Long pkg = packageOf(200_000);
        autoDiscount(List.of(pkg), 50_000, 0);
        WorkshopSession session = session(10);
        String voucher = voucher(pkg, session, 100_000);

        JsonNode quote = quote("Bearer test-uid:member", line(pkg, session, 2),
                "{\"choice\":\"VOUCHER\",\"voucherCode\":\"" + voucher + "\"}");

        assertThat(quote.get("voucherDiscountVnd").asLong()).isEqualTo(100_000);
        assertThat(quote.get("finalTotalVnd").asLong()).isEqualTo(300_000);
    }

    @Test
    @DisplayName("T097-d3: a cart below the discount minimum keeps the plain price and reserves nothing")
    void cartBelowTheMinimumKeepsThePlainPrice() throws Exception {
        Long pkg = packageOf(200_000);
        autoDiscount(List.of(pkg), 50_000, 1_000_000);
        JsonNode quote = quote(null, line(pkg, session(10), 2), null);

        assertThat(quote.get("voucherDiscountVnd").asLong()).isZero();
        assertThat(quote.get("finalTotalVnd").asLong()).isEqualTo(400_000);
        String code = createGroup(null, quote.get("quoteRef").asText()).get("groupCode").asText();
        assertThat(groups.findByGroupCode(code).orElseThrow().getVoucherUseReference()).isNull();
        assertThat(discountOf(code)).isZero();
    }

    @Test
    @DisplayName("T097-d4: the discount is spread by largest remainder across tickets of different prices")
    void largestRemainderAllocationPerTicket() throws Exception {
        Long cheap = packageOf(100_000);
        Long dear = packageOf(200_000);
        autoDiscount(List.of(cheap, dear), 100_001, 0);
        JsonNode quote = quote(null, line(cheap, session(10), 1), null, line(dear, session(11), 1));
        long total = quote.get("voucherDiscountVnd").asLong();
        assertThat(total).isEqualTo(100_001);

        String code = createGroup(null, quote.get("quoteRef").asText()).get("groupCode").asText();
        List<TicketPriceSnapshot> perTicket = tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(groupId(code))
                .stream().map(t -> snapshots.findByTicketId(t.getId()).orElseThrow()).toList();

        assertThat(perTicket.stream().mapToLong(TicketPriceSnapshot::getVoucherDiscountVnd).sum()).isEqualTo(total);
        long cheapShare = perTicket.get(0).getVoucherDiscountVnd();
        long dearShare = perTicket.get(1).getVoucherDiscountVnd();
        // 100,001 * 1/3 = 33,333.67 and 100,001 * 2/3 = 66,667.33: floors 33,333 + 66,667, the remainder goes to the larger fraction.
        assertThat(cheapShare).isEqualTo(33_334);
        assertThat(dearShare).isEqualTo(66_667);
        assertThat(perTicket).allSatisfy(s -> assertThat(s.getFinalPriceVnd())
                .isEqualTo(s.getPackagePriceVnd() - s.getVoucherDiscountVnd()));
    }

    @Test
    @DisplayName("T097-d5: late payment of a group with an automatic discount passes condition 3 and re-confirms")
    void lateAutoDiscountGroupPassesConditionThree() throws Exception {
        Long pkg = packageOf(200_000);
        autoDiscount(List.of(pkg), 50_000, 0);
        JsonNode quote = quote(null, line(pkg, session(10), 2), null);
        String code = createGroup(null, quote.get("quoteRef").asText()).get("groupCode").asText();
        String merchant = expireWithAttempt(code, 350_000);

        tx.executeWithoutResult(status -> evidence.apply(code, merchant, 350_000, "VND", "VNPAY-" + merchant,
                "op-" + UUID.randomUUID(), "corr-late"));

        assertThat(reviews.findAll()).singleElement().satisfies(r -> assertThat(r.getOutcome())
                .isEqualTo(LatePaymentReview.AUTO_RECONFIRMED));
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("T097-d6: late payment of a voucher group still fails condition 3 and waits for the Manager")
    void lateVoucherGroupStillFailsConditionThree() throws Exception {
        Long pkg = packageOf(200_000);
        WorkshopSession session = session(10);
        String voucher = voucher(pkg, session, 100_000);
        JsonNode quote = quote("Bearer test-uid:member", line(pkg, session, 2),
                "{\"choice\":\"VOUCHER\",\"voucherCode\":\"" + voucher + "\"}");
        String code = createGroup("Bearer test-uid:member", quote.get("quoteRef").asText()).get("groupCode").asText();
        String merchant = expireWithAttempt(code, 300_000);

        tx.executeWithoutResult(status -> evidence.apply(code, merchant, 300_000, "VND", "VNPAY-" + merchant,
                "op-" + UUID.randomUUID(), "corr-late"));

        assertThat(reviews.findAll()).singleElement().satisfies(r -> {
            assertThat(r.getOutcome()).isEqualTo(LatePaymentReview.PENDING_MANAGER);
            assertThat(r.getFailedConditions()).contains("AMOUNT");
        });
        assertThat(groups.findByGroupCode(code).orElseThrow().getGroupStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    // ---- helpers ----

    /** Lets the hold run out, releases it, and leaves the payment attempt the late result will name. */
    private String expireWithAttempt(String code, long amount) {
        BookingGroup stored = groups.findByGroupCode(code).orElseThrow();
        stored.setHoldExpiresAt(Instant.now().minusSeconds(30));
        stored.setPaymentDeadlineAt(Instant.now().minusSeconds(150));
        groups.saveAndFlush(stored);
        tx.executeWithoutResult(status -> confirmation.releaseExpiredGroup(code, "op-exp-" + code, "corr"));
        String merchant = "M-" + code;
        attempts.save(GroupPaymentAttempt.builder().group(groups.findByGroupCode(code).orElseThrow())
                .attemptReference(UUID.randomUUID().toString()).merchantReference(merchant).amountVnd(amount)
                .attemptStatus(PaymentAttemptStatus.PENDING).expiresAt(stored.getHoldExpiresAt()).build());
        return merchant;
    }

    private JsonNode quote(String bearer, String firstLine, String benefitSelection, String... moreLines) throws Exception {
        String all = firstLine + (moreLines.length == 0 ? "" : "," + String.join(",", moreLines));
        MockHttpServletRequestBuilder request = post("/api/v1/workshop-cart-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[" + all + "]"
                        + (benefitSelection == null ? "" : ",\"benefitSelection\":" + benefitSelection) + "}");
        if (bearer != null) request.header("Authorization", bearer);
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createGroup(String bearer, String quoteRef) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/workshop-booking-groups")
                .header("Idempotency-Key", "disc-key-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteRef\":\"" + quoteRef + "\",\"contactEmail\":\"" + EMAIL
                        + "\",\"contactPhone\":\"0900000000\"}");
        if (bearer != null) request.header("Authorization", bearer);
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String line(Long packageId, WorkshopSession session, int participants) {
        return "{\"packageId\":" + packageId + ",\"sessionReferences\":[\"" + session.getReference()
                + "\"],\"participantCount\":" + participants + "}";
    }

    private static String line(Long packageId, WorkshopSession session, int participants, boolean unused) {
        return line(packageId, session, participants);
    }

    private Long packageOf(long price) {
        return packages.save(WorkshopPackage.builder().name("Disc package " + price + "-" + UUID.randomUUID())
                .description("fixture").publicPrice(price).minParticipants(1).maxParticipants(10)
                .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                .state(WorkshopPackageState.PUBLISHED).build()).getId();
    }

    private WorkshopSession session(int daysAhead) {
        Instant start = Instant.now().plus(Duration.ofDays(daysAhead)).plus(hourOffset++, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.MINUTES);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalTime startTime = start.atZone(zone).toLocalTime();
        WorkshopSlot slot = slots.save(WorkshopSlot.builder().reference(UUID.randomUUID().toString()).location(location)
                .label("Slot").startTime(startTime).endTime(startTime.plusHours(1)).defaultCapacity(10).active(true).build());
        return sessions.save(WorkshopSession.builder().reference(UUID.randomUUID().toString()).location(location)
                .slot(slot).workshopDate(start.atZone(zone).toLocalDate()).scheduledStartAt(start)
                .scheduledEndAt(start.plus(Duration.ofHours(1))).checkInOpensAt(start.minus(Duration.ofMinutes(30)))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT).capacity(10).state(SessionState.PUBLISHED).build());
    }

    /** A workshop automatic discount (006 FR-044) on the given packages. */
    private void autoDiscount(List<Long> packageIds, long amount, long minimumFee) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        tx.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(PromotionProgram.autoDiscount(TargetScope.WORKSHOP, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.AUTO_DISCOUNT, "Booking IT auto", now.minus(1, ChronoUnit.DAYS),
                    now.plus(30, ChronoUnit.DAYS), DiscountTerms.fixed(BigInteger.valueOf(amount),
                            BigInteger.valueOf(amount), BigInteger.valueOf(minimumFee)), true, now, 1L));
            for (Long packageId : packageIds) {
                targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.PACKAGE,
                        Long.toString(packageId)));
            }
            program.pointToRevision(revision.getId());
        });
    }

    /** A fixed workshop voucher on the package and the session's date. */
    private String voucher(Long packageId, WorkshopSession session, long amount) {
        String code = "WK" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        tx.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.WORKSHOP, code, code, 10, 1, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Booking IT voucher", now.minus(1, ChronoUnit.DAYS), now.plus(60, ChronoUnit.DAYS),
                    DiscountTerms.fixed(BigInteger.valueOf(amount), BigInteger.valueOf(amount), BigInteger.ZERO), true,
                    now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.PACKAGE,
                    Long.toString(packageId)));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.WORKSHOP_DATE,
                    session.getWorkshopDate().toString()));
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    private Long groupId(String code) {
        return groups.findByGroupCode(code).orElseThrow().getId();
    }

    private long discountOf(String code) {
        return tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(groupId(code)).stream()
                .mapToLong(t -> snapshots.findByTicketId(t.getId()).orElseThrow().getVoucherDiscountVnd()).sum();
    }
}
