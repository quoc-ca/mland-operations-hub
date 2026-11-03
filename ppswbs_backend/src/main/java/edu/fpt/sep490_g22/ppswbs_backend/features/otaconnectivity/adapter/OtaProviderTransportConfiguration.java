package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Safe development default. Provider credentials and an approved transport are
 * supplied by deployment configuration; until then outbound delivery fails
 * closed without making a real provider call.
 */
@Configuration
public class OtaProviderTransportConfiguration {

    @Bean
    OtaProviderTransport otaProviderTransport() {
        return request -> new OtaTransportResponse(503, "PROVIDER_TRANSPORT_NOT_CONFIGURED", Map.of());
    }
}
