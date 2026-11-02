package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;

/**
 * The customer choice. {@code AUTO} is retail only and always allows points; {@code voucherCode}
 * is present only for {@code VOUCHER}; {@code requestedPoints} is a whole number, 0 meaning none.
 */
public record BenefitSelection(SelectionChoice choice, String voucherCode, BigInteger requestedPoints) {

    public BenefitSelection {
        if (choice == null) {
            throw LoyaltyException.invalidField("choice", "A selection is required.");
        }
        requestedPoints = requestedPoints == null ? BigInteger.ZERO : requestedPoints;
        if (requestedPoints.signum() < 0) {
            throw LoyaltyException.invalidField("requestedPoints", "Points must not be negative.");
        }
        boolean hasCode = voucherCode != null && !voucherCode.isBlank();
        if (choice == SelectionChoice.VOUCHER && !hasCode) {
            throw LoyaltyException.invalidField("voucherCode", "A voucher code is required.");
        }
        if (choice != SelectionChoice.VOUCHER && voucherCode != null) {
            throw LoyaltyException.invalidField("voucherCode", "A voucher code is only allowed with a voucher choice.");
        }
    }

    public static BenefitSelection none() {
        return new BenefitSelection(SelectionChoice.NONE, null, BigInteger.ZERO);
    }
}
