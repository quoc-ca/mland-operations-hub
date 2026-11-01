package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class VnpayPaymentGatewayCheckoutAdapter implements PaymentGatewayCheckoutPort {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7));
    private final VnpayProperties properties;
    private final VnpayChecksumService checksum;

    @Override
    public String createDepositUrl(String merchantReference, String bookingCode, long amountVnd,
                                   Instant createdAt, Instant expiresAt, String clientIp) {
        return createUrl(merchantReference, bookingCode, amountVnd, createdAt, expiresAt, clientIp,
                "Workshop deposit ");
    }

    @Override
    public String createFinalPaymentUrl(String merchantReference, String bookingCode, long amountVnd,
                                        Instant createdAt, Instant expiresAt, String clientIp) {
        return createUrl(merchantReference, bookingCode, amountVnd, createdAt, expiresAt, clientIp,
                "Workshop final payment ");
    }

    private String createUrl(String merchantReference, String bookingCode, long amountVnd,
                             Instant createdAt, Instant expiresAt, String clientIp,
                             String orderInfoPrefix) {
        if (amountVnd <= 0 || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("INVALID_CHECKOUT_AMOUNT_OR_EXPIRY");
        }
        var params = new TreeMap<String, String>();
        params.put("vnp_Version", "2.1.0"); params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode()); params.put("vnp_Amount", String.valueOf(Math.multiplyExact(amountVnd, 100L)));
        params.put("vnp_CurrCode", "VND"); params.put("vnp_TxnRef", merchantReference);
        params.put("vnp_OrderInfo", orderInfoPrefix + bookingCode); params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn"); params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", DATE.format(createdAt));
        params.put("vnp_ExpireDate", DATE.format(expiresAt));
        String query = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).reduce((a,b) -> a + "&" + b).orElse("");
        return properties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + checksum.sign(params);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
