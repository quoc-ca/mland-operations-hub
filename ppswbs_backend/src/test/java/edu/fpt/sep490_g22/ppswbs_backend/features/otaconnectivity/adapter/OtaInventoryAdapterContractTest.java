package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChangeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T017: OTA Inventory Adapter Contract Tests")
class OtaInventoryAdapterContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AtomicReference<OtaTransportRequest> capturedRequest;
    private AtomicReference<OtaTransportResponse> stubbedResponse;
    private AtomicReference<Exception> stubbedException;
    private OtaProviderTransport transport;

    @BeforeEach
    void setUp() {
        capturedRequest = new AtomicReference<>();
        stubbedResponse = new AtomicReference<>(new OtaTransportResponse(200, "{\"status\":\"OK\"}", Map.of("X-Correlation-Id", "corr-klook-001")));
        stubbedException = new AtomicReference<>();

        transport = request -> {
            capturedRequest.set(request);
            if (stubbedException.get() != null) {
                throw stubbedException.get();
            }
            return stubbedResponse.get();
        };
    }

    private OtaChannelMapping createMapping(OtaProvider provider, OtaMappingStatus status) {
        return OtaChannelMapping.builder()
                .id(101L)
                .connection(OtaChannelConnection.builder()
                        .providerCode(provider)
                        .credentialReference("opaque-vault-cred-ref-1")
                        .build())
                .sessionReference("session-ref-001")
                .locationReference("loc-001")
                .offerReference("offer-ref-001")
                .externalProductId("ext-prod-999")
                .externalOptionId("ext-opt-111")
                .status(status)
                .build();
    }

    private OtaOutboundChange createChange(OtaChangeKind kind, ObjectNode snapshot) {
        return OtaOutboundChange.builder()
                .id(501L)
                .sourceVersion(10L)
                .sourceChangeReference("chg-ref-10")
                .changeKind(kind)
                .canonicalSnapshot(snapshot)
                .status("QUEUED")
                .acceptedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Contract: Schedule, Availability, and Sales-Close publication")
    class PublicationContracts {

        @Test
        @DisplayName("Schedule publication sends approved fields to provider schedule endpoint")
        void scheduleDelivery_sendsApprovedFields() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.BOOKING_ENABLED);

            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-ref-10");
            snapshot.put("scheduledStart", "2026-11-01T10:00:00Z");
            snapshot.put("scheduledEnd", "2026-11-01T12:00:00Z");
            snapshot.put("locationReference", "loc-001");

            OtaOutboundChange change = createChange(OtaChangeKind.SCHEDULE, snapshot);

            OtaDeliveryResult result = adapter.deliver(mapping, change, 1);

            assertThat(result.outcome()).isEqualTo(OtaDeliveryOutcome.DELIVERED);
            assertThat(result.failureCategory()).isNull();
            assertThat(result.correlationReference()).isEqualTo("corr-klook-001");

            OtaTransportRequest request = capturedRequest.get();
            assertThat(request).isNotNull();
            assertThat(request.endpointUrl()).contains("/schedule");
            assertThat(request.headers()).containsEntry("X-Provider-Auth-Ref", "opaque-vault-cred-ref-1");
            assertThat(request.bodyPayload()).contains("ext-prod-999", "ext-opt-111", "2026-11-01T10:00:00Z");
        }

        @Test
        @DisplayName("Availability publication sends sellable availability")
        void availabilityDelivery_sendsSellableAvailability() {
            GetYourGuideInventoryAdapter adapter = new GetYourGuideInventoryAdapter(transport, objectMapper, 3);
            OtaChannelMapping mapping = createMapping(OtaProvider.GETYOURGUIDE, OtaMappingStatus.BOOKING_ENABLED);

            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 11L);
            snapshot.put("sourceChangeReference", "chg-ref-11");
            snapshot.put("sellableAvailability", 8);

            OtaOutboundChange change = createChange(OtaChangeKind.AVAILABILITY, snapshot);

            OtaDeliveryResult result = adapter.deliver(mapping, change, 1);

            assertThat(result.outcome()).isEqualTo(OtaDeliveryOutcome.DELIVERED);
            assertThat(capturedRequest.get().endpointUrl()).contains("/availability");
            assertThat(capturedRequest.get().bodyPayload()).contains("sellableAvailability", "8");
        }

        @Test
        @DisplayName("Sales-close publication sends sales-closed state")
        void salesCloseDelivery_sendsSalesClosedState() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.SALES_CLOSED);

            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 12L);
            snapshot.put("sourceChangeReference", "chg-ref-12");
            snapshot.put("saleState", "SALES_CLOSED");

            OtaOutboundChange change = createChange(OtaChangeKind.SALE_STATE, snapshot);

            OtaDeliveryResult result = adapter.deliver(mapping, change, 1);

            assertThat(result.outcome()).isEqualTo(OtaDeliveryOutcome.DELIVERED);
            assertThat(capturedRequest.get().endpointUrl()).contains("/sales-state");
            assertThat(capturedRequest.get().bodyPayload()).contains("SALES_CLOSED");
        }
    }

    @Nested
    @DisplayName("Contract: Sanitized delivery outcome and allowlist enforcement")
    class SanitizationAndAllowlistContracts {

        @Test
        @DisplayName("Sanitizes dirty correlation reference and avoids credential leakage")
        void sanitizesDirtyCorrelationAndExcludesSecrets() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            stubbedResponse.set(new OtaTransportResponse(200, "{\"secret_token\":\"xyz999\"}",
                    Map.of("X-Correlation-Id", "<script>alert('bad');</script>")));

            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.BOOKING_ENABLED);
            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);

            OtaDeliveryResult result = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 1);

            assertThat(result.outcome()).isEqualTo(OtaDeliveryOutcome.DELIVERED);
            assertThat(result.correlationReference()).doesNotContain("<script>", "alert");
            assertThat(result.correlationReference()).startsWith("corr-sanitized-");
        }

        @Test
        @DisplayName("Default-deny: Rejects snapshot containing customer, contact, or forbidden fields")
        void defaultDeny_rejectsForbiddenCustomerFields() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.BOOKING_ENABLED);

            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);
            snapshot.put("customerEmail", "leak@example.com"); // Forbidden

            OtaDeliveryResult result = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 1);

            assertThat(result.outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
            assertThat(result.failureCategory()).isEqualTo("ALLOWLIST_VIOLATION");
            assertThat(capturedRequest.get()).isNull(); // Never dispatched to network
        }

        @Test
        @DisplayName("Rejects disabled mapping without network dispatch")
        void rejectsDisabledMapping() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.DISABLED);

            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);

            OtaDeliveryResult result = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 1);

            assertThat(result.outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
            assertThat(result.failureCategory()).isEqualTo("MAPPING_DISABLED");
            assertThat(capturedRequest.get()).isNull();
        }

        @Test
        @DisplayName("Rejects unsupported provider code")
        void rejectsUnsupportedProvider() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            OtaChannelMapping mapping = createMapping(OtaProvider.GETYOURGUIDE, OtaMappingStatus.BOOKING_ENABLED);

            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);

            OtaDeliveryResult result = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 1);

            assertThat(result.outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
            assertThat(result.failureCategory()).isEqualTo("UNSUPPORTED_PROVIDER");
            assertThat(capturedRequest.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Contract: Transient retry and exhausted-retry behavior")
    class RetryContracts {

        @Test
        @DisplayName("Transient 5xx error on attempt < maxRetries returns RETRYABLE")
        void transient5xx_underMaxRetries_returnsRetryable() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            stubbedResponse.set(new OtaTransportResponse(503, "Service Unavailable", Map.of()));

            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.BOOKING_ENABLED);
            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);

            OtaDeliveryResult attempt1 = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 1);
            assertThat(attempt1.outcome()).isEqualTo(OtaDeliveryOutcome.RETRYABLE);
            assertThat(attempt1.failureCategory()).isEqualTo("PROVIDER_5XX");

            OtaDeliveryResult attempt2 = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 2);
            assertThat(attempt2.outcome()).isEqualTo(OtaDeliveryOutcome.RETRYABLE);
        }

        @Test
        @DisplayName("Transient network IO exception under maxRetries returns RETRYABLE")
        void transientIoException_underMaxRetries_returnsRetryable() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            stubbedException.set(new IOException("Connection reset by peer"));

            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.BOOKING_ENABLED);
            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);

            OtaDeliveryResult attempt1 = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 1);
            assertThat(attempt1.outcome()).isEqualTo(OtaDeliveryOutcome.RETRYABLE);
            assertThat(attempt1.failureCategory()).isEqualTo("TRANSIENT_IO_ERROR");
        }

        @Test
        @DisplayName("Exhausted retries: 5xx on attempt >= maxRetries returns EXHAUSTED")
        void exhausted5xx_atMaxRetries_returnsExhausted() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            stubbedResponse.set(new OtaTransportResponse(504, "Gateway Timeout", Map.of()));

            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.BOOKING_ENABLED);
            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);

            OtaDeliveryResult attempt3 = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 3);
            assertThat(attempt3.outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
            assertThat(attempt3.failureCategory()).isEqualTo("PROVIDER_5XX");
        }

        @Test
        @DisplayName("Permanent 4xx client/auth error returns EXHAUSTED immediately without retry")
        void permanent4xx_returnsExhaustedImmediately() {
            KlookInventoryAdapter adapter = new KlookInventoryAdapter(transport, objectMapper, 3);
            stubbedResponse.set(new OtaTransportResponse(401, "Unauthorized", Map.of()));

            OtaChannelMapping mapping = createMapping(OtaProvider.KLOOK, OtaMappingStatus.BOOKING_ENABLED);
            ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
            snapshot.put("sourceVersion", 10L);
            snapshot.put("sourceChangeReference", "chg-10");
            snapshot.put("sellableAvailability", 5);

            OtaDeliveryResult attempt1 = adapter.deliver(mapping, createChange(OtaChangeKind.AVAILABILITY, snapshot), 1);
            assertThat(attempt1.outcome()).isEqualTo(OtaDeliveryOutcome.EXHAUSTED);
            assertThat(attempt1.failureCategory()).isEqualTo("CLIENT_ERROR_4XX");
        }
    }

    @Nested
    @DisplayName("Contract: Registry and Provider implementations")
    class RegistryContracts {

        @ParameterizedTest
        @EnumSource(OtaProvider.class)
        @DisplayName("Registry finds adapter for every supported provider")
        void registryResolvesAllProviders(OtaProvider provider) {
            KlookInventoryAdapter klook = new KlookInventoryAdapter(transport, objectMapper);
            GetYourGuideInventoryAdapter gyg = new GetYourGuideInventoryAdapter(transport, objectMapper);
            OtaInventoryAdapterRegistry registry = new OtaInventoryAdapterRegistry(java.util.List.of(klook, gyg));

            OtaInventoryAdapter resolved = registry.getRequiredAdapter(provider);
            assertThat(resolved).isNotNull();
            assertThat(resolved.supportedProvider()).isEqualTo(provider);
        }
    }
}
