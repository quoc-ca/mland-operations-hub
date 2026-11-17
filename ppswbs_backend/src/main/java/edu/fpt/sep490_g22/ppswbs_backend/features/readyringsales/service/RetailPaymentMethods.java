package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailPaymentMethod;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;

/** Payment-method rules shared by the preview and acceptance paths (FR-08-041..FR-08-043). */
public final class RetailPaymentMethods {
    /** VNPay checkouts keep the original 15-minute hold. */
    public static final Duration VNPAY_HOLD = Duration.ofMinutes(15);
    /** Cash checkouts are held for collection at the branch for 72 hours. */
    public static final Duration CASH_HOLD = Duration.ofHours(72);

    private RetailPaymentMethods() { }

    /** A missing value keeps the earlier VNPay-only behaviour. */
    public static RetailPaymentMethod parse(String raw) {
        if (raw == null || raw.isBlank()) return RetailPaymentMethod.VNPAY;
        try {
            return RetailPaymentMethod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new RetailConflictException("Payment method is not supported");
        }
    }

    /**
     * Cash is allowed only when every branch order is store pickup. Several branches are allowed: each branch order is
     * paid, cancelled or expired on its own with its FE-06 settlement part (Session 2026-11-13, D-011).
     */
    public static void requireAllowed(RetailPaymentMethod method, Collection<String> fulfilmentMethods) {
        if (method != RetailPaymentMethod.CASH_AT_COUNTER) return;
        if (fulfilmentMethods == null || fulfilmentMethods.isEmpty()) {
            throw new RetailConflictException("Select fulfilment before choosing cash at the counter");
        }
        if (fulfilmentMethods.stream().anyMatch(value -> !"PICKUP".equalsIgnoreCase(value))) {
            throw new RetailConflictException("Cash at the counter is only available for store pickup");
        }
    }

    /** A zero-total checkout is accepted internally and never waits for cash. */
    public static void requirePayableForCash(RetailPaymentMethod method, BigDecimal payable) {
        if (method == RetailPaymentMethod.CASH_AT_COUNTER && (payable == null || payable.signum() <= 0)) {
            throw new RetailConflictException("A zero-total checkout does not need cash payment");
        }
    }

    public static Instant holdDeadline(RetailPaymentMethod method, Instant acceptedAt) {
        return acceptedAt.plus(method == RetailPaymentMethod.CASH_AT_COUNTER ? CASH_HOLD : VNPAY_HOLD);
    }
}
