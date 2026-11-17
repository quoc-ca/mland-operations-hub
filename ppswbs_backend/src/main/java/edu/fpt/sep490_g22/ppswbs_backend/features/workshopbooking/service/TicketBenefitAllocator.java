package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentMode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketCollectionKind;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Pure per-ticket money rules of the multi-ticket cart (003.1 FR-033..FR-036, decision 2026-11-12). Whole VND and
 * whole points only; no I/O. The caller (quote service) supplies the group discount and points already validated by
 * Loyalty (cap from the recorded FE-06 policy); this class only spreads them over tickets and decides what each
 * ticket and the whole group pay at booking time.
 */
public final class TicketBenefitAllocator {

    /** The VNPay minimum applies to the group collection, not to a ticket price (FR-036). */
    public static final long VNPAY_MINIMUM_VND = 5_000;
    /** A ticket collects min(final price, this amount) at booking; the rest is its checkout balance (FR-035). */
    public static final long DEPOSIT_VND = 200_000;

    private TicketBenefitAllocator() {
    }

    /** One cart ticket: its package price and whether the voucher scope covers it. */
    public record TicketInput(long packagePriceVnd, boolean inVoucherScope) {
        public TicketInput {
            if (packagePriceVnd < 0) throw new IllegalArgumentException("A ticket price cannot be negative");
        }
    }

    /** Allocation and booking-time collection of one ticket. */
    public record TicketResult(long packagePriceVnd, long voucherDiscountVnd, long pointsUsed, long pointDiscountVnd,
                               long finalPriceVnd, long collectedAtBookingVnd, TicketCollectionKind collectionKind,
                               long remainingVnd) {
    }

    public record GroupResult(List<TicketResult> tickets, long groupCollectionVnd, GroupPaymentMode paymentMode) {
    }

    /**
     * @param voucherDiscountVnd total voucher discount of the group (0 when no voucher)
     * @param pointsUsed         total whole points applied to the cart after the voucher
     * @param pointValueVnd      VND value of one point in the recorded policy (positive when points are used)
     */
    public static GroupResult allocate(List<TicketInput> tickets, long voucherDiscountVnd, long pointsUsed,
                                       long pointValueVnd) {
        if (tickets == null || tickets.isEmpty()) throw new IllegalArgumentException("A cart needs a ticket");
        if (voucherDiscountVnd < 0 || pointsUsed < 0) throw new IllegalArgumentException("Benefits cannot be negative");
        if (pointsUsed > 0 && pointValueVnd <= 0) throw new IllegalArgumentException("A point needs a positive value");
        int n = tickets.size();

        // FR-033: voucher over in-scope tickets by price, never above a ticket's price.
        long[] voucherWeights = new long[n];
        long[] voucherCaps = new long[n];
        for (int i = 0; i < n; i++) {
            long price = tickets.get(i).packagePriceVnd();
            voucherWeights[i] = tickets.get(i).inVoucherScope() ? price : 0;
            voucherCaps[i] = voucherWeights[i];
        }
        long[] voucher = spread(voucherDiscountVnd, voucherWeights, voucherCaps);

        // FR-034: whole points by post-voucher price; a ticket at 0 gets none; a ticket never spends more than it costs.
        long[] pointWeights = new long[n];
        long[] pointCaps = new long[n];
        for (int i = 0; i < n; i++) {
            pointWeights[i] = tickets.get(i).packagePriceVnd() - voucher[i];
            pointCaps[i] = pointsUsed == 0 ? 0 : pointWeights[i] / pointValueVnd;
        }
        long[] points = spread(pointsUsed, pointWeights, pointCaps);

        // FR-035: per-ticket collection, then FR-036 group mode.
        List<TicketResult> results = new ArrayList<>();
        long groupCollection = 0;
        for (int i = 0; i < n; i++) {
            long price = tickets.get(i).packagePriceVnd();
            long pointDiscount = points[i] * pointValueVnd;
            long finalPrice = price - voucher[i] - pointDiscount;
            long collected;
            TicketCollectionKind kind;
            if (finalPrice == 0) {
                collected = 0;
                kind = TicketCollectionKind.FREE;
            } else if (finalPrice <= DEPOSIT_VND) {
                collected = finalPrice;
                kind = TicketCollectionKind.FULL;
            } else {
                collected = DEPOSIT_VND;
                kind = TicketCollectionKind.DEPOSIT;
            }
            groupCollection += collected;
            results.add(new TicketResult(price, voucher[i], points[i], pointDiscount, finalPrice, collected, kind,
                    finalPrice - collected));
        }

        if (groupCollection == 0) {
            return new GroupResult(results, 0, GroupPaymentMode.INTERNAL_FREE);
        }
        if (groupCollection < VNPAY_MINIMUM_VND) {
            // Deferred to checkout: every paying ticket collects nothing now and keeps its whole price as balance.
            List<TicketResult> deferred = new ArrayList<>();
            for (TicketResult t : results) {
                deferred.add(t.finalPriceVnd() == 0 ? t : new TicketResult(t.packagePriceVnd(), t.voucherDiscountVnd(),
                        t.pointsUsed(), t.pointDiscountVnd(), t.finalPriceVnd(), 0,
                        TicketCollectionKind.DEFERRED_BELOW_VNPAY_MINIMUM, t.finalPriceVnd()));
            }
            return new GroupResult(deferred, 0, GroupPaymentMode.INTERNAL_DEFERRED);
        }
        return new GroupResult(results, groupCollection, GroupPaymentMode.VNPAY);
    }

