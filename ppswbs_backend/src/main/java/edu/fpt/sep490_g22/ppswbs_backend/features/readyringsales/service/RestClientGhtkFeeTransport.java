package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.URI;

/** Read-only GHTK fee transport available in every profile. It never calls any shipment mutation endpoint. */
@Component
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
            var endpointBuilder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/services/shipment/fee")
                .queryParam("pick_address", request.pickupAddress())
                .queryParam("pick_province", request.pickupProvince())
                .queryParam("pick_ward", request.pickupWard())
                .queryParam("address", request.address())
                .queryParam("province", request.province())
                .queryParam("weight", request.weightGrams())
                .queryParam("value", request.declaredValue().toPlainString())
                .queryParam("transport", "road");
            // GHTK fee API (two-level addresses): province and ward are required, districts are optional hints.
            if (request.ward() != null && !request.ward().isBlank()) {
                endpointBuilder.queryParam("ward", request.ward());
            }
            if (request.pickupDistrict() != null && !request.pickupDistrict().isBlank()) {
                endpointBuilder.queryParam("pick_district", request.pickupDistrict());
            }
            if (request.district() != null && !request.district().isBlank()) {
                endpointBuilder.queryParam("district", request.district());
            }
            String endpoint = endpointBuilder.build()
                .encode()
                .toUriString();
            var requestSpec = RestClient.builder().requestFactory(factory).build().get().uri(URI.create(endpoint))
                .header("Token", properties.getToken());
            if (properties.getClientSource() != null && !properties.getClientSource().isBlank()) {
                requestSpec.header("X-Client-Source", properties.getClientSource());
            }
            String body = requestSpec.retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            boolean success = root.path("success").asBoolean(false);
            String message = root.path("message").asText("");
            if (!success) {
                return new FeeResponse(false, false, null,
                    message.isBlank() ? "GHTK could not calculate the shipping fee" : message);
            }
            JsonNode fee = root.path("fee");
            if (!fee.hasNonNull("fee") || !fee.path("fee").isNumber()) return new FeeResponse(false, false, null, "GHTK returned no delivery fee");
            BigDecimal charge = fee.path("fee").decimalValue();
            if (charge.signum() < 0 || charge.stripTrailingZeros().scale() > 0 || charge.precision() - charge.scale() > 19) {
                return new FeeResponse(false, false, null, "GHTK returned an invalid delivery fee");
            }
            return new FeeResponse(true, fee.path("delivery").asBoolean(false), charge, message);
        } catch (Exception exception) {
            throw new RetailIntegrationUnavailableException(
                "GHTK shipping quote is temporarily unavailable: " + exception.getMessage());
        }
    }
}
