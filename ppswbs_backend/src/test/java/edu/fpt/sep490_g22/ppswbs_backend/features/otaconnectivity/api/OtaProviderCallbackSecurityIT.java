package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider callbacks through the real Spring Security filter chain: a provider has no Mland bearer, so the two
 * callback routes must reach OtaProviderEventController, which then authenticates X-Ota-Signature itself (E3, T118).
 * Every other OTA route still needs a Mland identity.
 */
@SpringBootTest(properties = {
        "ota.auth.allow-test-tokens=false",
        "ota-security-it-klook-credential=resolved-klook-secret"
})
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class OtaProviderCallbackSecurityIT {

    private static final String CANCEL_BODY = "{\"eventType\":\"BOOKING_CANCELLED\",\"bookingId\":\"KL-1\"}";

    @Autowired private MockMvc mvc;
    @MockBean private OtaChannelConnectionRepository connections;
    @MockBean private OtaCancellationService cancellations;
    @MockBean private OtaProviderEventHandler bookingEvents;

    @BeforeEach
    void setUp() {
        when(connections.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(OtaChannelConnection.builder().id(1L).providerCode(OtaProvider.KLOOK)
                        .environment(OtaEnvironment.TEST).credentialReference("ota-security-it-klook-credential")
                        .status("ACTIVE").build()));
    }

    @Test
    @DisplayName("A signed cancellation callback without a Mland bearer reaches the controller")
    void signedCancellationWithoutBearerIsProcessed() throws Exception {
        when(cancellations.handleRawProviderCancellation(any(), any(), any(), any()))
                .thenReturn(OtaCancellationEventResult.success("CANCELLED", "KL-1", "chb-1", "CANCELLED"));

        mvc.perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "TEST")
                        .header("X-Ota-Signature", "resolved-klook-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Past the security chain, a wrong signature is still refused with 401 by the controller")
    void wrongSignatureIsRefusedByTheController() throws Exception {
        mvc.perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "TEST")
                        .header("X-Ota-Signature", "ota-security-it-klook-credential")
                        .contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identity authentication failed"));
        verifyNoInteractions(cancellations);
    }

    @Test
    @DisplayName("A booking callback without a Mland bearer reaches the controller's signature check")
    void bookingCallbackReachesTheSignatureCheck() throws Exception {
        String body = """
                {"provider":"KLOOK","environment":"TEST","externalBookingReference":"KL-9",
                 "providerProductReference":"p","providerOptionReference":"o","participantCount":1,
                 "acceptedAmountMinor":100000,"currencyCode":"VND"}""";

        // No signature: the controller (not the security chain) answers, so no booking work happens.
        mvc.perform(post("/api/v1/ota-provider/booking-events")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identity authentication failed"));
        verifyNoInteractions(bookingEvents);
    }

    @Test
    @DisplayName("Opening the callbacks does not open other OTA routes: they still need a Mland identity")
    void otherOtaRoutesStillNeedAMlandIdentity() throws Exception {
        // The identity gate answers these before any OTA controller runs.
        mvc.perform(get("/api/v1/ota-channels")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_BEARER_TOKEN"));
        mvc.perform(get("/api/v1/ota-provider/cancellation-events")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_BEARER_TOKEN"));
    }
}
