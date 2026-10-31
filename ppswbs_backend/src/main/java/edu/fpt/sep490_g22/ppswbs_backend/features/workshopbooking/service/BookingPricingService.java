package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import org.springframework.stereotype.Service;

/** Calculates immutable whole-VND invoice amounts for one accepted booking. */
@Service
public class BookingPricingService {
    public FrozenBookingAmounts freeze(long pricePerParticipant, int participantCount, long postPromotionAmount) {
        if (pricePerParticipant < 0 || participantCount < 1 || postPromotionAmount < 0) {
            throw new BookingValidationException("Booking amounts and participant count are invalid");
        }
        final long packageTotal;
        try {
            packageTotal = Math.multiplyExact(pricePerParticipant, (long) participantCount);
        } catch (ArithmeticException ex) {
            throw new BookingValidationException("Package total exceeds the supported VND range");
        }
        if (postPromotionAmount > packageTotal) {
            throw new BookingValidationException("Post-promotion amount cannot exceed package total");
        }
        long deposit = Math.floorDiv(postPromotionAmount, 2) + postPromotionAmount % 2;
        return new FrozenBookingAmounts(pricePerParticipant, packageTotal, postPromotionAmount,
                deposit, postPromotionAmount - deposit);
    }

    public record FrozenBookingAmounts(long pricePerParticipant, long packageTotal,
                                       long postPromotionAmount, long depositAmount,
                                       long remainingAmount) { }
}
