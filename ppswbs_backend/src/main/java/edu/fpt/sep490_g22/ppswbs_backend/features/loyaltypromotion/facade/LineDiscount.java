package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;

/**
 * The programme discount of one source line (Session 2026-11-22, FR-047): a retail source line, or a ticket of a
 * workshop cart, by its zero-based index. {@code autoDiscount} is the line's share of the automatic discount,
 * {@code voucherDiscount} its share of the voucher; both are whole VND, and over all lines they add up to
 * {@link BenefitPreview#autoDiscount()} and {@link BenefitPreview#voucherDiscount()}. The order owner uses these
 * shares as they are instead of splitting {@code programDiscount} itself.
 */
public record LineDiscount(int lineIndex, BigInteger autoDiscount, BigInteger voucherDiscount) {

    public BigInteger total() {
        return autoDiscount.add(voucherDiscount);
    }
}
