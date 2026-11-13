package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopCartQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopCartQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quotes a whole cart per ticket (003.1 T092, FR-030, FR-033..FR-037). Guest and Member both quote here.
 * The quote holds no capacity and is not a booking: it records the prices and the booking-time collection
 * the customer was shown, and {@code POST /workshop-booking-groups} re-reads everything before holding.
 *
 * <p>Benefits are refused for now. FE-06 benefits are Member-only, so a Guest cart never had any; a Member
 * cart that asks for a voucher or points needs the 006 cart contract (per-ticket voucher scope) that 003.1
 * requested on 2026-11-12, and 003.1 will not allocate a discount of its own invention into an immutable
 * snapshot. See {@code cart-benefit-contract-request.md}.
 */
@Service
@RequiredArgsConstructor
public class WorkshopCartQuoteService {

    /** FE-06 benefits are Member-only, so a Guest cart asking for a voucher or points is refused. */
    public static final String BENEFITS_NEED_A_MEMBER = "CART_BENEFITS_NEED_A_MEMBER";

    /** Asks 006 for nothing: used to read a Member's point limits for a cart that selected no benefit (FR-034). */
    private static final BenefitSelection NO_BENEFIT = new BenefitSelection(
            edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice.NONE, null,
            java.math.BigInteger.ZERO);

    static final TypeReference<List<StoredLine>> STORED_LINES = new TypeReference<>() {
    };

    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopCartPricingService pricing;
    private final WorkshopCartBenefitService benefits;
    private final WorkshopCartQuoteRepository quotes;
    private final ObjectMapper mapper;

    /** Turns the request's choice into the selection 006 expects. */
    static edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection selectionOf(
            WorkshopCartQuoteRequest.BenefitSelectionRequest requested) {
        String voucherCode = requested.choice() == WorkshopCartQuoteRequest.BenefitSelectionRequest.Choice.VOUCHER
                ? (requested.voucherCode() == null || requested.voucherCode().isBlank()
                        ? null : requested.voucherCode().trim())
                : null;
        String points = requested.requestedPoints() == null || requested.requestedPoints().isBlank()
                ? "0" : requested.requestedPoints();
        return new edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection(
                edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice
                        .valueOf(requested.choice().name()),
                voucherCode, new java.math.BigInteger(points));
    }

    /** The cart as stored on the quote, so group creation re-reads exactly what was quoted. */
    public record StoredLine(long packageId, List<String> sessionReferences, int participantCount,
                             long pricePerParticipantVnd, String packageName) {
    }

    @Transactional
    public WorkshopCartQuoteResponse quote(String authorizationHeader, WorkshopCartQuoteRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new BookingValidationException("A cart needs at least one line");
        }
        boolean wantsBenefit = request.benefitSelection() != null && request.benefitSelection().requestsBenefit();
        Long buyerMemberAccountId = authorizationHeader == null || authorizationHeader.isBlank()
                ? null
                : authorization.requireMemberOwnership(authorizationHeader, null).accountId();
        if (wantsBenefit && buyerMemberAccountId == null) {
            // FE-06 benefits belong to a Member, so a Guest cart simply has none to apply.
            throw new BookingCreationRejectedException(BENEFITS_NEED_A_MEMBER,
                    "A voucher or points can only be used by a signed-in Member");
        }

        List<WorkshopCartPricingService.LineInput> inputs = new ArrayList<>();
        for (WorkshopCartQuoteRequest.Line line : request.lines()) {
            inputs.add(new WorkshopCartPricingService.LineInput(line.packageId(),
                    canonicalUuids(line.sessionReferences()), line.participantCount()));
        }
        WorkshopCartPricingService.PricedCart cart = pricing.price(inputs);

        // 006 decides the discount, the points and which tickets the voucher covers; 003.1 spreads them.
        BenefitSelection selection = wantsBenefit ? selectionOf(request.benefitSelection()) : null;
        WorkshopCartBenefitService.CartBenefits applied = selection == null
                ? WorkshopCartBenefitService.CartBenefits.none()
                : benefits.preview(buyerMemberAccountId, cart, selection);
        // A Member sees the point cap, the smallest redeemable amount, the point value and 006's reasons even
        // before choosing anything (FR-034), because the evaluator refuses an amount outside the range instead of
        // trimming it. This extra preview is read-only: it holds no voucher and no points, and it is not stored on
        // the quote, so a cart with no benefit keeps an empty benefit snapshot.
        WorkshopCartBenefitService.CartBenefits shown = selection != null || buyerMemberAccountId == null
                ? applied
                : benefits.preview(buyerMemberAccountId, cart, NO_BENEFIT);
        if (!applied.isEmpty()) {
            cart = cart.withAllocation(TicketBenefitAllocator.allocate(
                    benefits.allocatorInputs(cart, applied.eligibleTicketIndexes()), applied.voucherDiscountVnd(),
                    applied.pointsUsed(), applied.pointValueVnd()));
        }

