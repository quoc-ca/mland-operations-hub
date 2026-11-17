package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.PaymentIngressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class VnpayPaymentIngressController {
    private final PaymentIngressService ingress;
    @GetMapping("/api/v1/integrations/payments/vnpay/events")
    public Map<String,String> receive(@RequestParam Map<String,String> params) {
        var outcome = ingress.receive(params);
        var response = new LinkedHashMap<String,String>(); response.put("RspCode", outcome.rspCode()); response.put("Message", outcome.message()); return response;
    }
}
