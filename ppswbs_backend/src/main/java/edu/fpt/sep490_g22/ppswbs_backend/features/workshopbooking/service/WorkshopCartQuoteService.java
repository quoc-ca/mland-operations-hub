package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopCartQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopCartQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quotes a whole cart per ticket (003.1 T092, FR-030, FR-033..FR-037). Guest and Member both quote here.
 * The quote holds no capacity and is not a booking: it records the prices and the booking-time collection
 * the customer was shown, and {@code POST /workshop-booking-groups} re-reads everything before holding.
 *
 * <p>Every cart asks 006 for its benefits (003.1 T105, 006 FR-044/FR-015). A customer who names no voucher gets the
 * largest automatic workshop discount, Guest included; a Member who names a voucher gets that voucher instead and no
 * automatic discount. Vouchers and points stay Member-only, so a Guest asking for either is refused. The group
 * discount is spread per ticket by {@link TicketBenefitAllocator} exactly as a voucher's is (FR-033), and points come
 * off last (FR-034).
 */
@Service
@RequiredArgsConstructor
public class WorkshopCartQuoteService {

    /** FE-06 vouchers and points are Member-only, so a Guest cart asking for either is refused. */
    public static final String BENEFITS_NEED_A_MEMBER = "CART_BENEFITS_NEED_A_MEMBER";

    static final TypeReference<List<StoredLine>> STORED_LINES = new TypeReference<>() {
    };

    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopCartPricingService pricing;
    private final WorkshopCartBenefitService benefits;
    private final WorkshopCartQuoteRepository quotes;
    private final ObjectMapper mapper;

    /**
     * Turns the request's choice into the selection 006 expects. A voucher replaces the automatic discount (006
     * FR-015); anything else, including no selection at all, asks for the largest automatic discount, which always
     * allows points.
     */
    static BenefitSelection selectionOf(WorkshopCartQuoteRequest.BenefitSelectionRequest requested) {
        if (requested == null) {
            return new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO);
        }
        boolean voucher = requested.choice() == WorkshopCartQuoteRequest.BenefitSelectionRequest.Choice.VOUCHER;
        String voucherCode = voucher
                ? (requested.voucherCode() == null || requested.voucherCode().isBlank()
                        ? null : requested.voucherCode().trim())
                : null;
        String points = requested.requestedPoints() == null || requested.requestedPoints().isBlank()
                ? "0" : requested.requestedPoints();
        return new BenefitSelection(voucher ? SelectionChoice.VOUCHER : SelectionChoice.AUTO, voucherCode,
                new BigInteger(points));
    }

    /** Which programme discount the quote applied, so the screen never has to guess (T105). */
    static WorkshopCartQuoteResponse.AppliedDiscount appliedDiscountOf(BenefitSelection selection,
                                                                    WorkshopCartBenefitService.CartBenefits applied) {
        if (applied.voucherDiscountVnd() == 0) {
            return WorkshopCartQuoteResponse.AppliedDiscount.NONE;
        }
        return selection.choice() == SelectionChoice.VOUCHER
                ? WorkshopCartQuoteResponse.AppliedDiscount.VOUCHER
                : WorkshopCartQuoteResponse.AppliedDiscount.AUTO;
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
        boolean wantsMemberBenefit = request.benefitSelection() != null
                && request.benefitSelection().requestsBenefit();
        Long buyerMemberAccountId = authorizationHeader == null || authorizationHeader.isBlank()
                ? null
                : authorization.requireMemberOwnership(authorizationHeader, null).accountId();
        if (wantsMemberBenefit && buyerMemberAccountId == null) {
            // Vouchers and points belong to a Member; only the automatic discount reaches a Guest (006 FR-044).
            throw new BookingCreationRejectedException(BENEFITS_NEED_A_MEMBER,
                    "A voucher or points can only be used by a signed-in Member");
        }

        List<WorkshopCartPricingService.LineInput> inputs = new ArrayList<>();
        for (WorkshopCartQuoteRequest.Line line : request.lines()) {
            inputs.add(new WorkshopCartPricingService.LineInput(line.packageId(),
                    canonicalUuids(line.sessionReferences()), line.participantCount()));
        }
        WorkshopCartPricingService.PricedCart cart = pricing.price(inputs);

        // 006 decides the discount, the points and which tickets the programme covers; 003.1 spreads them. The same
        // read-only preview gives a Member the point cap, the smallest redeemable amount and the point value before
        // they choose anything (FR-034).
        BenefitSelection selection = selectionOf(request.benefitSelection());
        WorkshopCartBenefitService.CartBenefits applied = benefits.preview(buyerMemberAccountId, cart, selection);
        if (buyerMemberAccountId == null) {
            applied = applied.withoutPointLimits();
        }
        // Only a selection that changes the price is reserved at confirmation. A voucher or points the Member asked
        // for are kept even when refused here, so the strict accept reports the refusal instead of booking silently
        // at full price; a cart that got no automatic discount keeps an empty benefit snapshot.
        boolean reserves = wantsMemberBenefit || !applied.isEmpty();
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
                .benefitSelectionJson(reserves ? write(selection) : null)
                .eligibleTicketIndexesJson(write(reserves ? applied.eligibleTicketIndexes() : List.of()))
                .grossVnd(cart.grossVnd())
                .voucherDiscountVnd(applied.voucherDiscountVnd())
                .pointsUsed(applied.pointsUsed())
                .pointDiscountVnd(applied.pointDiscountVnd())
                .pointValueVnd(reserves ? applied.pointValueVnd() : 0)
                .termsDigest(reserves ? applied.termsDigest() : null)
                .loyaltyPolicyRevision(!reserves || applied.policyRevision() == 0 ? null : applied.policyRevision())
                .finalTotalVnd(finalTotal)
                .groupCollectionVnd(cart.allocation().groupCollectionVnd())
                .paymentMode(cart.allocation().paymentMode())
                .build());
        return response(quote, cart, finalTotal, applied, appliedDiscountOf(selection, applied));
    }

    private WorkshopCartQuoteResponse response(WorkshopCartQuote quote, WorkshopCartPricingService.PricedCart cart,
                                               long finalTotal, WorkshopCartBenefitService.CartBenefits shown,
                                               WorkshopCartQuoteResponse.AppliedDiscount appliedDiscount) {
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
                shown.reasons(), appliedDiscount.name());
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
