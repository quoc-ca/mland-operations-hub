package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Display code of a branch order, marketplace style: "ML" + order date (yyMMdd, Vietnam time) + the first eight
 * characters of the order id, e.g. ML26111213B6C582. It is derived, never stored, so Member and Staff screens show
 * the same code for the same order and Staff can search by it.
 */
public final class RetailOrderCodes {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private RetailOrderCodes() { }

    public static String of(String orderId, Instant orderedAt) {
        String tail = orderId == null ? "" : orderId.replace("-", "").toUpperCase(Locale.ROOT);
        if (tail.length() > 8) tail = tail.substring(0, 8);
        String day = orderedAt == null ? "000000" : DAY.format(orderedAt.atZone(VIETNAM));
        return "ML" + day + tail;
    }
}
