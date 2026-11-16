package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSessionStartScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** T119: the late auto-start counter can be scraped, and only through the management port (never the public API port). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "management.server.address=127.0.0.1"})
@ActiveProfiles("test")
@AutoConfigureObservability
class WorkshopMetricsScrapeIT {

    @LocalServerPort private int apiPort;
    @LocalManagementPort private int managementPort;
    @Autowired private MeterRegistry meterRegistry;

    private HttpResponse<String> get(int port, String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void lateStartCounterIsScrapeableOnTheManagementPortOnly() throws Exception {
        meterRegistry.counter(WorkshopSessionStartScheduler.LATE_START_METRIC).increment();

        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("workshop_session_auto_start_late_total");

        HttpResponse<String> publicPort = get(apiPort, "/actuator/prometheus");
        assertThat(publicPort.statusCode()).isIn(401, 403, 404);
        assertThat(publicPort.body()).doesNotContain("workshop_session_auto_start_late_total");

        assertThat(get(managementPort, "/actuator/env").statusCode()).isIn(401, 403, 404);
    }
}
