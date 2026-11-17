package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("T026: OTA Provider Event Controller Contract Tests")
class OtaProviderEventControllerIT {

    @Mock private OtaProviderEventHandler eventHandler;
    @Mock private OtaChannelConnectionRepository connectionRepository;

    private final org.springframework.mock.env.MockEnvironment mockEnvironment = new org.springframework.mock.env.MockEnvironment();
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        mockEnvironment.setProperty("secret-klook-signature-123", "secret-klook-signature-123");
        mockEnvironment.setProperty("prod-klook-credential", "prod-klook-credential");

        OtaProviderEventController controller = new OtaProviderEventController(eventHandler, connectionRepository, mockEnvironment);
        ReflectionTestUtils.setField(controller, "allowTestTokens", true);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new OtaExceptionHandler())
                .build();

        OtaChannelConnection connection = OtaChannelConnection.builder()
                .id(1L)
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .credentialReference("secret-klook-signature-123")
                .status("ACTIVE")
                .build();

        org.mockito.Mockito.lenient().when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(connection));
    }

    @Nested
    @DisplayName("Authentication & Request Validation Contract")
    class AuthenticationAndValidationTests {

        @Test
        @DisplayName("Missing X-Ota-Signature returns 401 Unauthorized with safe error")
        void missingSignature_returns401() throws Exception {
            String body = """
                    {
                        "provider": "KLOOK",
                        "environment": "TEST",
                        "externalBookingReference": "ext-123",
                        "providerProductReference": "prod-1",
                        "providerOptionReference": "opt-1",
                        "participantCount": 2,
                        "acceptedAmountMinor": 200000,
                        "currencyCode": "VND"
                    }
                    """;

            mvc.perform(post("/api/v1/ota-provider/booking-events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Identity authentication failed"))
                    .andExpect(result -> {
                        String responseContent = result.getResponse().getContentAsString();
                        assertThat(responseContent).doesNotContain("secret-klook-signature-123");
                        assertThat(responseContent).doesNotContain("ext-123");
                    });

            verify(eventHandler, never()).handleBookingEvent(any());
        }

        @Test
        @DisplayName("Invalid signature without test token bypass returns 401 Unauthorized")
        void invalidSignature_returns401() throws Exception {
            OtaProviderEventController strictController = new OtaProviderEventController(eventHandler, connectionRepository, mockEnvironment);
            ReflectionTestUtils.setField(strictController, "allowTestTokens", false);
            MockMvc strictMvc = MockMvcBuilders.standaloneSetup(strictController)
                    .setControllerAdvice(new OtaExceptionHandler())
                    .build();

            String body = """
                    {
                        "provider": "KLOOK",
                        "environment": "TEST",
                        "externalBookingReference": "ext-123",
                        "providerProductReference": "prod-1",
                        "providerOptionReference": "opt-1",
                        "participantCount": 2,
                        "acceptedAmountMinor": 200000,
                        "currencyCode": "VND"
                    }
                    """;

            strictMvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "wrong-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("E3: a PRODUCTION callback is refused while no signed callback is approved, even with a test token")
        void productionCallback_withoutApprovedProvider_returns403() throws Exception {
            mvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callback("PRODUCTION")))
                    .andExpect(status().isForbidden());
            verify(eventHandler, never()).handleBookingEvent(any());
        }

        @Test
        @DisplayName("E3: an approved PRODUCTION provider still needs its exact credential; test tokens are ignored there")
        void productionCallback_approvedProvider_ignoresTestToken() throws Exception {
            OtaProviderEventController controller = new OtaProviderEventController(eventHandler, connectionRepository, mockEnvironment);
            ReflectionTestUtils.setField(controller, "allowTestTokens", true);
            ReflectionTestUtils.setField(controller, "approvedProductionProviders", "klook");
            MockMvc approvedMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new OtaExceptionHandler()).build();
            org.mockito.Mockito.lenient().when(connectionRepository.findByProviderCodeAndEnvironment(
                    OtaProvider.KLOOK, OtaEnvironment.PRODUCTION)).thenReturn(Optional.of(OtaChannelConnection.builder()
                    .id(2L).providerCode(OtaProvider.KLOOK).environment(OtaEnvironment.PRODUCTION)
                    .credentialReference("prod-klook-credential").status("ACTIVE").build()));

            approvedMvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callback("PRODUCTION")))
                    .andExpect(status().isUnauthorized());
            verify(eventHandler, never()).handleBookingEvent(any());
        }

        @Test
        @DisplayName("E3: an inactive connection is refused even with its exact credential")
        void inactiveConnection_returns401() throws Exception {
            OtaProviderEventController controller = new OtaProviderEventController(eventHandler, connectionRepository, mockEnvironment);
            ReflectionTestUtils.setField(controller, "allowTestTokens", false);
            MockMvc strictMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new OtaExceptionHandler()).build();
            when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                    .thenReturn(Optional.of(OtaChannelConnection.builder().id(1L).providerCode(OtaProvider.KLOOK)
                            .environment(OtaEnvironment.TEST).credentialReference("secret-klook-signature-123")
                            .status("DISABLED").build()));

            strictMvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "secret-klook-signature-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callback("TEST")))
                    .andExpect(status().isUnauthorized());
            verify(eventHandler, never()).handleBookingEvent(any());
        }

        @Test
        @DisplayName("T059: resolved secret from environment is accepted when signature matches")
        void resolvedSecretFromEnvironment_accepted() throws Exception {
            mockEnvironment.setProperty("OTA_KLOOK_INGRESS_SECRET", "super-secret-token-xyz");
            when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                    .thenReturn(Optional.of(OtaChannelConnection.builder().id(1L).providerCode(OtaProvider.KLOOK)
                            .environment(OtaEnvironment.TEST).credentialReference("OTA_KLOOK_INGRESS_SECRET")
                            .status("ACTIVE").build()));

            OtaProviderEventController strictController = new OtaProviderEventController(eventHandler, connectionRepository, mockEnvironment);
            ReflectionTestUtils.setField(strictController, "allowTestTokens", false);
            MockMvc strictMvc = MockMvcBuilders.standaloneSetup(strictController)
                    .setControllerAdvice(new OtaExceptionHandler()).build();

            when(eventHandler.handleBookingEvent(any())).thenReturn(
                    new OtaProviderEventHandler.OtaBookingEventResult("CONFIRMED", "CH-1", "ML-1", 2,
                            List.of("T1", "T2"), null, null));

            strictMvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "super-secret-token-xyz")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callback("TEST")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("T059: unresolvable credential reference fails closed even in TEST environment")
        void unresolvableCredentialReference_failsClosedEvenInTest() throws Exception {
            when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                    .thenReturn(Optional.of(OtaChannelConnection.builder().id(1L).providerCode(OtaProvider.KLOOK)
                            .environment(OtaEnvironment.TEST).credentialReference("NON_EXISTENT_ENV_VAR")
                            .status("ACTIVE").build()));

            OtaProviderEventController strictController = new OtaProviderEventController(eventHandler, connectionRepository, mockEnvironment);
            ReflectionTestUtils.setField(strictController, "allowTestTokens", false);
            MockMvc strictMvc = MockMvcBuilders.standaloneSetup(strictController)
                    .setControllerAdvice(new OtaExceptionHandler()).build();

            strictMvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "any-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callback("TEST")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            verify(eventHandler, never()).handleBookingEvent(any());
        }

        private String callback(String environment) {
            return """
                    {
                        "provider": "KLOOK",
                        "environment": "%s",
                        "externalBookingReference": "ext-123",
                        "providerProductReference": "prod-1",
                        "providerOptionReference": "opt-1",
                        "participantCount": 2,
                        "acceptedAmountMinor": 200000,
                        "currencyCode": "VND"
                    }
                    """.formatted(environment);
        }

        @Test
        @DisplayName("Invalid request payload (negative participants) returns 400 Bad Request")
        void invalidParticipantCount_returns400() throws Exception {
            String body = """
                    {
                        "provider": "KLOOK",
                        "environment": "TEST",
                        "externalBookingReference": "ext-123",
                        "providerProductReference": "prod-1",
                        "providerOptionReference": "opt-1",
                        "participantCount": -1,
                        "acceptedAmountMinor": 200000,
                        "currencyCode": "VND"
                    }
                    """;

            mvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verify(eventHandler, never()).handleBookingEvent(any());
        }

        @Test
        @DisplayName("Missing currency code returns 400 Bad Request")
        void missingCurrency_returns400() throws Exception {
            String body = """
                    {
                        "provider": "KLOOK",
                        "environment": "TEST",
                        "externalBookingReference": "ext-123",
                        "providerProductReference": "prod-1",
                        "providerOptionReference": "opt-1",
                        "participantCount": 2,
                        "acceptedAmountMinor": 200000
                    }
                    """;

            mvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("Safe Acknowledgement and Financial Verification Contract")
    class AcknowledgementAndFinancialTests {

        @Test
        @DisplayName("Authenticated callback with full accepted payment returns safe acknowledgement with tickets")
        void fullPayment_returnsSafeAcknowledgementWithTickets() throws Exception {
            when(eventHandler.handleBookingEvent(any(OtaBookingEventCommand.class))).thenReturn(
                    OtaBookingEventResult.confirmed(
                            "ota-booking-uuid-001",
                            "OTA-mland-001",
                            2,
                            List.of("ticket-uuid-001", "ticket-uuid-002")
                    )
            );

            String body = """
                    {
                        "provider": "KLOOK",
                        "environment": "TEST",
                        "externalBookingReference": "klook-order-999",
                        "providerProductReference": "prod-1",
                        "providerOptionReference": "opt-1",
                        "participantCount": 2,
                        "acceptedAmountMinor": 200000,
                        "currencyCode": "VND",
                        "correlationReference": "cor-999"
                    }
                    """;

            mvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.externalBookingReference").value("klook-order-999"))
                    .andExpect(jsonPath("$.channelBookingReference").value("ota-booking-uuid-001"))
                    .andExpect(jsonPath("$.participantCount").value(2))
                    .andExpect(jsonPath("$.ticketReferences.length()").value(2))
                    .andExpect(jsonPath("$.ticketReferences[0]").value("ticket-uuid-001"))
                    .andExpect(jsonPath("$.ticketReferences[1]").value("ticket-uuid-002"))
                    .andExpect(jsonPath("$.correlationReference").value("cor-999"))
                    .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        }

        @Test
        @DisplayName("Partial or mismatched payment returns safe rejection with ZERO tickets issued")
        void partialPayment_returnsSafeRejectionWithZeroTickets() throws Exception {
            when(eventHandler.handleBookingEvent(any(OtaBookingEventCommand.class))).thenReturn(
                    OtaBookingEventResult.rejected("PARTIAL_PAYMENT_REJECTED", "rec-item-001")
            );

            String body = """
                    {
                        "provider": "KLOOK",
                        "environment": "TEST",
                        "externalBookingReference": "klook-order-partial",
                        "providerProductReference": "prod-1",
                        "providerOptionReference": "opt-1",
                        "participantCount": 2,
                        "acceptedAmountMinor": 50000,
                        "currencyCode": "VND"
                    }
                    """;

            mvc.perform(post("/api/v1/ota-provider/booking-events")
                            .header("X-Ota-Signature", "test-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.externalBookingReference").value("klook-order-partial"))
                    .andExpect(jsonPath("$.rejectionReason").value("PARTIAL_PAYMENT_REJECTED"))
                    .andExpect(jsonPath("$.ticketReferences").isEmpty())
                    .andExpect(jsonPath("$.channelBookingReference").doesNotExist());
        }
    }
}

