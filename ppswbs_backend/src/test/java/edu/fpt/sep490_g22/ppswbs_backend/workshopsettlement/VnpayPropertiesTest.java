package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.config.VnpayProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnpayPropertiesTest {
    @Test void disabledConfigurationDoesNotRequireCredentials() {
        var properties = new VnpayProperties();
        assertThatCode(properties::validateForEnabledRuntime).doesNotThrowAnyException();
    }

    @Test void enabledConfigurationRequiresApprovedRuntimeValues() {
        var properties = new VnpayProperties();
        properties.setEnabled(true);
        assertThatThrownBy(properties::validateForEnabledRuntime)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vnpay.tmn-code");
    }

    @Test void enabledConfigurationRejectsNonHttpsCallbackUrls() {
        var properties = configured();
        properties.setIpnUrl("http://localhost/ipn");
        assertThatThrownBy(properties::validateForEnabledRuntime)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vnpay.ipn-url");
    }

    private static VnpayProperties configured() {
        var properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TEST-TMN");
        properties.setHashSecret("runtime-only");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://merchant.example.test/return");
        properties.setIpnUrl("https://merchant.example.test/api/v1/integrations/payments/vnpay/events");
        properties.setQueryDrUrl("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
        properties.setRefundUrl("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
        return properties;
    }
}
