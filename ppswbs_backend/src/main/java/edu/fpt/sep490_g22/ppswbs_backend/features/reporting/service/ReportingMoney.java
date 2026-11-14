package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import java.math.BigInteger;

/** Whole-VND amounts for the reporting contract; null represents unknown. */
public final class ReportingMoney {
    private ReportingMoney() { }

    public static String vnd(BigInteger amount) {
        if (amount == null) return null;
        if (amount.signum() < 0) throw new IllegalArgumentException("Reporting amount must be non-negative");
        return amount.toString();
    }

    public static String sumVnd(BigInteger left, BigInteger right) {
        if (left == null || right == null) return null;
        vnd(left);
        vnd(right);
        return vnd(left.add(right));
    }
}
