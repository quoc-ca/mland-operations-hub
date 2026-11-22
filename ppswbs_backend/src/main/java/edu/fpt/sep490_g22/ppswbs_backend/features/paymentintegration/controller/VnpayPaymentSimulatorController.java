package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.PaymentIngressService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Dev-only stand-in for the VNPay payment page, so a customer can scan a QR with a phone camera and "pay" with
 * virtual money. It exists only when {@code payment-simulator.enabled=true} (default false). Confirming builds the
 * same signed IPN VNPay would send and feeds it to {@link PaymentIngressService#receive}, so checksum, owner,
 * amount and deduplication checks run exactly as in production.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment-simulator.enabled", havingValue = "true")
public class VnpayPaymentSimulatorController {
    static final String BASE = "/api/v1/integrations/payments/simulator/";
    private static final DateTimeFormatter PAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentOwnerRegistry registry;
    private final PaymentIngressService ingress;
    private final VnpayChecksumService checksum;
    private final VnpayProperties properties;

    @GetMapping(value = BASE + "{merchantReference}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable String merchantReference) {
        var owner = registry.resolve(merchantReference).orElse(null);
        if (owner == null) return html("Không tìm thấy giao dịch", "<p>Mã giao dịch không tồn tại hoặc đã hết hạn.</p>");
        String ref = HtmlUtils.htmlEscape(merchantReference);
        return html("Thanh toán giả lập", "<p>Số tiền</p><p class=\"amt\">" + String.format("%,d", owner.getAmountVnd())
                + " VND</p><p class=\"ref\">" + ref + "</p>"
                + "<form method=\"post\" action=\"" + BASE + ref + "\"><button>Xác nhận chuyển khoản</button></form>"
                + "<p class=\"note\">Môi trường thử nghiệm: không có tiền thật được chuyển.</p>");
    }

    @PostMapping(value = BASE + "{merchantReference}", produces = MediaType.TEXT_HTML_VALUE)
    public String confirm(@PathVariable String merchantReference) {
        var owner = registry.resolve(merchantReference).orElse(null);
        if (owner == null) return html("Không tìm thấy giao dịch", "<p>Mã giao dịch không tồn tại hoặc đã hết hạn.</p>");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_TxnRef", merchantReference);
        params.put("vnp_Amount", String.valueOf(owner.getAmountVnd() * 100));
        params.put("vnp_BankCode", "SIM");
        params.put("vnp_OrderInfo", "Payment simulator");
        params.put("vnp_TransactionNo", "SIM" + System.currentTimeMillis());
        params.put("vnp_PayDate", LocalDateTime.now().format(PAY_DATE));
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", checksum.sign(params));
        var outcome = ingress.receive(params);
        boolean ok = "00".equals(outcome.rspCode()) || "02".equals(outcome.rspCode());
        return ok ? html("Thanh toán thành công", "<p class=\"ok\">Đã ghi nhận thanh toán. Bạn có thể đóng trang này.</p>")
                : html("Thanh toán chưa được ghi nhận", "<p>Hệ thống từ chối: " + HtmlUtils.htmlEscape(outcome.message()) + "</p>");
    }

    private static String html(String title, String body) {
        return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1\"><title>" + title + "</title><style>"
                + "body{font-family:system-ui,sans-serif;margin:0;padding:24px;max-width:420px;margin:auto;text-align:center}"
                + ".amt{font-size:32px;font-weight:700;margin:4px 0}.ref{color:#666;font-size:12px;word-break:break-all}"
                + "button{width:100%;padding:16px;font-size:18px;border:0;border-radius:8px;background:#005baa;color:#fff;margin-top:16px}"
                + ".note{color:#999;font-size:12px;margin-top:24px}.ok{color:#0a7d2c;font-size:18px}"
                + "</style></head><body><h2>" + title + "</h2>" + body + "</body></html>";
    }
}
