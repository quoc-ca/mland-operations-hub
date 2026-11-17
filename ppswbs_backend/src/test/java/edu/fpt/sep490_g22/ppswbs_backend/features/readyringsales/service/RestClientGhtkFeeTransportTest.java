package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientGhtkFeeTransportTest {
    @Test
    void sendsUnicodeAddressOnceEncodedAndReadsTotalFeeWithoutCreatingShipment() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/services/shipment/fee", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] body = "{\"success\":true,\"fee\":{\"fee\":30450,\"delivery\":true}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var properties = new GhtkQuoteProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setToken("test-only");
            var transport = new RestClientGhtkFeeTransport(properties, new ObjectMapper());
            var result = transport.quote(new GhtkFeeTransport.FeeRequest("83 Mã Mây", "Hà Nội", "Hoàn Kiếm", "Hoàn Kiếm",
                "Số 3 Đồi Xuân", "Hà Nội", "Thạch Thất", "Hạ Bằng", 50, BigDecimal.valueOf(1250000)));
            assertThat(path.get()).isEqualTo("/services/shipment/fee");
            assertThat(query.get()).contains("pick_address=83 Mã Mây", "address=Số 3 Đồi Xuân", "weight=50");
            assertThat(result.success()).isTrue();
            assertThat(result.deliveryAvailable()).isTrue();
            assertThat(result.charge()).isEqualByComparingTo("30450");
        } finally {
            server.stop(0);
        }
    }
}
