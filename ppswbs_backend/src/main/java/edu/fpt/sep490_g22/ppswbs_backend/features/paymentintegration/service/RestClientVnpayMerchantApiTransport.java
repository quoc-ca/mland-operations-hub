package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Map;

@Component @RequiredArgsConstructor
public class RestClientVnpayMerchantApiTransport implements VnpayMerchantApiTransport {
    private final VnpayProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> post(String endpoint, Map<String, String> body) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getMerchantApiConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getMerchantApiReadTimeout());
        String response = RestClient.builder()
                .requestFactory(factory)
                .build()
                .post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readValue(response, new TypeReference<>() { });
        } catch (Exception error) {
            throw new IllegalStateException("Invalid VNPay Merchant API response", error);
        }
    }
}
