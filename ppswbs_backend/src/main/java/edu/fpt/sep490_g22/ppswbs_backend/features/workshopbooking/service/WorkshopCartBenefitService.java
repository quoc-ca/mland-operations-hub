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

    /**
     * @param eligibleTicketIndexes 0-based positions, in cart order, of the tickets the voucher covers
     * @param maxPoints the most points 006 lets this Member spend on this cart (FR-034); 0 when none may be spent
     * @param minRedeemPoints the smallest redeemable amount of the recorded point policy
     */
    public record CartBenefits(long voucherDiscountVnd, long pointsUsed, long pointValueVnd, long pointDiscountVnd,
                               List<Integer> eligibleTicketIndexes, String termsDigest, long policyRevision,
                               List<String> reasons, long maxPoints, long minRedeemPoints) {

        public static CartBenefits none() {
            return new CartBenefits(0, 0, 0, 0, List.of(), null, 0, List.of(), 0, 0);
        }

        public boolean isEmpty() {
            return voucherDiscountVnd == 0 && pointsUsed == 0;
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

    /** Read-only breakdown of the whole cart; holds no quota and no points. */
    public CartBenefits preview(long memberAccountId, WorkshopCartPricingService.PricedCart cart,
                                BenefitSelection selection) {
        BenefitPreview preview = loyalty.previewBenefits(trusted(memberAccountId, cart, Instant.now()), selection);
        return new CartBenefits(exact(preview.programDiscount()), exact(preview.pointsUsed()),
                exact(preview.pointValue()), exact(preview.pointDiscount()), preview.eligibleLineIndexes(),
                preview.termsDigest(), preview.policyRevisionNo(), preview.reasons(),
                exact(preview.maxPoints()), exact(preview.minRedeemPoints()));
    }

    /**
     * Holds the voucher use and the points for the whole group, inside the caller's transaction. The cart is
     * rebuilt in the same order as the quote, so a changed cart or changed terms surface as a typed failure
     * instead of a silently different discount.
     */
    public AcceptedBenefitRef accept(long memberAccountId, WorkshopCartPricingService.PricedCart cart,
                                     BenefitSelection selection, String groupCode, String quoteRef,
                                     String termsDigest, Instant acceptedAt, Instant holdExpiresAt) {
        AcceptedSourceContext accepted = new AcceptedSourceContext(trusted(memberAccountId, cart, acceptedAt),
                groupCode, 1L, true, acceptedAt, holdExpiresAt, quoteRef, termsDigest);
        return loyalty.reserveAcceptedTransaction(accepted, selection, "create-" + groupCode);
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

    private TrustedSourceContext trusted(long memberAccountId, WorkshopCartPricingService.PricedCart cart,
                                         Instant at) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberAccountId, at, List.of(),
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
