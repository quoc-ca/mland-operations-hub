package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefitPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SettlementPart;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopTicketFact;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits the accepted benefit of a retail checkout over its settlement parts (Session 2026-11-12, D-011). Pure and
 * deterministic, so the same checkout always gives the same shares.
 *
 * <ul>
 *   <li>Programme discount: over the part's lines the programme discounts (the preview's eligible lines), in
 *       proportion to their amount. A part without such lines gets no programme discount.</li>
 *   <li>Points used and point discount: in proportion to each part's amount after the programme discount.</li>
 *   <li>Earned points (policy recorded at acceptance): in proportion to each part's earn base.</li>
 * </ul>
 * Every split is largest remainder in whole units (VND or points); ties go to the larger weight, then to the earlier
 * part. Shares add up exactly to the checkout totals and no part goes below zero.
 */
public final class BenefitPartAllocator {

    private BenefitPartAllocator() {
    }

    public static List<AcceptedBenefitPart.Share> allocate(List<SourceLine> lines, List<SettlementPart> parts,
                                                           List<Integer> eligibleLines, BigInteger programDiscount,
                                                           BigInteger pointsUsed, BigInteger pointDiscount,
                                                           BigInteger earnedPoints) {
        Set<Integer> eligible = new HashSet<>(eligibleLines);
        int n = parts.size();
        List<BigInteger> gross = new ArrayList<>(n);
        List<BigInteger> eligibleAmount = new ArrayList<>(n);
        for (SettlementPart part : parts) {
            BigInteger g = BigInteger.ZERO;
            BigInteger e = BigInteger.ZERO;
            for (int index : part.lineIndexes()) {
                BigInteger amount = lines.get(index).lineAmount();
                g = g.add(amount);
                if (eligible.contains(index)) {
                    e = e.add(amount);
                }
            }
            gross.add(g);
            eligibleAmount.add(e);
        }
        List<BigInteger> discount = largestRemainder(programDiscount, eligibleAmount);
        List<BigInteger> net = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            net.add(gross.get(i).subtract(discount.get(i)));
        }
        List<BigInteger> points = largestRemainder(pointsUsed, net);
        List<BigInteger> pointMoney = largestRemainder(pointDiscount, net);
        List<BigInteger> earnBase = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            earnBase.add(net.get(i).subtract(pointMoney.get(i)));
        }
        List<BigInteger> earn = largestRemainder(earnedPoints, earnBase);
        List<AcceptedBenefitPart.Share> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (discount.get(i).compareTo(eligibleAmount.get(i)) > 0 || earnBase.get(i).signum() < 0) {
                throw new IllegalStateException("A part share exceeds the part amount");
            }
            shares.add(new AcceptedBenefitPart.Share(parts.get(i).partRef(), gross.get(i), discount.get(i),
                    points.get(i), pointMoney.get(i), earnBase.get(i), earn.get(i)));
        }
        return shares;
    }

    /**
     * Workshop cart (Session 2026-11-12, D-012): one share per ticket, with the same rules 003.1 uses so both sides get
     * the same numbers (003.1 FR-033/FR-034): the voucher discount over in-scope tickets by ticket price; points as
     * whole points by the price after the voucher, never more points than a ticket can absorb; the point discount of a
     * ticket is its points times the recorded point value; earned points (group total under the recorded policy) by
     * each ticket's earn base. Largest remainder throughout, ties to the higher weight, then cart order.
     */
    public static List<AcceptedBenefitPart.Share> allocateTickets(List<WorkshopTicketFact> tickets,
                                                                  List<Integer> eligibleTickets,
                                                                  BigInteger programDiscount, BigInteger pointsUsed,
                                                                  BigInteger pointValue, BigInteger earnedPoints) {
        Set<Integer> eligible = new HashSet<>(eligibleTickets);
        int n = tickets.size();
        List<BigInteger> gross = new ArrayList<>(n);
        List<BigInteger> inScope = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigInteger price = tickets.get(i).ticketPrice();
            gross.add(price);
            inScope.add(eligible.contains(i) ? price : BigInteger.ZERO);
        }
        List<BigInteger> discount = largestRemainder(programDiscount, inScope);
        List<BigInteger> net = new ArrayList<>(n);
        List<BigInteger> caps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigInteger after = gross.get(i).subtract(discount.get(i));
            net.add(after);
            caps.add(after.divide(pointValue));
        }
        List<BigInteger> points = largestRemainderCapped(pointsUsed, net, caps);
        List<BigInteger> earnBase = new ArrayList<>(n);
        List<BigInteger> pointMoney = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigInteger money = points.get(i).multiply(pointValue);
            pointMoney.add(money);
            earnBase.add(net.get(i).subtract(money));
        }
        List<BigInteger> earn = largestRemainder(earnedPoints, earnBase);
        List<AcceptedBenefitPart.Share> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (earnBase.get(i).signum() < 0) {
                throw new IllegalStateException("A ticket share exceeds the ticket price");
            }
            shares.add(new AcceptedBenefitPart.Share(tickets.get(i).ticketRef(), gross.get(i), discount.get(i),
                    points.get(i), pointMoney.get(i), earnBase.get(i), earn.get(i)));
        }
        return shares;
    }

    /**
     * As {@link #largestRemainder} but no share exceeds its cap: a share over its cap is fixed at the cap and the rest
     * is split again over the others. The caps must add up to at least {@code total}.
     */
    static List<BigInteger> largestRemainderCapped(BigInteger total, List<BigInteger> weights, List<BigInteger> caps) {
        int n = weights.size();
        BigInteger[] result = new BigInteger[n];
        boolean[] fixed = new boolean[n];
        BigInteger left = total;
        while (true) {
            List<Integer> open = new ArrayList<>();
            List<BigInteger> openWeights = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!fixed[i]) {
                    open.add(i);
                    openWeights.add(weights.get(i));
                }
            }
            if (open.isEmpty()) {
                if (left.signum() != 0) {
                    throw new IllegalStateException("The caps cannot absorb " + total);
                }
                break;
            }
            List<BigInteger> split = largestRemainder(left, openWeights);
            boolean over = false;
            for (int k = 0; k < open.size(); k++) {
                int i = open.get(k);
                if (split.get(k).compareTo(caps.get(i)) > 0) {
                    result[i] = caps.get(i);
                    fixed[i] = true;
                    left = left.subtract(caps.get(i));
                    over = true;
                }
            }
            if (!over) {
                for (int k = 0; k < open.size(); k++) {
                    result[open.get(k)] = split.get(k);
                }
                break;
            }
        }
        return java.util.Arrays.asList(result);
    }

    /**
     * {@code total} split in proportion to {@code weights}, whole units, largest remainder (ties: larger weight, then
     * earlier index). A zero total gives zeros; a positive total needs a positive weight sum.
     */
    static List<BigInteger> largestRemainder(BigInteger total, List<BigInteger> weights) {
        int n = weights.size();
        List<BigInteger> shares = new ArrayList<>(n);
        if (total.signum() == 0) {
            for (int i = 0; i < n; i++) {
                shares.add(BigInteger.ZERO);
            }
            return shares;
        }
        BigInteger sum = weights.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (total.signum() < 0 || sum.signum() <= 0) {
            throw new IllegalStateException("Nothing to split " + total + " over");
        }
        List<BigInteger> remainders = new ArrayList<>(n);
        BigInteger given = BigInteger.ZERO;
        for (BigInteger weight : weights) {
            BigInteger[] qr = total.multiply(weight).divideAndRemainder(sum);
            shares.add(qr[0]);
            remainders.add(qr[1]);
            given = given.add(qr[0]);
        }
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer, BigInteger>comparing(remainders::get).reversed()
                .thenComparing(Comparator.<Integer, BigInteger>comparing(weights::get).reversed())
                .thenComparing(Comparator.naturalOrder()));
        int left = total.subtract(given).intValueExact();
        for (int k = 0; k < left; k++) {
            int i = order.get(k);
            if (remainders.get(i).signum() == 0) {
                throw new IllegalStateException("Largest remainder ran out of fractional shares");
            }
            shares.set(i, shares.get(i).add(BigInteger.ONE));
        }
        return shares;
    }
}
