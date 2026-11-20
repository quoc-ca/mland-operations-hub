package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Session 2026-11-18: "Thanh toán" in "Chờ thanh toán" reopens the stored VNPay link while the hold lasts. */
class GroupedCheckoutPaymentLinkTest {

    private static GroupedCheckout checkout() {
        return new GroupedCheckout(1L, "key-1", 1L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void theStoredLinkIsReusedOnlyWhilePaymentIsOpenAndTheLinkIsValid() {
        var checkout = checkout();
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        assertThat(checkout.reusablePaymentUrl(now)).isNull();

        checkout.beginPayment();
        checkout.rememberPaymentLink("https://sandbox.vnpayment.vn/pay?x=1", now.plusSeconds(900));
        assertThat(checkout.reusablePaymentUrl(now.plusSeconds(600))).isEqualTo("https://sandbox.vnpayment.vn/pay?x=1");
        assertThat(checkout.reusablePaymentUrl(now.plusSeconds(900))).isNull();

        checkout.markPaid();
        assertThat(checkout.reusablePaymentUrl(now)).isNull();
    }
}
