package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;
import java.util.UUID;

/**
 * One session a ticket is entitled to. {@code locationName} is the display name of the session's location, read by
 * Operations so a customer-facing view can say where the session is without reading Operations' location list.
 */
public record SessionEntitlementProjection(
    UUID sessionReference,
    Long locationReference,
    Instant scheduledStart,
    Instant scheduledEnd,
    String locationName
) {
    /** For callers that have no location name. */
    public SessionEntitlementProjection(UUID sessionReference, Long locationReference, Instant scheduledStart, Instant scheduledEnd) {
        this(sessionReference, locationReference, scheduledStart, scheduledEnd, null);
    }
}
