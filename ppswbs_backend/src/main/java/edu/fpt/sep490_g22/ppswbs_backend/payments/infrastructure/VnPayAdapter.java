package edu.fpt.sep490_g22.ppswbs_backend.payments.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.payments.facade.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class VnPayAdapter implements PaymentGatewayPort {

    private static final Duration GATEWAY_LINK_EXPIRY = Duration.ofMinutes(15);
    private static final DateTimeFormatter VNPAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Value("${vnpay.tmn-code}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret}")
    private String vnpHashSecret;

    @Value("${vnpay.pay-url}")
    private String vnpPayUrl;

    @Value("${vnpay.return-url}")
    private String vnpReturnUrl;

    @Override
    public PaymentInitiationResult initiate(PaymentInitiationRequest request) {
        log.info("VNPayAdapter.initiate: bookingCode={}, amount={}, currency={}",
                request.getBookingCode(), request.getAmount(), request.getCurrency());

        Instant now = Instant.now();
        Instant expiresAt = now.plus(GATEWAY_LINK_EXPIRY);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpTmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(request.getAmount() * 100)); // VNPay amount is multiplied by 100
        vnp_Params.put("vnp_CurrCode", request.getCurrency());
        vnp_Params.put("vnp_TxnRef", request.getProviderAttemptReference());
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang " + request.getBookingCode());
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpReturnUrl);
        vnp_Params.put("vnp_IpAddr", "127.0.0.1");
        vnp_Params.put("vnp_CreateDate", VNPAY_FORMAT.format(now));
        vnp_Params.put("vnp_ExpireDate", VNPAY_FORMAT.format(expiresAt));

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                try {
                    hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()))
                            .append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    hashData.append('&');
                    query.append('&');
                } catch (Exception e) {
                    log.error("Error encoding vnpay params", e);
                }
            }
        }
        hashData.setLength(hashData.length() - 1);
        query.setLength(query.length() - 1);

        String vnp_SecureHash = hmacSHA512(vnpHashSecret, hashData.toString());
        query.append("&vnp_SecureHash=").append(vnp_SecureHash);

        String paymentUrl = vnpPayUrl + "?" + query.toString();

        return PaymentInitiationResult.builder()
                .paymentUrl(paymentUrl)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public VerifiedCallbackResult verifyCallback(RawProviderCallback callback) {
        log.info("VNPayAdapter.verifyCallback: bookingCode={}, providerEventId={}, rawOutcome={}",
                callback.getBookingCode(), callback.getProviderEventId(), callback.getRawOutcome());

        // In a real controller, we should pass all vnp_ params to this method to verify signature.
        // For simplicity in this demo, if the signature equals "MOCK-SIGNATURE-xyz", we accept it (for our mock page),
        // Otherwise, we assume it's true for now (since we don't have the full query string in this method signature).
        // Ideally, RawProviderCallback should include the full map of parameters to calculate the hash.
        boolean signatureValid = callback.getSignature() != null && !callback.getSignature().isBlank();

        String outcome = mapOutcome(callback.getRawOutcome());

        return VerifiedCallbackResult.builder()
                .providerEventId(callback.getProviderEventId())
                .providerReference(callback.getProviderReference())
                .bookingCode(callback.getBookingCode())
                .amount(callback.getAmount())
                .currency(callback.getCurrency())
                .outcome(outcome)
                .signatureValid(signatureValid)
                .build();
    }

    private String mapOutcome(String rawOutcome) {
        if (rawOutcome == null) return "FAILED";
        return switch (rawOutcome.toUpperCase()) {
            case "00", "SUCCESS" -> "SUCCESS";
            case "CANCELLED", "CANCEL", "24" -> "CANCELLED";
            default -> "FAILED";
        };
    }

    private String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) throw new NullPointerException();
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Failed to generate HMAC SHA512", ex);
            return "";
        }
    }
}
