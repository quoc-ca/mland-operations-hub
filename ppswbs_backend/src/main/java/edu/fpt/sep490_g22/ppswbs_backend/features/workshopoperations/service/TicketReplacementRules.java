package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 003.2 FR-034: the per-move rules of a ticket session replacement. One place used both by
 * {@link TicketSessionReplacementService#replace} (apply) and by {@link TicketChangeOptionsService} (listing, 003.3
 * FR-044a), so the list a customer sees cannot drift from what the apply accepts.
 */
@Component
public class TicketReplacementRules {

    /** FR-034: the replacement of a missed session must start within this window after its end. */
    public static final Duration LATE_ARRIVAL_WINDOW = Duration.ofDays(7);

    /** FR-034: a source may be left behind only when the ticket has no attendance there. */
    public RejectionReason checkSource(TicketReplacementType type, WorkshopSession source,
                                       TicketSessionEntitlement entitlement, Instant now) {
        if (source.getScheduledStartAt() == null || source.getScheduledEndAt() == null) {
            return RejectionReason.INVALID_STATE;
        }
        if (entitlement.getCheckedInAt() != null
                || entitlement.getAttendanceState() != AttendanceState.NOT_CHECKED_IN) {
            return RejectionReason.INVALID_REPLACEMENT;
        }
        if (source.getState() == SessionState.ATTENDANCE_FINALIZED) {
            return RejectionReason.INVALID_REPLACEMENT;
        }
        if (type == TicketReplacementType.MLAND) {
            // The Mland case is exactly the disrupted session, so a cancelled or running source is expected.
            return null;
        }
        if (type == TicketReplacementType.CUSTOMER_LATE_ARRIVAL) {
            boolean runningAndNotEnded = source.getState() == SessionState.IN_PROGRESS
                    && source.getScheduledEndAt().isAfter(now);
            boolean future = source.getScheduledStartAt().isAfter(now);
            return runningAndNotEnded || future ? null : RejectionReason.INVALID_REPLACEMENT;
        }
        boolean future = source.getScheduledStartAt().isAfter(now)
                && source.getState() != SessionState.IN_PROGRESS
                && source.getState() != SessionState.CANCELLATION_REQUESTED
                && source.getState() != SessionState.CANCELLED;
        return future ? null : RejectionReason.INVALID_REPLACEMENT;
    }

    /** FR-034: every target must be future, published and customer-selectable. */
    public RejectionReason checkTarget(WorkshopSession target, Instant now) {
        if (target.getScheduledStartAt() == null || !target.getScheduledStartAt().isAfter(now)
                || target.getState() != SessionState.PUBLISHED) {
            return RejectionReason.INVALID_REPLACEMENT;
        }
        return null;
    }

    public boolean isLateArrival(TicketReplacementType type, WorkshopSession source, Instant now) {
        return type == TicketReplacementType.CUSTOMER_LATE_ARRIVAL
                && !source.getScheduledStartAt().isAfter(now);
    }

    /** FR-034: only the missed session's own replacement must fall inside the 7-day window. */
    public RejectionReason checkLateArrivalWindow(TicketReplacementType type, WorkshopSession source,
                                                  WorkshopSession target, Instant now) {
        if (isLateArrival(type, source, now)
                && target.getScheduledStartAt().isAfter(source.getScheduledEndAt().plus(LATE_ARRIVAL_WINDOW))) {
            return RejectionReason.LATE_ARRIVAL_WINDOW_EXPIRED;
        }
        return null;
    }
}
