package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The 003.1 side of the 006 cart contract of 2026-11-12 (006 T098, D-010).
 *
 * <p>003.1 hands 006 the cart as one {@link WorkshopTicketFact} per person, in cart order, and 006 answers
 * with the group discount, the points and — the part 003.1 cannot work out itself — which tickets the voucher
 * covers. 003.1 then does the allocation of FR-033/FR-034 with {@link TicketBenefitAllocator}. It never
 * re-checks terms, re-evaluates scope or invents a split.
 *
 * <p>The ticket reference is 003.1's own and must be the same at preview and at accept, because the terms
 * digest covers the cart: the quote stores the cart line order, and this service rebuilds the same references
 * from it.
 */
@Service
@RequiredArgsConstructor
public class WorkshopCartBenefitService {

    private final LoyaltyFacade loyalty;

    /** A ticket's share of the automatic discount and of the voucher, by 0-based cart position (006 lineDiscounts). */
    public record TicketShare(int ticketIndex, long autoVnd, long voucherVnd) {
    }

    /**
     * @param voucherDiscountVnd the voucher part of the group discount (Session 2026-11-22: the best automatic
     *                           discount always applies and at most one voucher comes on top, 006 FR-015). Built
     *                           without the split it is the whole programme discount, as before.
     * @param autoDiscountVnd the automatic part of the group discount
     * @param ticketShares 006's per-ticket split; empty when 006 gave none, then the discount is spread here
     * @param eligibleTicketIndexes 0-based positions, in cart order, of the tickets either programme covers
     * @param maxPoints the most points 006 lets this Member spend on this cart (FR-034); 0 when none may be spent
     * @param minRedeemPoints the smallest redeemable amount of the recorded point policy
     */
    public record CartBenefits(long voucherDiscountVnd, long pointsUsed, long pointValueVnd, long pointDiscountVnd,
                               List<Integer> eligibleTicketIndexes, String termsDigest, long policyRevision,
                               List<String> reasons, long maxPoints, long minRedeemPoints,
                               long autoDiscountVnd, List<TicketShare> ticketShares,
                               List<BenefitReason> reasonDetails) {

        public CartBenefits {
            ticketShares = ticketShares == null ? List.of() : List.copyOf(ticketShares);
            reasonDetails = reasonDetails == null ? List.of() : List.copyOf(reasonDetails);
        }

        /** Shape before the split: voucherDiscountVnd is the whole programme discount, nothing per ticket. */
        public CartBenefits(long voucherDiscountVnd, long pointsUsed, long pointValueVnd, long pointDiscountVnd,
                            List<Integer> eligibleTicketIndexes, String termsDigest, long policyRevision,
                            List<String> reasons, long maxPoints, long minRedeemPoints) {
            this(voucherDiscountVnd, pointsUsed, pointValueVnd, pointDiscountVnd, eligibleTicketIndexes, termsDigest,
                    policyRevision, reasons, maxPoints, minRedeemPoints, 0, List.of(), List.of());
        }

        public static CartBenefits none() {
            return new CartBenefits(0, 0, 0, 0, List.of(), null, 0, List.of(), 0, 0);
        }

        /** Automatic + voucher: what is stored on the quote and compared with the accepted benefit. */
        public long programDiscountVnd() {
            return Math.addExact(autoDiscountVnd, voucherDiscountVnd);
        }

        public boolean isEmpty() {
            return programDiscountVnd() == 0 && pointsUsed == 0;
        }

        /** A Guest has no wallet: the quote reports no point limits for them, whatever the policy says. */
        public CartBenefits withoutPointLimits() {
            return new CartBenefits(voucherDiscountVnd, pointsUsed, 0, pointDiscountVnd, eligibleTicketIndexes,
                    termsDigest, policyRevision, reasons, 0, 0, autoDiscountVnd, ticketShares, reasonDetails);
        }
    }

    /** Builds the cart exactly as 006 must see it: one ticket per person, in cart order. */
    public List<WorkshopTicketFact> ticketFacts(WorkshopCartPricingService.PricedCart cart) {
        List<WorkshopTicketFact> tickets = new ArrayList<>();
        for (WorkshopCartPricingService.PricedLine line : cart.lines()) {
            List<WorkshopSessionFact> sessions = line.sessions().stream()
                    .map(session -> WorkshopSessionFact.of(session.reference().toString(),
                            session.locationReference(), session.workshopDate()))
                    .toList();
            for (int ordinal = 1; ordinal <= line.participantCount(); ordinal++) {
                tickets.add(new WorkshopTicketFact(ticketRef(line.lineIndex(), ordinal), line.packageId(),
                        BigInteger.valueOf(line.pricePerParticipantVnd()), sessions));
            }
        }
        return List.copyOf(tickets);
    }

    /**
     * Read-only breakdown of the whole cart; holds no quota and no points.
     *
     * @param memberAccountId the buyer, or {@code null} for a Guest, who can only receive an automatic discount
     *                        (006 FR-044) and is sent to 006 as {@link TrustedSourceContext#GUEST}
     */
    public CartBenefits preview(Long memberAccountId, WorkshopCartPricingService.PricedCart cart,
                                BenefitSelection selection) {
        BenefitPreview preview = DependencyGuard.call("Loyalty",
                () -> loyalty.previewBenefits(trusted(memberAccountId, cart, Instant.now()), selection));
        long program = exact(preview.programDiscount());
        long auto = exact(preview.autoDiscount());
        List<TicketShare> shares = preview.lineDiscounts().stream()
                .map(line -> new TicketShare(line.lineIndex(), exact(line.autoDiscount()),
                        exact(line.voucherDiscount())))
                .toList();
        return new CartBenefits(program - auto, exact(preview.pointsUsed()),
                exact(preview.pointValue()), exact(preview.pointDiscount()), preview.eligibleLineIndexes(),
                preview.termsDigest(), preview.policyRevisionNo(), customerReasons(preview),
                exact(preview.maxPoints()), exact(preview.minRedeemPoints()), auto, shares, customerReasonDetails(preview));
    }

