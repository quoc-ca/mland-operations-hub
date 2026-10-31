package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * One selected session of a booking, as the booking owner verified it: the public session reference (UUID, the same
 * value 003 exposes as a session reference), the branch (account-management location ID) and the workshop date.
 * The reference is stored in canonical lowercase form.
 */
public record WorkshopSessionFact(String sessionRef, long branchId, LocalDate workshopDate) {

    public WorkshopSessionFact {
        sessionRef = canonical(sessionRef);
        if (branchId <= 0 || workshopDate == null) {
            throw LoyaltyException.invalidField("workshop.sessions", "Session facts are incomplete.");
        }
    }

    public static WorkshopSessionFact of(String sessionRef, long branchId, LocalDate workshopDate) {
        return new WorkshopSessionFact(sessionRef, branchId, workshopDate);
    }

    /** A session reference must be a UUID; it is compared in canonical lowercase form. */
    public static String canonical(String sessionRef) {
        try {
            String trimmed = sessionRef == null ? "" : sessionRef.trim();
            UUID parsed = UUID.fromString(trimmed);
            String value = parsed.toString();
            if (!value.equals(trimmed.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("not canonical");
            }
            return value;
        } catch (IllegalArgumentException e) {
            throw LoyaltyException.invalidField("workshop.sessions", "A session reference must be a UUID.");
        }
    }
}
