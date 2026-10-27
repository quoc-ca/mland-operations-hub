package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.time.LocalDate;

/** Workshop package/session fact supplied by the booking owner; the discount cap never multiplies by participants. */
public record WorkshopFact(long packageId, long branchId, LocalDate workshopDate, long sessionId,
                           int participantCount, BigInteger grossPackageAmount) {

    public WorkshopFact {
        if (packageId <= 0 || branchId <= 0 || sessionId <= 0 || participantCount <= 0 || workshopDate == null) {
            throw LoyaltyException.invalidField("workshop", "Workshop facts are incomplete.");
        }
        if (grossPackageAmount == null || grossPackageAmount.signum() < 0) {
            throw LoyaltyException.invalidField("workshop", "Package fee must not be negative.");
        }
    }
}
