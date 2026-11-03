package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ghtk.quote")
public class GhtkQuoteProperties {
    private boolean enabled;
    private String baseUrl = "https://services.giaohangtietkiem.vn";
    private String token;
    private String clientSource;
    private String pickupAddress;
    private String pickupProvince;
    private String pickupDistrict;
    private String pickupWard;
    private int timeoutSeconds = 5;

    boolean hasRequiredConfiguration() {
        return hasText(token) && hasText(pickupAddress) && hasText(pickupProvince)
            && hasText(pickupDistrict) && hasText(pickupWard) && timeoutSeconds > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
