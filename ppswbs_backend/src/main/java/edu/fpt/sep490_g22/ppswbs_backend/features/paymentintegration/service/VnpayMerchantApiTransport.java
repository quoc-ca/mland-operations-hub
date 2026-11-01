package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import java.util.Map;

public interface VnpayMerchantApiTransport {
    Map<String, String> post(String endpoint, Map<String, String> body);
}
