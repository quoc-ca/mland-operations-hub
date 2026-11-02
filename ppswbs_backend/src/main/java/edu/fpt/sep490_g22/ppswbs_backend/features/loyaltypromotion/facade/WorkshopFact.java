package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Workshop package fact supplied by the booking owner: one package bought once, with every required session the
 * Member selected (a Silverclay booking has two). The package fee covers all sessions; the discount cap never
 * multiplies by participants or sessions. Sessions are kept in a stable order (date, then reference) so the same
 * booking always gives the same quote digest.
 */
public record WorkshopFact(long packageId, int participantCount, BigInteger grossPackageAmount,
                           List<WorkshopSessionFact> sessions) {

    public WorkshopFact {
        if (packageId <= 0 || participantCount <= 0) {
            throw LoyaltyException.invalidField("workshop", "Workshop facts are incomplete.");
        }
        if (grossPackageAmount == null || grossPackageAmount.signum() < 0) {
            throw LoyaltyException.invalidField("workshop", "Package fee must not be negative.");
        }
        if (sessions == null || sessions.isEmpty()) {
            throw LoyaltyException.invalidField("workshop.sessions", "At least one session is required.");
        }
        Set<String> seen = new HashSet<>();
        for (WorkshopSessionFact session : sessions) {
            if (session == null || !seen.add(session.sessionRef())) {
                throw LoyaltyException.invalidField("workshop.sessions", "Sessions must be distinct.");
            }
        }
        sessions = sessions.stream()
                .sorted(Comparator.comparing(WorkshopSessionFact::workshopDate).thenComparing(WorkshopSessionFact::sessionRef))
                .toList();
    }

    /** Convenience for a one-session package. */
    public static WorkshopFact singleSession(long packageId, int participantCount, BigInteger grossPackageAmount,
                                             WorkshopSessionFact session) {
        return new WorkshopFact(packageId, participantCount, grossPackageAmount, List.of(session));
    }
}
