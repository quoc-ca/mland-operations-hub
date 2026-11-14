package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopTicketFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Workshop journey through {@link LoyaltyFacade} on MySQL 8.4 with this test acting as the booking owner (US5.2-US5.5,
 * US5.7). FE-03 is not on this branch, so this is NOT the T057 / G-04 / G-05 evidence: it only shows that loyalty
 * computes the post-promotion amount, holds at accept, consumes on the deposit without earning, and earns once on
 * completed-and-fully-paid. The deposit itself is FE-03's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyWorkshopFacadeIT {

    private static final LocalDate DAY = LocalDate.parse("2026-12-05");

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private AcceptedBenefitRepository benefits;

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private String workshopVoucher(long packageId) {
        return workshopVoucher(packageId, List.of(DAY));
    }

    /** Fixed 100.000 on the package, limited to the given workshop dates. */
    private String workshopVoucher(long packageId, List<LocalDate> days) {
        String code = "WK" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.WORKSHOP, code, code, 10, 1, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Workshop IT", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                    DiscountTerms.fixed(n(100_000), n(100_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.PACKAGE,
                    Long.toString(packageId)));
            for (LocalDate day : days) {
                targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.WORKSHOP_DATE,
                        day.toString()));
            }
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    /**
     * A workshop automatic discount on the package (Session 2026-11-14, FR-044): no code, no quota, points always
     * combinable, packages as the only target dimension. Saved through the repositories, so the row also has to
     * satisfy the database rule V134 relaxed.
     */
    private long workshopAutoDiscount(long packageId, long amount, Instant createdAt) {
        return transaction.execute(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.autoDiscount(TargetScope.WORKSHOP, createdAt, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.AUTO_DISCOUNT, "Workshop auto IT", createdAt.minus(1, ChronoUnit.DAYS),
                    createdAt.plus(30, ChronoUnit.DAYS), DiscountTerms.fixed(n(amount), n(amount), BigInteger.ZERO),
                    true, createdAt, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.PACKAGE,
                    Long.toString(packageId)));
            program.pointToRevision(revision.getId());
            return program.getId();
        });
    }

    private static TrustedSourceContext guestBooking(long packageId, LocalDate day) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, TrustedSourceContext.GUEST, Instant.now(), List.of(),
                WorkshopFact.singleSession(packageId, 2, n(1_000_000), WorkshopSessionFact.of(SESSION_1, 2L, day)),
                BigInteger.ZERO);
    }

    private long member(long points) {
        long memberId = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(memberId);
            wallet.earn(n(points));
            wallets.saveAndFlush(wallet);
        });
        return memberId;
    }

    private static final String SESSION_1 = "6f1c2a3b-0000-4000-8000-000000000001";
    private static final String SESSION_2 = "6f1c2a3b-0000-4000-8000-000000000002";
    private static final String SESSION_3 = "6f1c2a3b-0000-4000-8000-000000000003";

    private static TrustedSourceContext booking(long memberId, long packageId, LocalDate day) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.singleSession(packageId, 2, n(1_000_000), WorkshopSessionFact.of(SESSION_1, 2L, day)),
                BigInteger.ZERO);
    }

    /** A two-session package (Silverclay): one fee for both sessions, each session with its own branch and date. */
    private static TrustedSourceContext twoSessionBooking(long memberId, long packageId, String first, LocalDate firstDay,
                                                          String second, LocalDate secondDay) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                new WorkshopFact(packageId, 2, n(1_000_000), List.of(WorkshopSessionFact.of(first, 2L, firstDay),
                        WorkshopSessionFact.of(second, 2L, secondDay))), BigInteger.ZERO);
    }

    private static AcceptedSourceContext accept(TrustedSourceContext booking, String ref, String digest) {
        Instant now = Instant.now();
        return new AcceptedSourceContext(booking, ref, 1L, true, now, now.plusSeconds(900), "quote-" + ref, digest);
    }

    private static AuthoritativeOutcome outcome(String ref, OutcomeKind kind, String key, long version) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(SourceKind.WORKSHOP, ref, "workshopbooking", key, version, kind, now,
                now.plusSeconds(900), "snap", true, "proof");
    }

    @Test
    void depositConsumesWithoutEarningAndCompletionEarnsOnce() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1_000);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(200));
        BenefitPreview preview = loyalty.previewBenefits(booking(memberId, packageId, DAY), selection);
        assertThat(preview.postPromotionAmount()).isEqualTo(n(880_000));
        assertThat(preview.maxPoints()).isEqualTo(n(900));

        String ref = "booking-" + UUID.randomUUID();
        AcceptedBenefitRef accepted = transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(booking(memberId, packageId, DAY), ref, preview.termsDigest()), selection, "cmd"));
        assertThat(accepted.postPromotionAmount()).isEqualTo(n(880_000));

        AuthoritativeOutcome deposit = outcome(ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "dep-" + ref, 2);
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(deposit));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(deposit)))
                .isInstanceOf(LoyaltyException.class);
        assertThat(wallets.findById(memberId).orElseThrow().getBalancePoints()).isEqualTo(n(800));

        AuthoritativeOutcome completed = outcome(ref, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-" + ref, 3);
        transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(completed));
        transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(completed));
        assertThat(wallets.findById(memberId).orElseThrow().getBalancePoints()).isEqualTo(n(888));
        AcceptedBenefit benefit = benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, ref).orElseThrow();
        assertThat(benefit.getEarnState()).isEqualTo(EarnState.GRANTED);
        assertThat(benefit.getPostPromotionAmount()).isEqualTo(n(880_000));
    }

    @Test
    void aVoucherForAnotherDateOrAProductVoucherIsRefused() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        BenefitPreview otherDay = loyalty.previewBenefits(booking(memberId, packageId, DAY.plusDays(1)), selection);
        assertThat(otherDay.programId()).isNull();
        assertThat(otherDay.reasons()).isNotEmpty();
        assertThatThrownBy(() -> transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(booking(memberId, packageId, DAY.plusDays(1)), "booking-" + UUID.randomUUID(),
                        otherDay.termsDigest()), selection, "cmd")))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE));
    }

    /**
     * Reference flow for 003.1 (integration guide, "workshop-booking-integration.md"): this test plays the booking
     * owner step by step.
     */
    @Test
    void referenceBookingFlowForATwoSessionPackage() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId, List.of(DAY, DAY.plusDays(7)));
        long memberId = member(1_000);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(200));

        // 1. Booking builds the context from its OWN authoritative data (package fee, the sessions it will hold,
        //    branch, dates, participants), never from amounts sent by the browser, and asks for a preview.
        TrustedSourceContext shown = twoSessionBooking(memberId, packageId, SESSION_1, DAY, SESSION_2, DAY.plusDays(7));
        BenefitPreview preview = loyalty.previewBenefits(shown, selection);
        assertThat(preview.reasons()).isEmpty();
        assertThat(preview.postPromotionAmount()).isEqualTo(n(880_000));

        // 2. Booking stores its own quote (quoteRef = its ID) with what the Member saw: package, sessions, people,
        //    selection and preview.termsDigest(), and shows the totals; the Member confirms that quote.
        String quoteRef = "quote-" + UUID.randomUUID();

        // 3. Accept: ONE booking transaction holds the seats (003.2), reserves the benefits and stores the booking.
        //    The context is rebuilt from the stored quote's package/sessions/people, with the quote's digest.
        String bookingRef = "booking-" + UUID.randomUUID();
        AcceptedBenefitRef accepted = transaction.execute(status -> {
            // ... WorkshopOperationsFacade create-and-reserve for every session, in this same transaction ...
            AcceptedBenefitRef ref = loyalty.reserveAcceptedTransaction(new AcceptedSourceContext(
                    twoSessionBooking(memberId, packageId, SESSION_1, DAY, SESSION_2, DAY.plusDays(7)), bookingRef, 1L,
                    true, Instant.now(), Instant.now().plusSeconds(900), quoteRef, preview.termsDigest()),
                    selection, "create-" + bookingRef);
            // ... 003.1 stores the booking with deposit = ceil(post / 2) and remaining = post - deposit ...
            return ref;
        });
        assertThat(accepted.postPromotionAmount()).isEqualTo(n(880_000));
        assertThat(accepted.voucherState()).isEqualTo(ComponentState.HELD);

        // 4. Verified deposit settles; completion of every session with full payment earns once.
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(bookingRef, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "dep-" + bookingRef, 2)));
        transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(
                outcome(bookingRef, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-" + bookingRef, 3)));
        assertThat(wallets.findById(memberId).orElseThrow().getBalancePoints()).isEqualTo(n(1_000 - 200 + 88));
    }

    @Test
    void aVoucherCoveringOnlyOneOfTheTwoSessionsIsRefused() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        // The voucher targets DAY only; the second session is a week later.
        BenefitPreview preview = loyalty.previewBenefits(
                twoSessionBooking(memberId, packageId, SESSION_1, DAY, SESSION_2, DAY.plusDays(7)), selection);
        assertThat(preview.programId()).isNull();
        assertThat(preview.reasons()).isNotEmpty();
    }

    @Test
    void acceptingOtherSessionsThanTheQuotedOnesIsQuoteChanged() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        long memberId = member(1_000);
        BenefitSelection points = new BenefitSelection(SelectionChoice.NONE, null, n(100));
        BenefitPreview quoted = loyalty.previewBenefits(
                twoSessionBooking(memberId, packageId, SESSION_1, DAY, SESSION_2, DAY.plusDays(7)), points);
        String bookingRef = "booking-" + UUID.randomUUID();

        assertThatThrownBy(() -> transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(twoSessionBooking(memberId, packageId, SESSION_1, DAY, SESSION_3, DAY.plusDays(8)), bookingRef,
                        quoted.termsDigest()), points, "cmd")))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.QUOTE_CHANGED));
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, bookingRef)).isEmpty();
        assertThat(wallets.findById(memberId).orElseThrow().getHeldPoints()).isZero();
    }

    /** Cart (Session 2026-11-12): two tickets of the targeted package and one of another package, all on DAY. */
    private static TrustedSourceContext cart(long memberId, long packageId, long otherPrice) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.cart(List.of(
                        new WorkshopTicketFact("t-1", packageId, n(600_000), List.of(WorkshopSessionFact.of(SESSION_1, 2L, DAY))),
                        new WorkshopTicketFact("t-2", packageId + 1, n(otherPrice), List.of(WorkshopSessionFact.of(SESSION_2, 2L, DAY))),
                        new WorkshopTicketFact("t-3", packageId, n(400_000), List.of(WorkshopSessionFact.of(SESSION_1, 2L, DAY))))),
                BigInteger.ZERO);
    }

    @Test
    void aCartDiscountsOnlyInScopeTicketsAndBindsTheTickets() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1_000);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(100));
        BenefitPreview preview = loyalty.previewBenefits(cart(memberId, packageId, 500_000), selection);
        assertThat(preview.grossAmount()).isEqualTo(n(1_500_000));
        assertThat(preview.eligibleSubtotal()).isEqualTo(n(1_000_000));
        assertThat(preview.programDiscount()).isEqualTo(n(100_000));
        assertThat(preview.eligibleLineIndexes()).containsExactly(0, 2);
        assertThat(preview.postPromotionAmount()).isEqualTo(n(1_390_000));

        // Another price on the out-of-scope ticket than the one previewed is QUOTE_CHANGED and holds nothing.
        String changedRef = "group-" + UUID.randomUUID();
        assertThatThrownBy(() -> transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(cart(memberId, packageId, 500_001), changedRef, preview.termsDigest()), selection, "cmd")))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.QUOTE_CHANGED));
        assertThat(wallets.findById(memberId).orElseThrow().getHeldPoints()).isZero();

        String ref = "group-" + UUID.randomUUID();
        AcceptedBenefitRef accepted = transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(cart(memberId, packageId, 500_000), ref, preview.termsDigest()), selection, "cmd"));
        assertThat(accepted.postPromotionAmount()).isEqualTo(n(1_390_000));
        assertThat(wallets.findById(memberId).orElseThrow().getHeldPoints()).isEqualTo(n(100));
    }

    /**
     * T100 (FR-022, US5.7, decision 2026-11-12): a voucher that takes the package fee to exactly 0đ is accepted and
     * stored; FE-03 confirms the booking without the gateway and settles it like a deposit.
     */
    @Test
    void aZeroFeeWorkshopBookingIsAcceptedStoredAndSettled() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1);
        TrustedSourceContext zero = new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.singleSession(packageId, 1, n(100_000), WorkshopSessionFact.of(SESSION_1, 2L, DAY)),
                BigInteger.ZERO);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO);
        BenefitPreview preview = loyalty.previewBenefits(zero, selection);
        assertThat(preview.postPromotionAmount()).isZero();
        assertThat(preview.reasons()).isEmpty();

        String ref = "booking-" + UUID.randomUUID();
        AcceptedBenefitRef accepted = transaction.execute(status ->
                loyalty.reserveAcceptedTransaction(accept(zero, ref, preview.termsDigest()), selection, "cmd"));
        assertThat(accepted.postPromotionAmount()).isZero();
        assertThat(accepted.voucherState()).isEqualTo(ComponentState.HELD);

        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "zero-" + ref, 2)));
        AcceptedBenefit stored = benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, ref).orElseThrow();
        assertThat(stored.getPostPromotionAmount()).isZero();
        assertThat(stored.getVoucherState()).isEqualTo(ComponentState.CONSUMED);
    }

    // ---- T103 (Session 2026-11-12, D-012): per-ticket outcomes of a workshop cart (003.3 FR-030/FR-042/FR-043) ----

    private record TicketCart(long memberId, String code, String ref, AcceptedBenefitRef held) {
        edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPartRef ticket(String ref) {
            return held.parts().stream().filter(p -> p.partRef().equals(ref)).findFirst().orElseThrow();
        }
    }

    /** Three 500.000 tickets t-1..t-3 of the voucher package on DAY, voucher 100.000 and 100 points; deposit settled. */
    private TicketCart acceptAndSettleThreeTickets() {
        long packageId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        String code = workshopVoucher(packageId);
        long memberId = member(1_000);
        List<WorkshopTicketFact> tickets = List.of("t-1", "t-2", "t-3").stream()
                .map(t -> new WorkshopTicketFact(t, packageId, n(500_000), List.of(WorkshopSessionFact.of(SESSION_1, 2L, DAY))))
                .toList();
        TrustedSourceContext group = new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.cart(tickets), BigInteger.ZERO);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.VOUCHER, code, n(100));
        BenefitPreview preview = loyalty.previewBenefits(group, selection);
        String ref = "group-" + UUID.randomUUID();
        AcceptedBenefitRef held = transaction.execute(status ->
                loyalty.reserveAcceptedTransaction(accept(group, ref, preview.termsDigest()), selection, "cmd"));
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "dep-" + ref, 2)));
        return new TicketCart(memberId, code, ref, held);
    }

    private static AuthoritativeOutcome ticketOutcome(String ref, OutcomeKind kind, String key, String ticket) {
        Instant now = Instant.now();
        return new AuthoritativeOutcome(SourceKind.WORKSHOP, ref, "workshopbooking", key + "-" + ref, 3, kind, now,
                now.plusSeconds(900), "snap", true, "proof", ticket);
    }

    private BigInteger balance(long memberId) {
        return wallets.findById(memberId).orElseThrow().getBalancePoints();
    }

    @Test
    void aWorkshopCartIsSplitPerTicketAtAccept() {
        TicketCart cart = acceptAndSettleThreeTickets();
        assertThat(cart.held().parts()).extracting(p -> p.partRef()).containsExactly("t-1", "t-2", "t-3");
        assertThat(cart.held().parts()).extracting(p -> p.programDiscount())
                .containsExactly(n(33_334), n(33_333), n(33_333));
        assertThat(cart.held().parts()).extracting(p -> p.pointsHeld()).containsExactly(n(33), n(34), n(33));
        assertThat(cart.held().parts().stream().map(p -> p.earnPoints()).reduce(BigInteger.ZERO, BigInteger::add))
                .as("ticket earn shares add up to the group's points").isEqualTo(n(1_400_000)
                        .subtract(cart.held().pointDiscount()).divide(n(10_000)));
        assertThat(balance(cart.memberId())).as("the group deposit debited the 100 points").isEqualTo(n(900));
    }

    @Test
    void ticketsEarnOneByOneAndACancelledTicketGetsItsPointsBack() {
        TicketCart cart = acceptAndSettleThreeTickets();
        String ref = cart.ref();

        // t-2 validly cancelled: its 34 points come back; the voucher stays used (t-1, t-3 still running).
        BenefitResult cancelled = transaction.execute(status -> loyalty.restoreCancelledWorkshopBenefits(
                ticketOutcome(ref, OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, "cancel-2", "t-2")));
        assertThat(balance(cart.memberId())).isEqualTo(n(934));
        assertThat(cancelled.voucherState()).isEqualTo(ComponentState.CONSUMED);

        // t-1 checked out: earns only its share, once.
        BigInteger e1 = cart.ticket("t-1").earnPoints();
        transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(
                ticketOutcome(ref, OutcomeKind.WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID, "done-1", "t-1")));
        transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(
                ticketOutcome(ref, OutcomeKind.WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID, "done-1", "t-1")));
        assertThat(balance(cart.memberId())).isEqualTo(n(934).add(e1));

        // t-3 no-show: nothing back, no earn, the booking's earn is now the sum of earned tickets.
        BenefitResult last = transaction.execute(status -> loyalty.finishIneligibleEarn(
                ticketOutcome(ref, OutcomeKind.WORKSHOP_TICKET_NO_SHOW_FINAL, "noshow-3", "t-3")));
        assertThat(last.earnState()).isEqualTo(EarnState.GRANTED);
        assertThat(last.voucherState()).as("a no-show blocks the voucher return").isEqualTo(ComponentState.CONSUMED);
        assertThat(last.parts()).extracting(p -> p.closure())
                .containsExactly("COMPLETED", "VALID_CANCELLATION", "NO_SHOW");
        AcceptedBenefit stored = benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, ref).orElseThrow();
        assertThat(stored.getEarnedPoints()).isEqualTo(e1);

        // A whole-booking earn after per-ticket outcomes is a conflict.
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> loyalty.grantEarnedPoints(
                outcome(ref, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "all-" + ref, 4))))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.BENEFIT_STATE_CONFLICT));
        // The group deposit replayed after ticket outcomes is still a no-op.
        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "dep-" + ref, 2)));
    }

    @Test
    void aMlandCancellationGivesTheVoucherBackOnceEvenForPartOfTheGroup() {
        TicketCart cart = acceptAndSettleThreeTickets();
        long consumedBefore = programs.findByCodeKey(cart.code()).orElseThrow().getConsumedCount();
        BenefitResult mland = transaction.execute(status -> loyalty.restoreCancelledWorkshopBenefits(
                ticketOutcome(cart.ref(), OutcomeKind.WORKSHOP_TICKET_MLAND_CANCELLATION, "mland-1", "t-1")));
        assertThat(mland.voucherState()).isEqualTo(ComponentState.RESTORED);
        assertThat(programs.findByCodeKey(cart.code()).orElseThrow().getConsumedCount()).isEqualTo(consumedBefore - 1);
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(
                ticketOutcome(cart.ref(), OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, "cancel-2", "t-2")));
        assertThat(programs.findByCodeKey(cart.code()).orElseThrow().getConsumedCount())
                .as("never a second time").isEqualTo(consumedBefore - 1);
        assertThat(balance(cart.memberId())).isEqualTo(n(900 + 33 + 34));
    }

    @Test
    void theVoucherComesBackWhenEveryTicketIsValidlyCancelled() {
        TicketCart cart = acceptAndSettleThreeTickets();
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(
                ticketOutcome(cart.ref(), OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, "c1", "t-1")));
        BenefitResult two = transaction.execute(status -> loyalty.restoreCancelledWorkshopBenefits(
                ticketOutcome(cart.ref(), OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, "c2", "t-2")));
        assertThat(two.voucherState()).isEqualTo(ComponentState.CONSUMED);
        BenefitResult three = transaction.execute(status -> loyalty.restoreCancelledWorkshopBenefits(
                ticketOutcome(cart.ref(), OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, "c3", "t-3")));
        assertThat(three.voucherState()).isEqualTo(ComponentState.RESTORED);
        assertThat(three.pointsState()).isEqualTo(ComponentState.RESTORED);
        assertThat(three.earnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(balance(cart.memberId())).isEqualTo(n(1_000));
    }

    @Test
    void anAutomaticSelectionWithoutAWorkshopProgrammeGivesNoDiscount() {
        long memberId = member(1);
        BenefitPreview preview = loyalty.previewBenefits(booking(memberId, 1L, DAY),
                new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO));
        assertThat(preview.programId()).isNull();
        assertThat(preview.reasons()).isNotEmpty();
    }

    @Test
    void theLargestWorkshopAutomaticDiscountIsAppliedToAMemberAndToAGuestAlike() {
        // FR-044: the discount is the same number for everyone, which is why it may be shown publicly (FR-045).
        long packageId = ThreadLocalRandom.current().nextLong(500_000L, 900_000L);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        workshopAutoDiscount(packageId, 60_000, now.minus(2, ChronoUnit.DAYS));
        long larger = workshopAutoDiscount(packageId, 150_000, now.minus(1, ChronoUnit.DAYS));
        BenefitSelection auto = new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO);

        BenefitPreview forMember = loyalty.previewBenefits(booking(member(1_000), packageId, DAY), auto);
        assertThat(forMember.programId()).isEqualTo(larger);
        assertThat(forMember.programDiscount()).isEqualTo(n(150_000));
        assertThat(forMember.netBeforePoints()).isEqualTo(n(850_000));
        assertThat(forMember.pointsAllowed()).isTrue();

        BenefitPreview forGuest = loyalty.previewBenefits(guestBooking(packageId, DAY), auto);
        assertThat(forGuest.programId()).isEqualTo(larger);
        assertThat(forGuest.programDiscount()).isEqualTo(n(150_000));
        assertThat(forGuest.netBeforePoints()).isEqualTo(n(850_000));
        assertThat(forGuest.pointsUsed()).isZero();
        assertThat(forGuest.maxPoints()).isZero();
    }

    @Test
    void aGuestGetsNoVoucherAndNoPoints() {
        long packageId = ThreadLocalRandom.current().nextLong(500_000L, 900_000L);
        workshopAutoDiscount(packageId, 50_000, Instant.now().truncatedTo(ChronoUnit.MICROS));
        String code = workshopVoucher(packageId);
        assertThatThrownBy(() -> loyalty.previewBenefits(guestBooking(packageId, DAY),
                new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO)))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE));
        assertThatThrownBy(() -> loyalty.previewBenefits(guestBooking(packageId, DAY),
                new BenefitSelection(SelectionChoice.AUTO, null, n(100))))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE));
    }

    /**
     * Session 2026-11-14 (D-014): a Guest accepts the workshop automatic discount. The snapshot has no Member, the
     * deposit settles it, the completion closes the earn as a zero grant, and no wallet is ever touched.
     */
    @Test
    void aGuestAcceptsTheAutomaticDiscountWithoutAMemberAndEarnsNothing() {
        long packageId = ThreadLocalRandom.current().nextLong(500_000L, 900_000L);
        long autoId = workshopAutoDiscount(packageId, 50_000, Instant.now().truncatedTo(ChronoUnit.MICROS));
        TrustedSourceContext guest = guestBooking(packageId, DAY);
        BenefitSelection auto = new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO);
        BenefitPreview preview = loyalty.previewBenefits(guest, auto);
        String ref = "BK-GUEST-" + UUID.randomUUID();

        AcceptedBenefitRef held = transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(guest, ref, preview.termsDigest()), auto, "create-guest"));
        assertThat(held.programDiscount()).isEqualTo(n(50_000));
        assertThat(held.postPromotionAmount()).isEqualTo(n(950_000));
        AcceptedBenefit stored = benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, ref).orElseThrow();
        assertThat(stored.getMemberId()).isNull();
        assertThat(stored.getProgramId()).isEqualTo(autoId);
        assertThat(stored.getPointsState()).isEqualTo(ComponentState.NONE);
        assertThat(stored.getVoucherState()).isEqualTo(ComponentState.NONE);
        // The same booking accepted again replays the snapshot.
        AcceptedBenefitRef again = transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(guest, ref, preview.termsDigest()), auto, "create-guest"));
        assertThat(again.acceptedBenefitId()).isEqualTo(held.acceptedBenefitId());

        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "dep-" + ref, 2)));
        AuthoritativeOutcome completed = outcome(ref, OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, "done-" + ref, 3);
        BenefitResult earned = transaction.execute(status -> loyalty.grantEarnedPoints(completed));
        assertThat(earned.earnState()).isEqualTo(EarnState.GRANTED);
        AcceptedBenefit done = benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, ref).orElseThrow();
        assertThat(done.getSettledAt()).isNotNull();
        assertThat(done.getEarnedPoints()).isZero();
    }

    @Test
    void aGuestCannotHoldAVoucherOrPointsAtAcceptance() {
        long packageId = ThreadLocalRandom.current().nextLong(500_000L, 900_000L);
        workshopAutoDiscount(packageId, 50_000, Instant.now().truncatedTo(ChronoUnit.MICROS));
        String code = workshopVoucher(packageId);
        TrustedSourceContext guest = guestBooking(packageId, DAY);
        for (BenefitSelection selection : List.of(new BenefitSelection(SelectionChoice.VOUCHER, code, BigInteger.ZERO),
                new BenefitSelection(SelectionChoice.AUTO, null, n(100)))) {
            assertThatThrownBy(() -> transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                    accept(guest, "BK-GUEST-" + UUID.randomUUID(), "digest"), selection, "create-guest")))
                    .isInstanceOfSatisfying(LoyaltyException.class,
                            e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.PROMOTION_INELIGIBLE));
        }
    }

    /** A Guest cart: per-ticket shares exist (D-012), carry no points and earn nothing; ticket outcomes still apply. */
    @Test
    void aGuestCartKeepsPerTicketSharesWithoutPoints() {
        long packageId = ThreadLocalRandom.current().nextLong(500_000L, 900_000L);
        workshopAutoDiscount(packageId, 100_000, Instant.now().truncatedTo(ChronoUnit.MICROS));
        TrustedSourceContext guest = cart(TrustedSourceContext.GUEST, packageId, 300_000);
        BenefitSelection auto = new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO);
        BenefitPreview preview = loyalty.previewBenefits(guest, auto);
        String ref = "BK-GUEST-" + UUID.randomUUID();
        AcceptedBenefitRef held = transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                accept(guest, ref, preview.termsDigest()), auto, "create-guest-cart"));
        assertThat(held.programDiscount()).isEqualTo(n(100_000));
        assertThat(held.parts()).hasSize(3).allSatisfy(part -> {
            assertThat(part.pointsHeld()).isZero();
            assertThat(part.earnPoints()).isZero();
        });

        transaction.executeWithoutResult(status -> loyalty.settleAcceptedBenefits(
                outcome(ref, OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED, "dep-" + ref, 2)));
        transaction.executeWithoutResult(status -> loyalty.restoreCancelledWorkshopBenefits(
                ticketOutcome(ref, OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION, "cancel-2", "t-2")));
        BenefitResult result = transaction.execute(status -> loyalty.grantEarnedPoints(
                ticketOutcome(ref, OutcomeKind.WORKSHOP_TICKET_COMPLETED_AND_FULLY_PAID, "done-1", "t-1")));
        assertThat(result.voucherState()).isEqualTo(ComponentState.NONE);
        assertThat(benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, ref).orElseThrow().getMemberId()).isNull();
    }
}
