package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCashCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailPaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetailPaymentMethodsTest {

    @Test
    void missingMethodKeepsVnpayAndUnknownMethodIsRejected() {
        assertThat(RetailPaymentMethods.parse(null)).isEqualTo(RetailPaymentMethod.VNPAY);
        assertThat(RetailPaymentMethods.parse("cash_at_counter")).isEqualTo(RetailPaymentMethod.CASH_AT_COUNTER);
        assertThatThrownBy(() -> RetailPaymentMethods.parse("COD")).isInstanceOf(RetailConflictException.class);
    }

    @Test
    void cashIsOnlyForOneStorePickupBranch() {
        assertThatCode(() -> RetailPaymentMethods.requireAllowed(RetailPaymentMethod.CASH_AT_COUNTER, List.of("PICKUP")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> RetailPaymentMethods.requireAllowed(RetailPaymentMethod.CASH_AT_COUNTER, List.of("GHTK")))
            .isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> RetailPaymentMethods.requireAllowed(RetailPaymentMethod.CASH_AT_COUNTER, List.of("PICKUP", "PICKUP")))
            .isInstanceOf(RetailConflictException.class);
        assertThatCode(() -> RetailPaymentMethods.requireAllowed(RetailPaymentMethod.VNPAY, List.of("GHTK", "PICKUP")))
            .doesNotThrowAnyException();
    }

    @Test
    void cashHasNoMinimumButZeroTotalStaysInternal() {
        assertThatCode(() -> RetailPaymentMethods.requirePayableForCash(RetailPaymentMethod.CASH_AT_COUNTER, BigDecimal.valueOf(2_000)))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> RetailPaymentMethods.requirePayableForCash(RetailPaymentMethod.CASH_AT_COUNTER, BigDecimal.ZERO))
            .isInstanceOf(RetailConflictException.class);
    }

    @Test
    void cashIsHeldForSeventyTwoHoursAndVnpayForFifteenMinutes() {
        Instant accepted = Instant.parse("2026-11-12T05:00:00Z");
        assertThat(RetailPaymentMethods.holdDeadline(RetailPaymentMethod.CASH_AT_COUNTER, accepted))
            .isEqualTo(accepted.plus(Duration.ofHours(72)));
        assertThat(RetailPaymentMethods.holdDeadline(RetailPaymentMethod.VNPAY, accepted))
            .isEqualTo(accepted.plus(Duration.ofMinutes(15)));
    }

    @Test
    void cashCollectionRecordsChangeAndRejectsShortPayment() {
        var collection = new RetailCashCollection("checkout", "order", 1L, LocalDate.of(2026, 11, 12),
            BigDecimal.valueOf(2_000), BigDecimal.valueOf(10_000), 7L, Instant.now(), "key");
        assertThat(collection.getChangeReturned()).isEqualByComparingTo("8000");
        assertThatThrownBy(() -> new RetailCashCollection("checkout", "order", 1L, LocalDate.of(2026, 11, 12),
            BigDecimal.valueOf(2_000), BigDecimal.valueOf(1_000), 7L, Instant.now(), "key"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
