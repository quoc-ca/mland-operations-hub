package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The sessions a ticket could move to, from 003.2's own rules (003.3 FR-044a). Advisory: the apply
 * ({@code replaceTicketSessions}) re-checks everything. 003.3 adds the 24 h window, the allowance and the price.
 */
public record TicketChangeOptions(Status status, List<FromSession> perFromSession, List<Pair> pairs) {

    public enum Status { OK, TICKET_NOT_FOUND, TICKET_NOT_ACTIVE, OTA_TICKET_NOT_SUPPORTED, FROM_SESSION_NOT_OF_TICKET }

    public TicketChangeOptions {
        perFromSession = perFromSession == null ? List.of() : List.copyOf(perFromSession);
        pairs = pairs == null ? List.of() : List.copyOf(pairs);
    }

    public static TicketChangeOptions refused(Status status) {
        return new TicketChangeOptions(status, List.of(), List.of());
    }

    /** One current session of the ticket and the sessions that could replace it (the ticket's other session stays). */
    public record FromSession(UUID sessionReference, Instant start, Instant end, boolean sourceChangeable,
                              String sourceReasonCode, List<Option> options) {
        public FromSession {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /**
     * Only a session with a free seat and reschedule quota left is listed (decision 2026-11-16), so both
     * {@code remainingCapacity} and {@code remainingRescheduleQuota} are at least 1 here.
     */
    public record Option(UUID sessionReference, Instant start, Instant end, Long locationReference,
                         int remainingCapacity, int remainingRescheduleQuota, String reasonCode, String locationName) {
        /** Without the location display name (003.2 FR-044 adds it; consumers built earlier keep compiling). */
        public Option(UUID sessionReference, Instant start, Instant end, Long locationReference,
                      int remainingCapacity, int remainingRescheduleQuota, String reasonCode) {
            this(sessionReference, start, end, locationReference, remainingCapacity, remainingRescheduleQuota,
                    reasonCode, null);
        }
    }

    /** Both sessions of a two-session ticket moving together; each session has a seat and quota left. */
    public record Pair(PairSession first, PairSession second, String reasonCode) {
    }

    public record PairSession(UUID sessionReference, Instant start, Instant end, int remainingCapacity,
                              int remainingRescheduleQuota, Long locationReference, String locationName) {
        public PairSession(UUID sessionReference, Instant start, Instant end, int remainingCapacity,
                           int remainingRescheduleQuota) {
            this(sessionReference, start, end, remainingCapacity, remainingRescheduleQuota, null, null);
        }
    }
}
