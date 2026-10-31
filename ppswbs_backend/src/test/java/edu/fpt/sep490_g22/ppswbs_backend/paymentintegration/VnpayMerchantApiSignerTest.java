package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayMerchantApiSigner;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayMerchantApiSignerTest {
    private static final String SECRET = "merchant-api-test-secret";

    @Test
    void queryDrRequestUsesDocumentedPipeDelimitedFieldOrder() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("vnp_RequestId", "request-1");
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_TmnCode", "TEST1234");
        fields.put("vnp_TxnRef", "DEP-BOOKING-1");
        fields.put("vnp_TransactionDate", "20261030181359");
        fields.put("vnp_CreateDate", "20261031120000");
        fields.put("vnp_IpAddr", "127.0.0.1");
        fields.put("vnp_OrderInfo", "Workshop deposit");

        Map<String, String> signed = signer().signQueryDr(fields);

        assertThat(signed.get("vnp_SecureHash")).isEqualTo(hmac(String.join("|",
                "request-1", "2.1.0", "querydr", "TEST1234", "DEP-BOOKING-1",
                "20261030181359", "20261031120000", "127.0.0.1", "Workshop deposit")));
    }

    @Test
    void refundRequestUsesFullRefundCanonicalFieldOrder() {
        var fields = new LinkedHashMap<String, String>();
        fields.put("vnp_RequestId", "request-2");
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "refund");
        fields.put("vnp_TmnCode", "TEST1234");
        fields.put("vnp_TransactionType", "02");
        fields.put("vnp_TxnRef", "DEP-BOOKING-1");
        fields.put("vnp_Amount", "5000000");
        fields.put("vnp_TransactionNo", "12345678");
        fields.put("vnp_TransactionDate", "20261030181359");
        fields.put("vnp_CreateBy", "manager-test");
        fields.put("vnp_CreateDate", "20261031120000");
        fields.put("vnp_IpAddr", "127.0.0.1");
        fields.put("vnp_OrderInfo", "Workshop deposit");

        Map<String, String> signed = signer().signRefund(fields);

        assertThat(signed.get("vnp_SecureHash")).isEqualTo(hmac(String.join("|",
                "request-2", "2.1.0", "refund", "TEST1234", "02", "DEP-BOOKING-1",
                "5000000", "12345678", "20261030181359", "manager-test", "20261031120000",
                "127.0.0.1", "Workshop deposit")));
    }

    @Test
    void queryDrResponseVerificationRejectsTampering() {
        Map<String, String> response = queryResponse("00");
        response.put("vnp_SecureHash", hmac(String.join("|", "response-1", "querydr", "00", "OK",
                "TEST1234", "DEP-BOOKING-1", "5000000", "NCB", "20261030181540", "12345678",
                "01", "00", "Workshop deposit", "", "")));
        assertThat(signer().verifyQueryDrResponse(response)).isTrue();

        response.put("vnp_Amount", "5000001");
        assertThat(signer().verifyQueryDrResponse(response)).isFalse();
    }

    @Test
    void refundResponseVerificationUsesItsPublishedResponseFieldSet() {
        var response = new LinkedHashMap<String, String>();
        response.put("vnp_ResponseId", "response-2");
        response.put("vnp_Command", "refund");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Accepted");
        response.put("vnp_TmnCode", "TEST1234");
        response.put("vnp_TxnRef", "DEP-BOOKING-1");
        response.put("vnp_Amount", "5000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261031120100");
        response.put("vnp_TransactionNo", "12345679");
        response.put("vnp_TransactionType", "02");
        response.put("vnp_TransactionStatus", "06");
        response.put("vnp_OrderInfo", "Workshop deposit");
        response.put("vnp_SecureHash", hmac(String.join("|", "response-2", "refund", "00", "Accepted",
                "TEST1234", "DEP-BOOKING-1", "5000000", "NCB", "20261031120100", "12345679",
                "02", "06", "Workshop deposit")));

        assertThat(signer().verifyRefundResponse(response)).isTrue();
    }

    private static VnpayMerchantApiSigner signer() {
        var properties = new VnpayProperties();
        properties.setHashSecret(SECRET);
        return new VnpayMerchantApiSigner(properties);
    }

    private static Map<String, String> queryResponse(String code) {
        var response = new LinkedHashMap<String, String>();
        response.put("vnp_ResponseId", "response-1");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", code);
        response.put("vnp_Message", "OK");
        response.put("vnp_TmnCode", "TEST1234");
        response.put("vnp_TxnRef", "DEP-BOOKING-1");
        response.put("vnp_Amount", "5000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261030181540");
        response.put("vnp_TransactionNo", "12345678");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Workshop deposit");
        response.put("vnp_PromotionCode", "");
        response.put("vnp_PromotionAmount", "");
        return response;
    }

    private static String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
