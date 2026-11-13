package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopTicketFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.RetailQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.WorkshopBenefitEvaluator;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.WorkshopBenefitEvaluator.WorkshopCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T104 (Session 2026-11-14, 006 FR-044, D-013): a workshop automatic discount. It is targeted by package only, it
 * needs no code and no quota, it applies to every direct customer including a Guest, the largest discount wins with
 * the programme created first on a tie (FR-015), and a chosen voucher replaces it instead of adding to it.
 */
class WorkshopAutoDiscountTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PolicyTerms POLICY = PolicyTerms.defaults();
    private static final LocalDate DAY = LocalDate.parse("2026-11-20");
    private static final String SESSION = "00000000-0000-4000-8000-000000000003";
    private static final Instant EARLIER = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-02T00:00:00Z");
    private static final BenefitSelection AUTO = new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO);

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    /** Package 1, one session at branch 2, four participants, the given package fee. */
    private static WorkshopFact booking(long fee) {
        return WorkshopFact.singleSession(1L, 4, n(fee), WorkshopSessionFact.of(SESSION, 2L, DAY));
    }

    /** A cart of two tickets: one of package 1 and one of package 5, at the given prices. */
    private static WorkshopFact cart(long firstPrice, long secondPrice) {
        List<WorkshopSessionFact> sessions = List.of(WorkshopSessionFact.of(SESSION, 2L, DAY));
        return WorkshopFact.cart(List.of(new WorkshopTicketFact("L0-T1", 1L, n(firstPrice), sessions),
                new WorkshopTicketFact("L1-T1", 5L, n(secondPrice), sessions)));
    }

    private static WorkshopCandidate auto(long programId, Instant createdAt, DiscountTerms terms, Set<Long> packages) {
        return WorkshopCandidate.auto(new RetailQuote.Candidate(programId, programId * 10, 1,
                ProgramKind.AUTO_DISCOUNT, createdAt, terms, true, Set.of(), Set.of(), null, null), packages);
    }

    private static WorkshopCandidate voucher(DiscountTerms terms) {
        return new WorkshopCandidate(new RetailQuote.Candidate(9, 90, 1, ProgramKind.VOUCHER, EARLIER, terms, true,
                Set.of(), Set.of(), "WS9", null), Set.of(1L), Set.of(), Set.of(), Set.of());
    }

    private static DiscountTerms fixed(long amount, long cap, long minimum) {
        return DiscountTerms.fixed(n(amount), n(cap), n(minimum));
    }

    private static RetailQuote.Result compute(WorkshopFact fact, BenefitSelection selection, WorkshopCandidate voucher,
                                              List<WorkshopCandidate> autos, boolean strict) {
        return WorkshopBenefitEvaluator.compute(fact, selection, voucher, autos, POLICY, n(0), strict);
    }

    // --- Creation: scope and targets (FR-044) ------------------------------------------------------------------

    private static JsonNode createBody(String kind, String scope, String targets) {
        String voucherFields = kind.equals("VOUCHER")
                ? "\"code\":\"MLABCDEFGH\",\"totalLimit\":\"10\",\"perMemberLimit\":\"1\",\"allowPoints\":true,"
                : "";
        try {
            return JSON.readTree("{\"kind\":\"" + kind + "\",\"scope\":\"" + scope + "\",\"name\":\"Giảm gói gốm\","
                    + "\"startAt\":\"2026-11-15T00:00:00Z\",\"endAt\":\"2026-11-30T00:00:00Z\","
                    + "\"discount\":{\"kind\":\"PERCENT\",\"percentage\":\"10\",\"maxDiscountAmount\":\"100000\"},"
                    + "\"minimumEligibleAmount\":\"0\"," + voucherFields + "\"targets\":" + targets + "}");
        } catch (Exception e) {
            throw new IllegalStateException("The test body is not valid JSON", e);
        }
    }

    private static void assertInvalid(Runnable action, String... fields) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class, e -> {
            assertThat(e.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT);
            assertThat(e.fieldIssues()).extracting(LoyaltyException.FieldIssue::field).contains(fields);
        });
    }

    @Test
    void aWorkshopAutomaticDiscountIsCreatedWithPackagesOnlyAndKeepsTheAutoRules() {
        PromotionWriteDtos.CreateProgram request = PromotionWriteDtos.parseCreate(
                createBody("AUTO_DISCOUNT", "WORKSHOP", "{\"packageIds\":[\"1\",\"5\"]}"));
        assertThat(request.kind()).isEqualTo(ProgramKind.AUTO_DISCOUNT);
        assertThat(request.scope()).isEqualTo(TargetScope.WORKSHOP);
        assertThat(request.targets()).containsExactly(new TargetRef(TargetDimension.PACKAGE, "1"),
                new TargetRef(TargetDimension.PACKAGE, "5"));
        // No code, no quota, points always combinable.
        assertThat(request.code()).isNull();
        assertThat(request.totalLimit()).isNull();
        assertThat(request.perMemberLimit()).isNull();
        assertThat(request.allowPoints()).isTrue();
        PromotionProgram program = PromotionProgram.autoDiscount(TargetScope.WORKSHOP, EARLIER, 7L);
        assertThat(program.getScope()).isEqualTo(TargetScope.WORKSHOP);
        assertThat(program.getKind()).isEqualTo(ProgramKind.AUTO_DISCOUNT);
        assertThat(program.getCodeKey()).isNull();
        assertThat(program.getTotalLimit()).isNull();
    }

    @Test
    void aWorkshopAutomaticDiscountRefusesBranchesDatesAndSessions() {
        assertInvalid(() -> PromotionWriteDtos.parseCreate(
                createBody("AUTO_DISCOUNT", "WORKSHOP", "{\"packageIds\":[\"1\"],\"branchIds\":[\"2\"]}")),
                "targets.branchIds");
        assertInvalid(() -> PromotionWriteDtos.parseCreate(
                createBody("AUTO_DISCOUNT", "WORKSHOP", "{\"packageIds\":[\"1\"],\"workshopDates\":[\"2026-11-20\"]}")),
                "targets.workshopDates");
        assertInvalid(() -> PromotionWriteDtos.parseCreate(createBody("AUTO_DISCOUNT", "WORKSHOP",
                "{\"packageIds\":[\"1\"],\"sessionIds\":[\"" + SESSION + "\"]}")), "targets.sessionIds");
        // A workshop voucher still takes all four dimensions (FR-019).
        assertThatCode(() -> PromotionWriteDtos.parseCreate(
                createBody("VOUCHER", "WORKSHOP", "{\"packageIds\":[\"1\"],\"branchIds\":[\"2\"]}")))
                .doesNotThrowAnyException();
    }

    @Test
    void aWorkshopAutomaticDiscountNeedsAtLeastOnePackage() {
        assertInvalid(() -> PromotionWriteDtos.parseCreate(createBody("AUTO_DISCOUNT", "WORKSHOP", "{}")),
                "targets.packageIds");
    }

    // --- Evaluation (FR-014, FR-015, FR-019) ------------------------------------------------------------------

    @Test
    void theLargestDiscountWinsAmongValidProgrammes() {
        RetailQuote.Result result = compute(booking(1_000_000), AUTO, null,
                List.of(auto(1, EARLIER, fixed(50_000, 50_000, 0), Set.of(1L)),
                        auto(2, LATER, fixed(120_000, 120_000, 0), Set.of(1L))), false);
        assertThat(result.candidate().programId()).isEqualTo(2);
        assertThat(result.programDiscount()).isEqualTo(n(120_000));
        assertThat(result.netBeforePoints()).isEqualTo(n(880_000));
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void equalDiscountsGoToTheProgrammeCreatedFirst() {
        // The candidates arrive in (createdAt, id) order, as the repository reads them.
        RetailQuote.Result result = compute(booking(1_000_000), AUTO, null,
                List.of(auto(1, EARLIER, fixed(80_000, 80_000, 0), Set.of(1L)),
                        auto(2, LATER, fixed(80_000, 80_000, 0), Set.of(1L))), false);
        assertThat(result.candidate().programId()).isEqualTo(1);
        assertThat(result.programDiscount()).isEqualTo(n(80_000));
    }

    @Test
    void aPercentDiscountIsFlooredAndTheCapAppliesOnceForTheWholeBooking() {
        // FR-014/FR-019: 7% of 1.000.333 is 70.023,31 -> 70.023; the cap is not multiplied by the four participants.
        RetailQuote.Result floored = compute(booking(1_000_333), AUTO, null,
                List.of(auto(1, EARLIER, DiscountTerms.percent(new BigDecimal("7"), n(1_000_000), n(0)), Set.of(1L))),
                false);
        assertThat(floored.programDiscount()).isEqualTo(n(70_023));
        RetailQuote.Result capped = compute(booking(1_000_000), AUTO, null,
                List.of(auto(1, EARLIER, DiscountTerms.percent(new BigDecimal("50"), n(60_000), n(0)), Set.of(1L))),
                false);
        assertThat(capped.programDiscount()).isEqualTo(n(60_000));
    }

    @Test
    void aProgrammeBelowItsMinimumOrOutsideItsPackagesDoesNotApply() {
        RetailQuote.Result belowMinimum = compute(booking(100_000), AUTO, null,
                List.of(auto(1, EARLIER, fixed(50_000, 50_000, 500_000), Set.of(1L))), false);
        assertThat(belowMinimum.candidate()).isNull();
        assertThat(belowMinimum.programDiscount()).isZero();
        assertThat(belowMinimum.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES);

        RetailQuote.Result otherPackage = compute(booking(1_000_000), AUTO, null,
                List.of(auto(1, EARLIER, fixed(50_000, 50_000, 0), Set.of(9L))), false);
        assertThat(otherPackage.candidate()).isNull();
        assertThat(otherPackage.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES);
    }

    @Test
    void anUnavailableProgrammeIsSkippedInsteadOfRefusingTheBooking() {
        WorkshopCandidate paused = WorkshopCandidate.auto(new RetailQuote.Candidate(1, 10, 1,
                ProgramKind.AUTO_DISCOUNT, EARLIER, fixed(200_000, 200_000, 0), true, Set.of(), Set.of(), null,
                BenefitReason.of(BenefitReasonCode.PROMOTION_PAUSED)), Set.of(1L));
        RetailQuote.Result result = compute(booking(1_000_000), AUTO, null,
                List.of(paused, auto(2, LATER, fixed(50_000, 50_000, 0), Set.of(1L))), false);
        assertThat(result.candidate().programId()).isEqualTo(2);
        assertThat(result.programDiscount()).isEqualTo(n(50_000));
    }

    @Test
    void noProgrammeAtAllIsAReasonAndNotATypedFailureEvenInStrictMode() {
        RetailQuote.Result result = compute(booking(1_000_000), AUTO, null, List.of(), true);
        assertThat(result.candidate()).isNull();
        assertThat(result.programDiscount()).isZero();
        assertThat(result.earnBase()).isEqualTo(n(1_000_000));
        assertThat(result.reasonDetails()).extracting(BenefitReason::code)
                .containsExactly(BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES);
    }

    @Test
    void aChosenVoucherReplacesTheAutomaticDiscountInsteadOfAddingToIt() {
        // FR-015: one automatic discount OR one voucher. The voucher selection never looks at the automatic ones.
        RetailQuote.Result result = compute(booking(1_000_000),
                new BenefitSelection(SelectionChoice.VOUCHER, "WS9", BigInteger.ZERO), voucher(fixed(30_000, 30_000, 0)),
                List.of(auto(1, EARLIER, fixed(200_000, 200_000, 0), Set.of(1L))), true);
        assertThat(result.candidate().kind()).isEqualTo(ProgramKind.VOUCHER);
        assertThat(result.programDiscount()).isEqualTo(n(30_000));
        assertThat(result.netBeforePoints()).isEqualTo(n(970_000));
    }

    @Test
    void aCartDiscountsOnlyTheTicketsInScopeAndReportsTheirIndexes() {
        // 003.1 FR-033: the minimum and the discount use the in-scope tickets, the cap applies once to the group,
        // and 003.1 allocates the group discount over the reported tickets only.
        RetailQuote.Result result = compute(cart(400_000, 600_000), AUTO, null,
                List.of(auto(1, EARLIER, DiscountTerms.percent(new BigDecimal("10"), n(1_000_000), n(0)), Set.of(1L))),
                false);
        assertThat(result.eligibleSubtotal()).isEqualTo(n(400_000));
        assertThat(result.programDiscount()).isEqualTo(n(40_000));
        assertThat(result.grossAmount()).isEqualTo(n(1_000_000));
        assertThat(result.eligibleLines()).containsExactly(0);
    }

    // --- Guest (FR-044) -----------------------------------------------------------------------------------------

    @Test
    void aGuestWorkshopContextIsAcceptedAndNeedsNoMember() {
        TrustedSourceContext guest = new TrustedSourceContext(SourceKind.WORKSHOP, TrustedSourceContext.GUEST,
                EARLIER, List.of(), booking(1_000_000), BigInteger.ZERO);
        assertThat(guest.isGuest()).isTrue();
        TrustedSourceContext member = new TrustedSourceContext(SourceKind.WORKSHOP, 42L, EARLIER, List.of(),
                booking(1_000_000), BigInteger.ZERO);
        assertThat(member.isGuest()).isFalse();
    }

    @Test
    void aGuestRetailContextIsStillRefused() {
        assertThatThrownBy(() -> new TrustedSourceContext(SourceKind.RETAIL, TrustedSourceContext.GUEST, EARLIER,
                List.of(), null, BigInteger.ZERO))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT));
    }

    @Test
    void aGuestBookingGetsTheAutomaticDiscountWithoutAWallet() {
        // The evaluator needs no Member: a Guest has no points, so the quote is the automatic discount alone.
        RetailQuote.Result result = compute(booking(1_000_000), AUTO, null,
                List.of(auto(1, EARLIER, fixed(150_000, 150_000, 0), Set.of(1L))), true);
        assertThat(result.programDiscount()).isEqualTo(n(150_000));
        assertThat(result.pointsUsed()).isZero();
        assertThat(result.earnBase()).isEqualTo(n(850_000));
    }
}
