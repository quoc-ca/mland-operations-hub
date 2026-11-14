package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.EquivalentReplacementSessions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 003.3 T112, FR-043, US9 (decided 2026-11-14): turns a 003.2 session-cancellation handoff into one Mland remedy
 * per affected ticket. Only tickets that hold the cancelled session are touched (a two-session ticket losing one
 * session is handled whole); each gets an offer of every equivalent session 003.2 finds, or the 100% bank refund at
 * once when there is none. Idempotent per ticket and cancelled session, so a redelivered handoff changes nothing.
 * <p>
 * {@code affectedBookingReferences} are 003.2 booking-line references (one per cart line), so each is expanded to
 * its tickets through the Operations projection and mapped to its {@code ticketCode} through 003.1.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionCancellationRemedyService {

    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopBookingSettlementAdapter booking;
    private final TicketMlandRemedyService remedies;
    private final SettlementCustomerNotifier notifier;

    /** @return the number of tickets that now have a remedy from this handoff */
    @Transactional
    public int openRemedies(String cancelledSessionReference, List<String> affectedBookingReferences,
                            String correlationReference) {
        UUID cancelled;
        try {
            cancelled = UUID.fromString(cancelledSessionReference.trim());
        } catch (RuntimeException invalid) {
            log.warn("Mland remedies skipped: the cancelled session reference is not a session UUID");
            return 0;
        }
        String sessionReference = cancelled.toString();
        Set<String> handled = new LinkedHashSet<>();
        int opened = 0;
        for (String bookingReference : new LinkedHashSet<>(affectedBookingReferences)) {
            for (DeliverySafeTicket ticket : operations.ticketProjection(bookingReference).tickets()) {
                boolean holdsCancelled = ticket.sessionEntitlements().stream()
                        .anyMatch(entitlement -> cancelled.equals(entitlement.sessionReference()));
                if (!holdsCancelled) {
                    continue;
                }
                TicketBookingRef ref = booking
                        .findTicketBookingByOperationsReference(ticket.ticketReference().toString()).orElse(null);
                if (ref == null || !handled.add(ref.ticketCode())) {
                    continue;
                }
                if (!remedies.canOpenRemedy(ref.ticketCode(), sessionReference)) {
                    log.info("Mland remedy skipped, the ticket folio is not open: ticketCode={}", ref.ticketCode());
                    continue;
                }
                EquivalentReplacementSessions found =
                        operations.equivalentReplacementSessions(ticket.ticketReference(), cancelled);
                if (found.candidates().isEmpty()) {
                    remedies.openDirectRefund(ref.ticketCode(), sessionReference, correlationReference);
                } else {
                    remedies.createOffer(ref.ticketCode(), sessionReference,
                            found.candidates().stream()
                                    .map(candidate -> new TicketMlandRemedyService.ReplacementOption(
                                            candidate.sessionReference().toString(), candidate.startAt(),
                                            candidate.endAt()))
                                    .toList(),
                            correlationReference);
                }
                // FR-043: the recipient is only told the session was cancelled; the buyer chooses.
                notifier.notifyMlandRemedyRecipientOf(ref.ticketCode(), correlationReference);
                opened++;
            }
        }
        log.info("Mland remedies opened for cancelled session {}: {} ticket(s)", sessionReference, opened);
        return opened;
    }
}
