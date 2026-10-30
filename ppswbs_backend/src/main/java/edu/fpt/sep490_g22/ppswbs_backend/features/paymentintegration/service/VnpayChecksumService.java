package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service @RequiredArgsConstructor
public class VnpayChecksumService {
    private final VnpayProperties properties;
    public boolean valid(Map<String, String> params, String suppliedHash) {
        if (suppliedHash == null || suppliedHash.isBlank()) return false;
        try {
            TreeMap<String, String> sorted = new TreeMap<>();
            params.forEach((key, value) -> { if (!key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType") && value != null) sorted.put(key, value); });
            StringBuilder data = new StringBuilder();
            sorted.forEach((key, value) -> { if (data.length() > 0) data.append('&'); data.append(key).append('=').append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)); });
            Mac mac = Mac.getInstance("HmacSHA512"); mac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            String calculated = HexFormat.of().formatHex(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
            return calculated.equalsIgnoreCase(suppliedHash);
        } catch (Exception error) { return false; }
    }
}
