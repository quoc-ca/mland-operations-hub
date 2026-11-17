package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayPaymentGatewayCheckoutAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnpayPaymentGatewayCheckoutAdapterTest {
    private static final DateTimeFormatter VN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.ofHours(7));

    @Test
    void signsTheSharedAbsoluteDeadlineIntoVnpayExpiryParameter() {
        var properties = new VnpayProperties();
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://example.test/payment/return");
        properties.setHashSecret("unit-test-only-secret");
        var adapter = new VnpayPaymentGatewayCheckoutAdapter(properties,
                new VnpayChecksumService(properties));
        Instant createdAt = Instant.parse("2026-10-31T04:00:00Z");
        Instant sharedDeadline = Instant.parse("2026-10-31T04:10:00Z");

        String url = adapter.createDepositUrl("DEP-BK-1-1", "BK-1", 50_000L,
                createdAt, sharedDeadline, "127.0.0.1");
        Map<String, String> query = Arrays.stream(url.substring(url.indexOf('?') + 1).split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> decode(pair[0]), pair -> decode(pair[1])));

        assertThat(query.get("vnp_CreateDate")).isEqualTo(VN_TIME.format(createdAt));
        assertThat(query.get("vnp_ExpireDate")).isEqualTo(VN_TIME.format(sharedDeadline));
        assertThat(query.get("vnp_Amount")).isEqualTo("5000000");
        assertThat(query).containsKey("vnp_SecureHash");
        String secureHash = query.remove("vnp_SecureHash");
        assertThat(new VnpayChecksumService(properties).valid(query, secureHash)).isTrue();
    }

    @Test
    void signsRetailCheckoutWithGroupedAmountAndDeadline() {
        var properties = new VnpayProperties();
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://example.test/payment/return");
        properties.setHashSecret("unit-test-only-secret");
        var checksum = new VnpayChecksumService(properties);
        PaymentGatewayCheckoutPort adapter = new VnpayPaymentGatewayCheckoutAdapter(properties, checksum);
        var createdAt = Instant.parse("2026-11-04T04:00:00Z");
        var deadline = Instant.parse("2026-11-04T04:15:00Z");
        String url = adapter.createRetailPaymentUrl("RETAIL-1", "CHECKOUT-1", 300_000L, createdAt, deadline, "127.0.0.1");
        Map<String, String> query = Arrays.stream(url.substring(url.indexOf('?') + 1).split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> decode(pair[0]), pair -> decode(pair[1])));
        assertThat(query).containsEntry("vnp_TxnRef", "RETAIL-1")
                .containsEntry("vnp_Amount", "30000000")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_OrderInfo", "Retail checkout CHECKOUT-1")
                .containsEntry("vnp_ExpireDate", VN_TIME.format(deadline));
        String signature = query.remove("vnp_SecureHash");
        assertThat(checksum.valid(query, signature)).isTrue();
        assertThatThrownBy(() -> adapter.createRetailPaymentUrl("", "CHECKOUT-1", 300_000L, createdAt, deadline, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
