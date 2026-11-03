package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class OtaCurrencyMoneyTest {
    @ParameterizedTest
    @CsvSource({"VND,1.0000,1", "JPY,2,2", "USD,1.2300,123", "KWD,1.234,1234", "CLF,1.2345,12345"})
    void convertsUsingCurrencyExponentWithoutRounding(String currency,String amount,long expected) {
        assertThat(OtaCurrencyMoney.toMinor(amount,currency)).isEqualTo(expected);
        assertThat(OtaCurrencyMoney.toMinor(OtaCurrencyMoney.fromMinor(expected,currency).toPlainString(),currency)).isEqualTo(expected);
    }
    @ParameterizedTest
    @CsvSource({"VND,1.1", "USD,1.231", "KWD,1.2345", "USD,9223372036854775808", "XXX,1", "ZZZ,1"})
    void rejectsUnrepresentableOrUnsupportedAmounts(String currency,String amount) {
        assertThatThrownBy(()->OtaCurrencyMoney.toMinor(amount,currency)).isInstanceOf(IllegalArgumentException.class);
    }
}