        List<StoredLine> stored = cart.lines().stream()
                .map(line -> new StoredLine(line.packageId(),
                        line.sessions().stream().map(s -> s.reference().toString()).toList(),
                        line.participantCount(), line.pricePerParticipantVnd(), line.packageName()))
                .toList();
        long finalTotal = cart.allocation().tickets().stream()
                .mapToLong(TicketBenefitAllocator.TicketResult::finalPriceVnd).sum();
        WorkshopCartQuote quote = quotes.save(WorkshopCartQuote.builder()
                .quoteRef(UUID.randomUUID().toString())
                .buyerMemberAccountId(buyerMemberAccountId)
                .linesJson(write(stored))
                .benefitSelectionJson(selection == null ? null : write(selection))
                .eligibleTicketIndexesJson(write(applied.eligibleTicketIndexes()))
                .grossVnd(cart.grossVnd())
                .voucherDiscountVnd(applied.voucherDiscountVnd())
                .pointsUsed(applied.pointsUsed())
                .pointDiscountVnd(applied.pointDiscountVnd())
                .pointValueVnd(applied.pointValueVnd())
                .termsDigest(applied.termsDigest())
                .loyaltyPolicyRevision(applied.policyRevision() == 0 ? null : applied.policyRevision())
                .finalTotalVnd(finalTotal)
                .groupCollectionVnd(cart.allocation().groupCollectionVnd())
                .paymentMode(cart.allocation().paymentMode())
                .build());
        return response(quote, cart, finalTotal, shown);
    }

    private WorkshopCartQuoteResponse response(WorkshopCartQuote quote, WorkshopCartPricingService.PricedCart cart,
                                               long finalTotal, WorkshopCartBenefitService.CartBenefits shown) {
        List<WorkshopCartQuoteResponse.QuotedLine> lines = new ArrayList<>();
        for (WorkshopCartPricingService.PricedLine line : cart.lines()) {
            List<WorkshopCartQuoteResponse.QuotedSession> sessions = line.sessions().stream()
                    .map(s -> new WorkshopCartQuoteResponse.QuotedSession(s.reference(), s.locationReference(),
                            s.ordinal(), s.scheduledStart(), s.scheduledEnd()))
                    .toList();
            List<WorkshopCartQuoteResponse.QuotedTicket> tickets = new ArrayList<>();
            for (int ordinal = 1; ordinal <= line.participantCount(); ordinal++) {
                TicketBenefitAllocator.TicketResult ticket = cart.ticket(line.lineIndex(), ordinal);
                tickets.add(new WorkshopCartQuoteResponse.QuotedTicket(ordinal, ticket.packagePriceVnd(),
                        ticket.voucherDiscountVnd(), ticket.pointsUsed(), ticket.pointDiscountVnd(),
                        ticket.finalPriceVnd(), ticket.collectedAtBookingVnd(), ticket.collectionKind().name(),
                        ticket.remainingVnd()));
            }
            lines.add(new WorkshopCartQuoteResponse.QuotedLine(line.lineIndex(), line.packageId(), line.packageName(),
                    line.pricePerParticipantVnd(), line.participantCount(), sessions, List.copyOf(tickets)));
        }
        return new WorkshopCartQuoteResponse(quote.getQuoteRef(), quote.getExpiresAt(), List.copyOf(lines),
                cart.grossVnd(), quote.getVoucherDiscountVnd(), quote.getPointsUsed(), quote.getPointDiscountVnd(),
                finalTotal, shown.maxPoints(), shown.minRedeemPoints(), shown.pointValueVnd(),
                cart.allocation().groupCollectionVnd(),
                finalTotal - cart.allocation().groupCollectionVnd(), cart.allocation().paymentMode().name(),
                shown.reasons());
    }

    static List<UUID> canonicalUuids(List<String> values) {
        List<UUID> parsed = new ArrayList<>();
        for (String value : values) {
            try {
                UUID uuid = UUID.fromString(value);
                if (!uuid.toString().equals(value)) {
                    throw new IllegalArgumentException();
                }
                parsed.add(uuid);
            } catch (RuntimeException ex) {
                throw new BookingValidationException("sessionReferences must be canonical UUIDs");
            }
        }
        return parsed;
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not persist the cart quote", ex);
        }
    }
}
