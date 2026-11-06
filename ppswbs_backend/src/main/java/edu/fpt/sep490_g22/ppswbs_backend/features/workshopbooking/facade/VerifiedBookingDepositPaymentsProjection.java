package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.util.List;

public record VerifiedBookingDepositPaymentsProjection(List<VerifiedBookingPayment> payments) {
    public VerifiedBookingDepositPaymentsProjection {
        payments = payments == null ? List.of() : List.copyOf(payments);
    }
}
