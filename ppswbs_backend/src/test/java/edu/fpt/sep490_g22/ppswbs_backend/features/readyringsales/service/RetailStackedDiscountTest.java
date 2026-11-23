package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LineDiscount;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CartLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuoteBranch;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FE-06 stacks the automatic discount with one voucher (Session 2026-11-22). FE-08 spreads the discount over branch
 * orders with FE-06's per-line shares. Example of the FE-06 note: line A 600,000 and B 400,000 both get 10% off
 * automatically; a 20% voucher (cap 100,000, minimum 300,000) covers only B.
 */
class RetailStackedDiscountTest {
    private static final BigInteger A = BigInteger.valueOf(600_000);
    private static final BigInteger B = BigInteger.valueOf(400_000);

    @Test
    void eachBranchGetsTheSumOfItsLinesShares() {
        var quote = quoteWithTwoBranches();
        var preview = preview(BigInteger.valueOf(172_000), BigInteger.valueOf(100_000), BigInteger.valueOf(72_000),
            List.of(line(0, 60_000, 0), line(1, 40_000, 72_000)));

        RetailCheckoutQuotePreviewService.allocateDiscounts(quote, cartLines(), sourceLines(), preview);

        assertThat(quote.getBranches().get(0).getDiscountAmount()).isEqualTo(BigInteger.valueOf(60_000));
        assertThat(quote.getBranches().get(1).getDiscountAmount()).isEqualTo(BigInteger.valueOf(112_000));
    }

    @Test
    void sharesThatDoNotAddUpFallBackToTheProportionalSplit() {
        var quote = quoteWithTwoBranches();
        var preview = preview(BigInteger.valueOf(172_000), BigInteger.valueOf(100_000), BigInteger.valueOf(72_000),
            List.of(line(0, 60_000, 0), line(1, 40_000, 70_000)));

        RetailCheckoutQuotePreviewService.allocateDiscounts(quote, cartLines(), sourceLines(), preview);

        assertThat(quote.getBranches().get(0).getDiscountAmount()).isEqualTo(BigInteger.valueOf(103_200));
        assertThat(quote.getBranches().get(1).getDiscountAmount()).isEqualTo(BigInteger.valueOf(68_800));
    }

    @Test
    void returnsTheTwoPartsOnlyWhenTheyAddUpToTheProgrammeDiscount() {
        var split = preview(BigInteger.valueOf(172_000), BigInteger.valueOf(100_000), BigInteger.valueOf(72_000), List.of());
        assertThat(RetailCheckoutQuotePreviewService.discountParts(split)).containsExactly("100000", "72000");

        var unknown = preview(BigInteger.valueOf(172_000), BigInteger.ZERO, BigInteger.ZERO, List.of());
        assertThat(RetailCheckoutQuotePreviewService.discountParts(unknown)).containsExactly(null, null);
    }

    @Test
    void aRequestedVoucherCountsOnlyWhenFe06AppliedThatCode() {
        var withVoucher = preview(BigInteger.valueOf(172_000), BigInteger.valueOf(100_000), BigInteger.valueOf(72_000), List.of());
        assertThat(RetailCheckoutQuotePreviewService.appliedCode(withVoucher, " vc20 ")).isTrue();
        assertThat(RetailCheckoutQuotePreviewService.appliedCode(withVoucher, "OTHER")).isFalse();
    }

    private static RetailCheckoutQuote quoteWithTwoBranches() {
        Instant now = Instant.parse("2026-11-23T02:00:00Z");
        var quote = new RetailCheckoutQuote(1L, "cart", 1L, "VOUCHER", "VC20", BigInteger.ZERO, "d", "VND",
            A.add(B), BigInteger.ZERO, BigInteger.valueOf(172_000), BigInteger.ZERO, BigInteger.valueOf(828_000), now,
            now.plusSeconds(900));
        quote.addBranch(new RetailCheckoutQuoteBranch(quote, 1L, "PICKUP", null, BigInteger.ZERO, A));
        quote.addBranch(new RetailCheckoutQuoteBranch(quote, 2L, "PICKUP", null, BigInteger.ZERO, B));
        return quote;
    }

    private static List<CartLine> cartLines() {
        CartLine a = mock(CartLine.class);
        when(a.getLocationId()).thenReturn(1L);
        CartLine b = mock(CartLine.class);
        when(b.getLocationId()).thenReturn(2L);
        return List.of(a, b);
    }

    private static List<SourceLine> sourceLines() {
        return List.of(new SourceLine(11L, 111L, 1L, A, 1L), new SourceLine(12L, 112L, 1L, B, 2L));
    }

    private static LineDiscount line(int index, long auto, long voucher) {
        return new LineDiscount(index, BigInteger.valueOf(auto), BigInteger.valueOf(voucher));
    }

    private static BenefitPreview preview(BigInteger programDiscount, BigInteger auto, BigInteger voucher,
                                          List<LineDiscount> lines) {
        BigInteger after = A.add(B).subtract(programDiscount);
        return new BenefitPreview(A.add(B), A.add(B), programDiscount, after, BigInteger.ZERO, BigInteger.ZERO,
            BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, after, after, 9L, 1L, "VC20", 1L, true, List.of(), "d",
            List.of(), List.of(0, 1), 8L, 1L, auto, voucher, lines);
    }
}
