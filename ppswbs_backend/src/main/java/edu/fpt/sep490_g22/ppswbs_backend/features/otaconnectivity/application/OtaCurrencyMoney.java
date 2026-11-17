package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import java.math.BigDecimal;
import java.util.Currency;

/** Exact ISO 4217 decimal/minor-unit conversion backed by the JDK currency registry. */
public final class OtaCurrencyMoney {
    private OtaCurrencyMoney() {}

    public static long toMinor(String amount, String currencyCode) {
        Currency currency = currency(currencyCode);
        try {
            return new BigDecimal(amount).movePointRight(currency.getDefaultFractionDigits()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Amount is not exactly representable in the currency minor unit");
        }
    }

    public static BigDecimal fromMinor(long amountMinor, String currencyCode) {
        Currency currency = currency(currencyCode);
        return BigDecimal.valueOf(amountMinor, currency.getDefaultFractionDigits()).stripTrailingZeros();
    }

    public static int fractionDigits(String currencyCode) {
        return currency(currencyCode).getDefaultFractionDigits();
    }

    private static Currency currency(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase ISO code");
        }
        try {
            Currency currency = Currency.getInstance(code);
            if (currency.getDefaultFractionDigits() < 0) {
                throw new IllegalArgumentException("Currency has no ISO 4217 minor-unit exponent");
            }
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency is not supported by the ISO currency registry", exception);
        }
    }
}
