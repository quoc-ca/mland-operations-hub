package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.http.HttpClient;

/** Local-only, read-only GHTK fee transport. It never calls any shipment mutation endpoint. */
@Component
@Profile("local")
@RequiredArgsConstructor
class RestClientGhtkFeeTransport implements GhtkFeeTransport {
    private final GhtkQuoteProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public FeeResponse quote(FeeRequest request) {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(java.time.Duration.ofSeconds(properties.getTimeoutSeconds()));
            String endpoint = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/services/shipment/fee")
                .queryParam("pick_address", request.pickupAddress())
                .queryParam("pick_province", request.pickupProvince())
                .queryParam("pick_district", request.pickupDistrict())
                .queryParam("pick_ward", request.pickupWard())
                .queryParam("address", request.address())
                .queryParam("province", request.province())
                .queryParam("district", request.district())
                .queryParam("ward", request.ward())
                .queryParam("weight", request.weightGrams())
                .queryParam("value", request.declaredValue().toPlainString())
                .queryParam("transport", "road")
                .build()
                .encode()
                .toUriString();
            var requestSpec = RestClient.builder().requestFactory(factory).build().get().uri(endpoint)
                .header("Token", properties.getToken());
            if (properties.getClientSource() != null && !properties.getClientSource().isBlank()) {
                requestSpec.header("X-Client-Source", properties.getClientSource());
            }
            String body = requestSpec.retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            boolean success = root.path("success").asBoolean(false);
            String message = root.path("message").asText("");
            if (!success) return new FeeResponse(false, false, null, message);
            JsonNode fee = root.path("fee");
            if (!fee.hasNonNull("fee")) return new FeeResponse(false, false, null, "GHTK returned no delivery fee");
            return new FeeResponse(true, fee.path("delivery").asBoolean(false), fee.path("fee").decimalValue(), message);
        } catch (Exception exception) {
            throw new RetailIntegrationUnavailableException("GHTK shipping quote is temporarily unavailable");
        }
    }
}