    /**
     * 006's reasons without "no automatic discount applies": every cart now asks for the automatic discount, so that
     * reason only says the customer pays the plain price, which {@code appliedDiscount = NONE} already says. The quote
     * reports voucher and point reasons only. The text and detail lists share order and length when the preview comes
     * from Loyalty; when they do not, the text is kept as it is.
     */
    private static List<String> customerReasons(BenefitPreview preview) {
        List<String> text = preview.reasons();
        List<BenefitReason> details = preview.reasonDetails();
        if (details.size() != text.size()) {
            return text;
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < text.size(); i++) {
            if (details.get(i).code() != BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES) {
                kept.add(text.get(i));
            }
        }
        return List.copyOf(kept);
    }

    /** The coded form of {@link #customerReasons}: same reasons, same order, so the screen can translate them. */
    private static List<BenefitReason> customerReasonDetails(BenefitPreview preview) {
        return preview.reasonDetails().stream()
                .filter(detail -> detail.code() != BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES)
                .toList();
    }

    /**
     * Holds the programme discount and the points for the whole group, inside the caller's transaction. A Guest
     * group ({@code memberAccountId == null}) is accepted too (006 D-014): 006 keeps a snapshot without a Member
     * for its automatic discount, holding no voucher and no points. The cart is rebuilt in the same order as the
     * quote, so a changed cart or changed terms surface as a typed failure instead of a silently different discount.
     */
    public AcceptedBenefitRef accept(Long memberAccountId, WorkshopCartPricingService.PricedCart cart,
                                     BenefitSelection selection, String groupCode, String quoteRef,
                                     String termsDigest, Instant acceptedAt, Instant holdExpiresAt) {
        AcceptedSourceContext accepted = new AcceptedSourceContext(trusted(memberAccountId, cart, acceptedAt),
                groupCode, 1L, true, acceptedAt, holdExpiresAt, quoteRef, termsDigest);
        return DependencyGuard.call("Loyalty",
                () -> loyalty.reserveAcceptedTransaction(accepted, selection, "create-" + groupCode));
    }

    /**
     * Spreads the group's programme discount and points over the tickets. When 006 split the discount per ticket
     * (shares) those shares are used as they are; otherwise the whole discount is spread over the covered tickets
     * here, as before the split existed.
     */
    public TicketBenefitAllocator.GroupResult allocate(WorkshopCartPricingService.PricedCart cart,
                                                       long programDiscountVnd, List<Integer> eligibleTicketIndexes,
                                                       List<TicketShare> shares, long pointsUsed,
                                                       long pointValueVnd) {
        if (shares == null || shares.isEmpty()) {
            return TicketBenefitAllocator.allocate(allocatorInputs(cart, eligibleTicketIndexes), programDiscountVnd,
                    pointsUsed, pointValueVnd);
        }
        java.util.Map<Integer, TicketShare> byTicket = new java.util.HashMap<>();
        shares.forEach(share -> byTicket.put(share.ticketIndex(), share));
        List<TicketBenefitAllocator.TicketInput> inputs = new ArrayList<>();
        int flat = 0;
        for (WorkshopCartPricingService.PricedLine line : cart.lines()) {
            for (int ordinal = 1; ordinal <= line.participantCount(); ordinal++) {
                TicketShare share = byTicket.get(flat);
                inputs.add(new TicketBenefitAllocator.TicketInput(line.pricePerParticipantVnd(), share != null,
                        share == null ? 0 : share.autoVnd(), share == null ? 0 : share.voucherVnd()));
                flat++;
            }
        }
        return TicketBenefitAllocator.allocateShares(inputs, pointsUsed, pointValueVnd);
    }

    /** Per-ticket inputs for the allocator, with the voucher scope 006 decided. */
    public List<TicketBenefitAllocator.TicketInput> allocatorInputs(WorkshopCartPricingService.PricedCart cart,
                                                                    List<Integer> eligibleTicketIndexes) {
        List<Integer> eligible = eligibleTicketIndexes == null ? List.of() : eligibleTicketIndexes;
        List<TicketBenefitAllocator.TicketInput> inputs = new ArrayList<>();
        int flat = 0;
        for (WorkshopCartPricingService.PricedLine line : cart.lines()) {
            for (int ordinal = 1; ordinal <= line.participantCount(); ordinal++) {
                inputs.add(new TicketBenefitAllocator.TicketInput(line.pricePerParticipantVnd(),
                        eligible.contains(flat)));
                flat++;
            }
        }
        return inputs;
    }

    private TrustedSourceContext trusted(Long memberAccountId, WorkshopCartPricingService.PricedCart cart,
                                         Instant at) {
        long member = memberAccountId == null ? TrustedSourceContext.GUEST : memberAccountId;
        return new TrustedSourceContext(SourceKind.WORKSHOP, member, at, List.of(),
                WorkshopFact.cart(ticketFacts(cart)), BigInteger.ZERO);
    }

    static String ticketRef(int lineIndex, int participantOrdinal) {
        return "L" + lineIndex + "-T" + participantOrdinal;
    }

    private static long exact(BigInteger value) {
        if (value == null) {
            return 0L;
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException ex) {
            throw new BookingValidationException("A benefit amount is outside the supported VND range");
        }
    }
}
