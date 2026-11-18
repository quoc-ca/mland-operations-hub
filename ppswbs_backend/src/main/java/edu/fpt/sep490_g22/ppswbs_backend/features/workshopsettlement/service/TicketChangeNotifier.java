package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Customer emails for ticket changes (003.3 FR-045b, template 10 of {@code artifacts/workshop-email-templates-draft.md},
 * owner decision 2026-11-18): "changed", "refused" and "expired". It only gathers the schedule and branch text and hands
 * the rest to {@link SettlementCustomerNotifier}. Every method swallows its own failure: the email is a courtesy, never
 * a reason to undo a change (the handoff itself is created in the caller's transaction).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketChangeNotifier {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);

    private final SettlementCustomerNotifier notifier;
    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopBookingFacade booking;

    /** A change was applied (self-approved, approved from the queue, or part of a group change). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applied(String ticketCode, TicketFolioContext ctx, List<UUID> newSessions, long priceIncreaseVnd,
                        String changeReference, String correlation) {
        try {
            List<UUID> old = ctx == null || ctx.sessionReferences() == null ? List.of()
                    : ctx.sessionReferences().stream().map(UUID::fromString).toList();
            // After the change the ticket may keep one session and move the other: show the whole new schedule.
            List<UUID> after = entitlements(ctx).stream().map(SessionEntitlementProjection::sessionReference).toList();
            Facts facts = facts(ctx, old, after.isEmpty() ? newSessions : after);
            notifier.notifyTicketChanged(ticketCode, changeReference, facts.current, facts.other, facts.location,
                    priceIncreaseVnd, correlation);
        } catch (RuntimeException failure) {
            log.warn("Ticket change email skipped: ticketCode={} ({})", ticketCode, failure.getClass().getSimpleName());
        }
    }

    /** A queued request was refused by Staff/Manager; the ticket keeps its schedule. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rejected(SettlementTicketChangeRequest request, String correlation) {
        unchanged(request, correlation, true);
    }

    /** A queued request ran out of time; the ticket keeps its schedule. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void expired(SettlementTicketChangeRequest request, String correlation) {
        unchanged(request, correlation, false);
    }

    private void unchanged(SettlementTicketChangeRequest request, String correlation, boolean rejected) {
        String ticketCode = request.getTicketCode();
        try {
            TicketFolioContext ctx = booking.getTicketFolioContext(ticketCode).orElse(null);
            List<UUID> current = request.getFromSessionReferences() != null && !request.getFromSessionReferences().isBlank()
                    ? uuids(request.getFromSessionReferences())
                    : ctx == null || ctx.sessionReferences() == null ? List.of()
                            : ctx.sessionReferences().stream().map(UUID::fromString).toList();
            Facts facts = facts(ctx, current, uuids(request.getTargetSessionReferences()));
            String reference = request.getRequestReference();
            if (rejected) {
                notifier.notifyTicketChangeRejected(ticketCode, reference, facts.current, facts.other, facts.location,
                        correlation);
            } else {
                notifier.notifyTicketChangeExpired(ticketCode, reference, facts.current, facts.other, facts.location,
                        correlation);
            }
        } catch (RuntimeException failure) {
            log.warn("Ticket change email skipped: ticketCode={} ({})", ticketCode, failure.getClass().getSimpleName());
        }
    }

    private record Facts(String current, String other, String location) {
    }

    /** {@code current} = the first list as text, {@code other} = the second list; location = the branch of either, as Operations names it. */
    private Facts facts(TicketFolioContext ctx, List<UUID> first, List<UUID> second) {
        String location = null;
        for (SessionEntitlementProjection entitlement : entitlements(ctx)) {
            if ((second.contains(entitlement.sessionReference()) || first.contains(entitlement.sessionReference()))
                    && entitlement.locationName() != null
                    && !entitlement.locationName().isBlank()) {
                location = entitlement.locationName();
            }
        }
        return new Facts(describe(first), describe(second), location);
    }

    /** The ticket's sessions as Operations holds them now; empty when unavailable. */
    private List<SessionEntitlementProjection> entitlements(TicketFolioContext ctx) {
        if (ctx == null || ctx.lineReference() == null) return List.of();
        try {
            for (DeliverySafeTicket ticket : operations.ticketProjection(ctx.lineReference()).tickets()) {
                if (ctx.ticketCode().equals(ticket.ticketCode())) return ticket.sessionEntitlements();
            }
        } catch (RuntimeException failure) {
            log.info("Ticket change email: session names unavailable ({})", failure.getClass().getSimpleName());
        }
        return List.of();
    }

    private String describe(List<UUID> sessions) {
        List<String> parts = new ArrayList<>();
        for (UUID session : sessions) {
            SaleEligibilitySnapshot snapshot = operations.saleEligibility(session);
            if (snapshot == null || snapshot.scheduledStart() == null) continue;
            Instant start = snapshot.scheduledStart();
            parts.add(DAY.format(start) + (snapshot.scheduledEnd() == null ? "" : "–" + TIME.format(snapshot.scheduledEnd())));
        }
        return parts.isEmpty() ? "lịch đã chọn" : String.join(", ", parts);
    }

    private static List<UUID> uuids(String csv) {
        return csv == null || csv.isBlank() ? List.of()
                : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
    }
}
