package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Splits one discount over branch orders in proportion to a weight (the amount each branch contributes), never giving
 * a branch more than its cap. Shares are floored; the few VND left by rounding go one by one to the branches with the
 * largest weight that still have room. The shares always add up to the total.
 */
public final class RetailDiscountAllocation {
    private RetailDiscountAllocation() { }

    public static List<BigInteger> split(BigInteger total, List<BigInteger> weights, List<BigInteger> caps) {
        int size = weights.size();
        List<BigInteger> shares = new ArrayList<>();
        for (int index = 0; index < size; index++) shares.add(BigInteger.ZERO);
        if (total == null || total.signum() <= 0 || size == 0) return shares;
        BigInteger weightSum = weights.stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger capSum = caps.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (total.compareTo(capSum) > 0) throw new RetailConflictException("Discount exceeds the order amount");
        List<BigInteger> basis = weightSum.signum() > 0 ? weights : caps;
        BigInteger basisSum = weightSum.signum() > 0 ? weightSum : capSum;
        BigInteger given = BigInteger.ZERO;
        for (int index = 0; index < size; index++) {
            BigInteger share = total.multiply(basis.get(index)).divide(basisSum).min(caps.get(index));
            shares.set(index, share);
            given = given.add(share);
        }
        List<Integer> order = IntStream.range(0, size).boxed()
            .sorted(Comparator.comparing((Integer index) -> basis.get(index)).reversed()).toList();
        BigInteger left = total.subtract(given);
        while (left.signum() > 0) {
            boolean moved = false;
            for (int index : order) {
                if (left.signum() == 0) break;
                BigInteger room = caps.get(index).subtract(shares.get(index));
                if (room.signum() <= 0) continue;
                BigInteger step = room.min(left);
                shares.set(index, shares.get(index).add(step));
                left = left.subtract(step);
                moved = true;
            }
            if (!moved) throw new RetailConflictException("Discount exceeds the order amount");
        }
        return shares;
    }
}
