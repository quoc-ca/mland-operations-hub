package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /** Code returned while the 006 cart contract is pending; the cart itself stays quotable without benefits. */
    public static final String BENEFITS_PENDING_CODE = "CART_BENEFITS_NOT_AVAILABLE";

    static final TypeReference<List<StoredLine>> STORED_LINES = new TypeReference<>() {
    };

    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopCartPricingService pricing;
    private final WorkshopCartQuoteRepository quotes;
    private final ObjectMapper mapper;

    /** The cart as stored on the quote, so group creation re-reads exactly what was quoted. */
    public record StoredLine(long packageId, List<String> sessionReferences, int participantCount,
                             long pricePerParticipantVnd, String packageName) {
    }

    @Transactional
    public WorkshopCartQuoteResponse quote(String authorizationHeader, WorkshopCartQuoteRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new BookingValidationException("A cart needs at least one line");
        }
        if (request.benefitSelection() != null && request.benefitSelection().requestsBenefit()) {
            throw new BookingCreationRejectedException(BENEFITS_PENDING_CODE,
                    "A voucher or points cannot be applied to a cart yet; quote the cart without them");
        }
        Long buyerMemberAccountId = authorizationHeader == null || authorizationHeader.isBlank()
                ? null
                : authorization.requireMemberOwnership(authorizationHeader, null).accountId();

        List<WorkshopCartPricingService.LineInput> inputs = new ArrayList<>();
        for (WorkshopCartQuoteRequest.Line line : request.lines()) {
            inputs.add(new WorkshopCartPricingService.LineInput(line.packageId(),
                    canonicalUuids(line.sessionReferences()), line.participantCount()));
        }
        WorkshopCartPricingService.PricedCart cart = pricing.price(inputs);

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
                .grossVnd(cart.grossVnd())
                .voucherDiscountVnd(0)
                .pointsUsed(0)
                .pointDiscountVnd(0)
                .finalTotalVnd(finalTotal)
                .groupCollectionVnd(cart.allocation().groupCollectionVnd())
                .paymentMode(cart.allocation().paymentMode())
                .build());
        return response(quote, cart, finalTotal);
    }

    private WorkshopCartQuoteResponse response(WorkshopCartQuote quote, WorkshopCartPricingService.PricedCart cart,
                                               long finalTotal) {
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
                cart.grossVnd(), 0, 0, 0, finalTotal, cart.allocation().groupCollectionVnd(),
                finalTotal - cart.allocation().groupCollectionVnd(), cart.allocation().paymentMode().name());
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
