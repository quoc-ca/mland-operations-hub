package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Browser-only VNPay return bridge.
 *
 * The provider redirect is never payment evidence. This endpoint intentionally
 * does not invoke PaymentIngressService; it forwards only display fields to
 * the React result page, where the user can verify the committed booking state.
 */
@RestController
public class VnpayBrowserReturnController {
    private static final String DEPOSIT_PREFIX = "Workshop deposit ";
    private static final String FINAL_PREFIX = "Workshop final payment ";

    private final String frontendBaseUrl;

    public VnpayBrowserReturnController(
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);
    }

    @GetMapping("/payment/vnpay/return")
    public ResponseEntity<Void> redirectToDisplayPage(
            @RequestParam MultiValueMap<String, String> params) {
        String bookingCode = extractBookingCode(params.getFirst("vnp_OrderInfo"));
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(frontendBaseUrl);
        if (bookingCode != null) {
            target.pathSegment("workshop-booking", bookingCode, "payment-return");
        } else {
            target.path("/workshop-booking/lookup");
        }

        // Keep only non-sensitive display values. In particular, never forward
        // vnp_SecureHash or the raw provider callback payload to the client.
        Map<String, String> displayParams = new LinkedHashMap<>();
        copyIfPresent(params, displayParams, "vnp_ResponseCode");
        copyIfPresent(params, displayParams, "vnp_TransactionNo");
        copyIfPresent(params, displayParams, "vnp_TransactionStatus");
        copyIfPresent(params, displayParams, "vnp_Amount");
        copyIfPresent(params, displayParams, "vnp_TxnRef");
        displayParams.forEach(target::queryParam);

        URI location = target.build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private static void copyIfPresent(MultiValueMap<String, String> source,
                                      Map<String, String> target,
                                      String key) {
        String value = source.getFirst(key);
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private static String extractBookingCode(String orderInfo) {
        if (orderInfo == null || orderInfo.isBlank()) return null;
        if (orderInfo.startsWith(DEPOSIT_PREFIX)) return suffix(orderInfo, DEPOSIT_PREFIX);
        if (orderInfo.startsWith(FINAL_PREFIX)) return suffix(orderInfo, FINAL_PREFIX);
        return null;
    }

    private static String suffix(String value, String prefix) {
        String bookingCode = value.substring(prefix.length()).trim();
        return bookingCode.isBlank() ? null : bookingCode;
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) return "http://localhost:5173";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
