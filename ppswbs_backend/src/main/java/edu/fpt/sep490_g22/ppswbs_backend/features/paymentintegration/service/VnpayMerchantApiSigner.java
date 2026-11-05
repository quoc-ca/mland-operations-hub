package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component @RequiredArgsConstructor
public class VnpayMerchantApiSigner {
    private final VnpayProperties properties;

    public Map<String, String> signQueryDr(Map<String, String> fields) {
        return sign(fields, List.of("vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
                "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo"));
    }

    public Map<String, String> signRefund(Map<String, String> fields) {
        return sign(fields, List.of("vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
                "vnp_TransactionType", "vnp_TxnRef", "vnp_Amount", "vnp_TransactionNo",
                "vnp_TransactionDate", "vnp_CreateBy", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo"));
    }

    public boolean verifyQueryDrResponse(Map<String, String> response) {
        return verify(response, List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
                "vnp_PromotionCode", "vnp_PromotionAmount"));
    }

    public boolean verifyRefundResponse(Map<String, String> response) {
        return verify(response, List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"));
    }

    private Map<String, String> sign(Map<String, String> fields, List<String> order) {
        Map<String, String> result = new LinkedHashMap<>(fields);
        result.put("vnp_SecureHash", hmac(join(fields, order)));
        return result;
    }

    private boolean verify(Map<String, String> response, List<String> order) {
        String supplied = response.get("vnp_SecureHash");
        return supplied != null && !supplied.isBlank() &&
                java.security.MessageDigest.isEqual(
                        supplied.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                        hmac(join(response, order)).getBytes(StandardCharsets.US_ASCII));
    }

    private static String join(Map<String, String> values, List<String> order) {
        return String.join("|", order.stream().map(key -> values.getOrDefault(key, "")).toList());
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.getHashSecret().trim().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to sign VNPay Merchant API request", error);
        }
    }
}
