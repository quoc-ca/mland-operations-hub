package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the payment-adapter profile conflict found from a Member-facing bug report: the
 * checkout page showed a generic "An unexpected error occurred" when paying with VNPay.
 *
 * <p>{@code VnpayRetailPaymentAdapter} used to declare {@code @Profile("retail-vnpay")} with no exclusion of
 * {@code local}, so a run with both {@code local} and {@code retail-vnpay} active silently picked the real
 * VNPay adapter over {@link LocalRetailPaymentAdapter}. That adapter reads
 * {@link edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold} rows, but the
 * local checkout flow ({@code RetailCheckoutService#createCheckout}) only ever creates a checkout-level
 * {@link edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CheckoutHold}, so every payment
 * initiation under that combination failed with "Checkout hold expired or is unavailable".
 *
 * <p>This asserts the declared {@link Profile} expressions resolve the local adapter whenever {@code local}
 * is active, matching the already-correct sibling beans in this package
 * ({@code RetailQueryReconciliationService}, {@code RetailConfirmedUnpaidExpiryJob}), which both use
 * {@code "retail-vnpay & !local"}.
 */
class RetailPaymentPortProfileTest {

    private static String profileExpressionOf(Class<? extends RetailPaymentPort> type) {
        return type.getAnnotation(Profile.class).value()[0];
    }

    private static boolean activatesUnder(Class<? extends RetailPaymentPort> type, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return accepts(environment, profileExpressionOf(type));
    }

    private static boolean accepts(Environment environment, String expression) {
        return environment.acceptsProfiles(Profiles.of(expression));
    }

    static Stream<List<String>> profileCombinations() {
        return Stream.of(
            List.of("local"),
            List.of("retail-vnpay"),
            List.of("local", "retail-vnpay"),
            List.of("local", "retail-payment-sim"),
            List.of()
        );
    }

    @ParameterizedTest
    @MethodSource("profileCombinations")
    void localAndVnPayAdaptersNeverBothActivateForTheSameProfileSet(List<String> active) {
        String[] activeProfiles = active.toArray(String[]::new);
        boolean localActive = activatesUnder(LocalRetailPaymentAdapter.class, activeProfiles);
        boolean vnpayActive = activatesUnder(VnpayRetailPaymentAdapter.class, activeProfiles);
        assertThat(localActive && vnpayActive)
            .as("profiles %s must not activate both the local and the VNPay payment adapter", active)
            .isFalse();
    }

    @Test
    void localProfileAloneSelectsTheLocalAdapterNotVnPay() {
        assertThat(activatesUnder(LocalRetailPaymentAdapter.class, "local")).isTrue();
        assertThat(activatesUnder(VnpayRetailPaymentAdapter.class, "local")).isFalse();
    }

    @Test
    void localAndRetailVnPayTogetherStillSelectTheLocalAdapter() {
        // This is the exact combination from the bug report: both profiles active at once.
        assertThat(activatesUnder(LocalRetailPaymentAdapter.class, "local", "retail-vnpay")).isTrue();
        assertThat(activatesUnder(VnpayRetailPaymentAdapter.class, "local", "retail-vnpay")).isFalse();
    }

    @Test
    void retailVnPayWithoutLocalSelectsTheVnPayAdapter() {
        assertThat(activatesUnder(VnpayRetailPaymentAdapter.class, "retail-vnpay")).isTrue();
        assertThat(activatesUnder(LocalRetailPaymentAdapter.class, "retail-vnpay")).isFalse();
    }
}
