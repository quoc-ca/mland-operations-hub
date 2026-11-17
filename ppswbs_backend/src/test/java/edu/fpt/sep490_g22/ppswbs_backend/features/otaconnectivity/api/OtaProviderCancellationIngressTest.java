package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.BaseOtaCancellationAdapter;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 003.4 T121 (US3, FR-010, FR-011): authenticated interim cancellation ingress with the booking-events rules. */
class OtaProviderCancellationIngressTest {

    private static final String BODY = "{\"eventType\":\"BOOKING_CANCELLED\",\"bookingId\":\"KL-1\"}";

    private final OtaProviderEventHandler eventHandler = mock(OtaProviderEventHandler.class);
    private final OtaChannelConnectionRepository connections = mock(OtaChannelConnectionRepository.class);
    private final OtaCancellationService cancellations = mock(OtaCancellationService.class);
    private final MockEnvironment environment = new MockEnvironment();

    private MockMvc mvc(boolean allowTestTokens) {
        OtaProviderEventController controller =
                new OtaProviderEventController(eventHandler, connections, environment, cancellations);
        ReflectionTestUtils.setField(controller, "allowTestTokens", allowTestTokens);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new OtaExceptionHandler()).build();
    }

    @BeforeEach
    void setUp() {
        environment.setProperty("klook-test-credential", "resolved-klook-secret");
        when(connections.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(OtaChannelConnection.builder().id(1L).providerCode(OtaProvider.KLOOK)
                        .environment(OtaEnvironment.TEST).credentialReference("klook-test-credential")
                        .status("ACTIVE").build()));
    }

    @Test
    @DisplayName("A callback signed with the resolved secret reaches the cancellation service, marked as authenticated")
    void signedCallbackIsProcessed() throws Exception {
        when(cancellations.handleRawProviderCancellation(eq(OtaProvider.KLOOK), eq(OtaEnvironment.TEST), eq(BODY), any()))
                .thenReturn(OtaCancellationEventResult.success("CANCELLED", "KL-1", "chb-1", "CANCELLED"));

        mvc(false).perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "TEST")
                        .header("X-Ota-Signature", "resolved-klook-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.channelBookingReference").value("chb-1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(cancellations).handleRawProviderCancellation(eq(OtaProvider.KLOOK), eq(OtaEnvironment.TEST), eq(BODY),
                headers.capture());
        assertThat(headers.getValue()).containsEntry(BaseOtaCancellationAdapter.AUTHENTICATED_INGRESS_HEADER, "true");
        assertThat(headers.getValue()).doesNotContainKey("X-Ota-Signature");
    }

    @Test
    @DisplayName("The credential reference itself is not a valid signature")
    void credentialReferenceIsNotASignature() throws Exception {
        mvc(false).perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "TEST")
                        .header("X-Ota-Signature", "klook-test-credential")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cancellations);
    }

    @Test
    @DisplayName("A missing signature is refused before any processing")
    void missingSignatureIsRefused() throws Exception {
        mvc(true).perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "TEST")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cancellations);
    }

    @Test
    @DisplayName("An unresolvable credential reference fails closed")
    void unresolvableCredentialFailsClosed() throws Exception {
        when(connections.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(OtaChannelConnection.builder().id(1L).providerCode(OtaProvider.KLOOK)
                        .environment(OtaEnvironment.TEST).credentialReference("missing-reference")
                        .status("ACTIVE").build()));

        mvc(false).perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "TEST")
                        .header("X-Ota-Signature", "missing-reference")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cancellations);
    }

    @Test
    @DisplayName("Production callbacks stay closed until a provider is approved")
    void productionIsClosedByDefault() throws Exception {
        mvc(true).perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "PRODUCTION")
                        .header("X-Ota-Signature", "test-sig:any")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cancellations);
    }

    @Test
    @DisplayName("A rejected cancellation is acknowledged with 422 and its work item reference")
    void rejectedCancellationReturns422() throws Exception {
        when(cancellations.handleRawProviderCancellation(any(), any(), any(), any()))
                .thenReturn(OtaCancellationEventResult.rejected(null, "UNMAPPED_PROVIDER_EVENT: x", "wi-1"));

        mvc(true).perform(post("/api/v1/ota-provider/cancellation-events")
                        .param("provider", "KLOOK").param("environment", "TEST")
                        .header("X-Ota-Signature", "test-sig:ok")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reconciliationWorkItemReference").value("wi-1"));
    }
}
