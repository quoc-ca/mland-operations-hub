package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the current Operations snapshots of a cart and prices it per ticket (003.1 FR-030, FR-033..FR-036).
 * Used both to quote a cart and to re-read it when the booking group is created, so a quote that drifted is
 * refused instead of booked. Operations stays authoritative on capacity: it rechecks everything atomically
 * when the group reserves, and the {@code remainingCapacity} read here only keeps an obviously full line out
 * of the cart early.
 *
 * <p>A ticket costs one package price regardless of how many required sessions the package has (FR-035).
 * Benefits are not applied here: the group discount and points come from 006 and are 0 until the cart
 * contract lands (see {@code cart-benefit-contract-request.md}).
 */
@Service
@RequiredArgsConstructor
public class WorkshopCartPricingService {

    private final WorkshopOperationsAdapter operations;

    /** One requested cart line, already parsed from the request or from a stored quote. */
    public record LineInput(long packageId, List<UUID> sessionReferences, int participantCount) {
    }

    public record PricedSession(UUID reference, Long locationReference, int ordinal, Instant scheduledStart,
                                Instant scheduledEnd) {
    }

    public record PricedLine(int lineIndex, long packageId, String packageName, long pricePerParticipantVnd,
                             int participantCount, List<PricedSession> sessions) {
    }

    /**
     * @param lines      the cart in cart order, priced from the current published snapshots
     * @param allocation per-ticket allocation and collection, tickets in cart order (line, then ordinal)
     * @param grossVnd   the cart total before any benefit
     */
    public record PricedCart(List<PricedLine> lines, TicketBenefitAllocator.GroupResult allocation, long grossVnd) {

        /** The allocation result of one ticket, addressed by its line and 1-based ordinal within the line. */
        public TicketBenefitAllocator.TicketResult ticket(int lineIndex, int participantOrdinal) {
            int flat = 0;
            for (PricedLine line : lines) {
                if (line.lineIndex() == lineIndex) {
                    return allocation.tickets().get(flat + participantOrdinal - 1);
                }
                flat += line.participantCount();
            }
            throw new IllegalArgumentException("No cart line " + lineIndex);
        }
    }

    /**
     * Validates and prices the cart. Throws {@link BookingValidationException} when the request itself is
     * wrong and {@link BookingConflictException} when a package or session is not currently sellable.
     */
    public PricedCart price(List<LineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BookingValidationException("A cart needs at least one line");
        }
        List<PricedLine> priced = new ArrayList<>();
        List<TicketBenefitAllocator.TicketInput> tickets = new ArrayList<>();
        long gross = 0;
        for (int index = 0; index < lines.size(); index++) {
            LineInput line = lines.get(index);
            PricedLine pricedLine = priceLine(index, line);
            priced.add(pricedLine);
            for (int ordinal = 1; ordinal <= pricedLine.participantCount(); ordinal++) {
                // No voucher scope to honour yet, so every ticket is quoted at its package price.
                tickets.add(new TicketBenefitAllocator.TicketInput(pricedLine.pricePerParticipantVnd(), false));
            }
            try {
                gross = Math.addExact(gross, Math.multiplyExact(pricedLine.pricePerParticipantVnd(),
                        (long) pricedLine.participantCount()));
            } catch (ArithmeticException ex) {
                throw new BookingValidationException("The cart total is outside the supported VND range");
            }
        }
        return new PricedCart(List.copyOf(priced), TicketBenefitAllocator.allocate(tickets, 0, 0, 0), gross);
    }

    private PricedLine priceLine(int index, LineInput line) {
        if (line == null || line.sessionReferences() == null || line.sessionReferences().isEmpty()
                || line.participantCount() < 1) {
            throw new BookingValidationException("Cart line " + index + " needs a package, sessions and participants");
        }
        List<UUID> references = line.sessionReferences();
        if (new HashSet<>(references).size() != references.size()) {
            throw new BookingValidationException("Cart line " + index + " repeats a session");
        }
        PublishedPackageSelectionSnapshot pkg = operations.getPublishedPackageSelection(Long.toString(line.packageId()));
        if (pkg == null || pkg.packageName() == null || pkg.packageName().isBlank()) {
            throw new BookingConflictException("A package in the cart is not currently available");
        }
        if (line.participantCount() < pkg.minParticipants() || line.participantCount() > pkg.maxParticipants()
                || references.size() != pkg.requiredSessionCount()) {
            throw new BookingValidationException(
                    "Cart line " + index + " does not match the published package requirements");
        }
        List<PricedSession> sessions = new ArrayList<>();
        for (UUID reference : references) {
            SaleEligibilitySnapshot session = operations.getSaleEligibility(reference);
            if (session == null || !session.customerSelectable() || session.locationReference() == null
                    || session.workshopDate() == null) {
                throw new BookingConflictException("A session in the cart is not currently available");
            }
            if (session.sessionReference() != null && !reference.equals(session.sessionReference())) {
                throw new BookingConflictException("Workshop Operations returned a mismatched session reference");
            }
            if (session.remainingCapacity() < line.participantCount()) {
                throw new BookingConflictException("A session in the cart no longer has room for the whole line");
            }
            sessions.add(new PricedSession(reference, session.locationReference(), 0, session.scheduledStart(),
                    session.scheduledEnd()));
        }
        List<PricedSession> ordered = new ArrayList<>();
        List<PricedSession> chronological = sessions.stream()
                .sorted(Comparator.comparing(s -> Optional.ofNullable(s.scheduledStart()).orElse(Instant.MAX)))
                .toList();
        for (int i = 0; i < chronological.size(); i++) {
            PricedSession session = chronological.get(i);
            ordered.add(new PricedSession(session.reference(), session.locationReference(), i + 1,
                    session.scheduledStart(), session.scheduledEnd()));
        }
        return new PricedLine(index, line.packageId(), pkg.packageName(), pkg.publicPerParticipantPrice(),
                line.participantCount(), List.copyOf(ordered));
    }
}