    /**
     * Largest-remainder split of {@code total} over {@code weights}, never above {@code caps}. Ties go to the larger
     * weight, then the earlier cart position. A ticket that hits its cap is fixed and the rest is re-spread over the
     * others. Throws when {@code total} cannot be placed within the caps.
     */
    public static long[] spread(long total, long[] weights, long[] caps) {
        int n = weights.length;
        long[] result = new long[n];
        if (total == 0) return result;
        boolean[] active = new boolean[n];
        long remaining = total;
        for (int i = 0; i < n; i++) active[i] = weights[i] > 0 && caps[i] > 0;

        while (remaining > 0) {
            BigInteger weightSum = BigInteger.ZERO;
            for (int i = 0; i < n; i++) if (active[i]) weightSum = weightSum.add(BigInteger.valueOf(weights[i]));
            if (weightSum.signum() == 0) {
                throw new IllegalArgumentException("The benefit of " + total + " cannot be placed on these tickets");
            }
            long[] share = new long[n];
            BigInteger[] remainder = new BigInteger[n];
            long placed = 0;
            for (int i = 0; i < n; i++) {
                if (!active[i]) continue;
                BigInteger[] qr = BigInteger.valueOf(remaining).multiply(BigInteger.valueOf(weights[i]))
                        .divideAndRemainder(weightSum);
                share[i] = qr[0].longValueExact();
                remainder[i] = qr[1];
                placed += share[i];
            }
            long leftover = remaining - placed;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) if (active[i]) order.add(i);
            order.sort(Comparator.<Integer, BigInteger>comparing(i -> remainder[i]).reversed()
                    .thenComparing(i -> weights[i], Comparator.reverseOrder())
                    .thenComparing(i -> i));
            for (int k = 0; k < leftover; k++) share[order.get(k)]++;

            boolean capped = false;
            for (int i = 0; i < n; i++) {
                if (active[i] && result[i] + share[i] > caps[i]) {
                    remaining -= caps[i] - result[i];
                    result[i] = caps[i];
                    active[i] = false;
                    capped = true;
                }
            }
            if (!capped) {
                for (int i = 0; i < n; i++) result[i] += share[i];
                remaining = 0;
            }
        }
        return result;
    }

    /** Convenience for tests and callers that need the allocated total. */
    public static long sum(long[] values) {
        return Arrays.stream(values).sum();
    }
}
