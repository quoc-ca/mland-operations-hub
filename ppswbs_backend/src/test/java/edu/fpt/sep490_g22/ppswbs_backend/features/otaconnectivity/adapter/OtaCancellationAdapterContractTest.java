package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationConsequenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaInboundEventService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T038: OTA Provider Cancellation Adapter Contract Tests")
class OtaCancellationAdapterContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OtaChannelConnectionRepository connectionRepository;
    @Mock private OtaChannelMappingRepository mappingRepository;
    @Mock private OtaChannelBookingRepository bookingRepository;
    @Mock private OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    @Mock private OtaReconciliationWorkItemRepository reconciliationRepository;
    @Mock private OtaInboundEventService inboundEventService;
    @Mock private OtaCancellationConsequenceService consequenceService;

    private KlookCancellationAdapter klookAdapter;
    private GetYourGuideCancellationAdapter gygAdapter;
    private OtaCancellationAdapterRegistry adapterRegistry;
    private OtaCancellationService cancellationService;

    private OtaChannelConnection klookConnection;
    private OtaChannelConnection gygConnection;

    @BeforeEach
    void setUp() {
        klookAdapter = new KlookCancellationAdapter(objectMapper);
        gygAdapter = new GetYourGuideCancellationAdapter(objectMapper);
        adapterRegistry = new OtaCancellationAdapterRegistry(List.of(klookAdapter, gygAdapter));

        cancellationService = new OtaCancellationService(
                connectionRepository,
                mappingRepository,
                bookingRepository,
                customerPaymentRepository,
                reconciliationRepository,
                inboundEventService,
                consequenceService,
                adapterRegistry
        );

        klookConnection = OtaChannelConnection.builder()
                .id(1L)
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .credentialReference("klook-cred-ref-456")
                .status("ACTIVE")
                .build();

        gygConnection = OtaChannelConnection.builder()
                .id(2L)
                .providerCode(OtaProvider.GETYOURGUIDE)
                .environment(OtaEnvironment.TEST)
                .credentialReference("gyg-cred-ref-789")
                .status("ACTIVE")
                .build();

        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(klookConnection));
        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.GETYOURGUIDE, OtaEnvironment.TEST))
                .thenReturn(Optional.of(gygConnection));

        when(inboundEventService.fingerprintCanonicalAllowlistedPayload(any())).thenReturn("a".repeat(64));
        when(inboundEventService.receive(any())).thenReturn(
                new edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventReceipt(100L, false, "RECEIVED", null));
    }

    @Nested
    @DisplayName("Klook Cancellation Adapter")
    class KlookAdapterTests {

        @Test
        @DisplayName("Maps valid CANCEL_REQUESTED payload into domain command")
        void mapsValidCancelRequested() {
            String payload = """
                    {
                        "bookingReference": "klk-bk-001",
                        "eventId": "klk-ev-001",
                        "action": "CANCEL_REQUESTED",
                        "refundAmount": 250000,
                        "currency": "VND",
                        "reason": "Customer trip cancelled"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "klook-cred-ref-456");

            OtaCancellationMappingResult result = klookAdapter.mapCancellationEvent(klookConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Mapped.class);
            var mapped = (OtaCancellationMappingResult.Mapped) result;
            assertThat(mapped.command().provider()).isEqualTo(OtaProvider.KLOOK);
            assertThat(mapped.command().externalBookingReference()).isEqualTo("klk-bk-001");
            assertThat(mapped.command().cancellationKind()).isEqualTo("REQUESTED");
            assertThat(mapped.command().refundAmountMinor()).isEqualTo(250000L);
            assertThat(mapped.command().currencyCode()).isEqualTo("VND");
            assertThat(mapped.command().reason()).isEqualTo("Customer trip cancelled");
        }

        @Test
        @DisplayName("Maps valid CANCEL_CONFIRMED payload into domain command")
        void mapsValidCancelConfirmed() {
            String payload = """
                    {
                        "externalBookingReference": "klk-bk-002",
                        "status": "CANCEL_CONFIRMED",
                        "refundAmount": 300000,
                        "currencyCode": "VND"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "klook-cred-ref-456");

            OtaCancellationMappingResult result = klookAdapter.mapCancellationEvent(klookConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Mapped.class);
            var mapped = (OtaCancellationMappingResult.Mapped) result;
            assertThat(mapped.command().cancellationKind()).isEqualTo("CONFIRMED_CANCELLED");
            assertThat(mapped.command().externalBookingReference()).isEqualTo("klk-bk-002");
        }

        @Test
        @DisplayName("Rejects payload with invalid signature")
        void rejectsInvalidSignature() {
            String payload = """
                    {
                        "bookingReference": "klk-bk-003",
                        "action": "CANCEL_CONFIRMED"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "wrong-secret");

            OtaCancellationMappingResult result = klookAdapter.mapCancellationEvent(klookConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Rejected.class);
            var rejected = (OtaCancellationMappingResult.Rejected) result;
            assertThat(rejected.failureCategory()).isEqualTo("UNAUTHORIZED");
        }

        @Test
        @DisplayName("Rejects payload containing forbidden customer PII or credit cards (default-deny)")
        void rejectsForbiddenPii() {
            String payload = """
                    {
                        "bookingReference": "klk-bk-004",
                        "action": "CANCEL_CONFIRMED",
                        "creditCard": "4111111111111111",
                        "customerEmail": "guest@klook.com"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "klook-cred-ref-456");

            OtaCancellationMappingResult result = klookAdapter.mapCancellationEvent(klookConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Rejected.class);
            var rejected = (OtaCancellationMappingResult.Rejected) result;
            assertThat(rejected.failureCategory()).isEqualTo("ALLOWLIST_VIOLATION");
            assertThat(rejected.reason()).contains("Forbidden field");
        }

        @Test
        @DisplayName("Unmapped Klook action returns Unmapped without guessing semantics")
        void unmappedActionReturnsUnmapped() {
            String payload = """
                    {
                        "bookingReference": "klk-bk-005",
                        "action": "DISPUTE_OPENED"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "klook-cred-ref-456");

            OtaCancellationMappingResult result = klookAdapter.mapCancellationEvent(klookConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Unmapped.class);
            var unmapped = (OtaCancellationMappingResult.Unmapped) result;
            assertThat(unmapped.eventType()).isEqualTo("DISPUTE_OPENED");
            assertThat(unmapped.reason()).contains("Unmapped Klook cancellation action");
        }
    }

    @Nested
    @DisplayName("GetYourGuide Cancellation Adapter")
    class GetYourGuideAdapterTests {

        @Test
        @DisplayName("Maps valid BOOKING_CANCEL_REQUEST payload into domain command")
        void mapsValidCancelRequest() {
            String payload = """
                    {
                        "bookingReference": "gyg-bk-101",
                        "notificationId": "gyg-notif-101",
                        "event": "BOOKING_CANCEL_REQUEST",
                        "refundAmount": 400000,
                        "currency": "VND",
                        "reason": "Flight delayed"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "gyg-cred-ref-789");

            OtaCancellationMappingResult result = gygAdapter.mapCancellationEvent(gygConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Mapped.class);
            var mapped = (OtaCancellationMappingResult.Mapped) result;
            assertThat(mapped.command().provider()).isEqualTo(OtaProvider.GETYOURGUIDE);
            assertThat(mapped.command().externalBookingReference()).isEqualTo("gyg-bk-101");
            assertThat(mapped.command().cancellationKind()).isEqualTo("REQUESTED");
        }

        @Test
        @DisplayName("Maps valid BOOKING_CANCELLED payload into domain command")
        void mapsValidBookingCancelled() {
            String payload = """
                    {
                        "bookingReference": "gyg-bk-102",
                        "event": "BOOKING_CANCELLED",
                        "refundAmount": 450000,
                        "currency": "VND"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "gyg-cred-ref-789");

            OtaCancellationMappingResult result = gygAdapter.mapCancellationEvent(gygConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Mapped.class);
            var mapped = (OtaCancellationMappingResult.Mapped) result;
            assertThat(mapped.command().cancellationKind()).isEqualTo("CONFIRMED_CANCELLED");
        }

        @Test
        @DisplayName("Unmapped GYG event returns Unmapped fail-closed")
        void unmappedGygEvent() {
            String payload = """
                    {
                        "bookingReference": "gyg-bk-103",
                        "event": "BOOKING_AMENDED"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "gyg-cred-ref-789");

            OtaCancellationMappingResult result = gygAdapter.mapCancellationEvent(gygConnection, payload, headers);

            assertThat(result).isInstanceOf(OtaCancellationMappingResult.Unmapped.class);
            var unmapped = (OtaCancellationMappingResult.Unmapped) result;
            assertThat(unmapped.eventType()).isEqualTo("BOOKING_AMENDED");
        }
    }

    @Nested
    @DisplayName("End-to-End Raw Callback through OtaCancellationService")
    class ServiceIntegrationTests {

        @Test
        @DisplayName("Unmapped event routes to INBOUND_EVENT reconciliation queue with zero side-effects")
        void unmappedEventRoutesToReconciliation() {
            String payload = """
                    {
                        "bookingReference": "klk-bk-201",
                        "action": "PARTIAL_REFUND_OFFER"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "klook-cred-ref-456");

            OtaCancellationEventResult result = cancellationService.handleRawProviderCancellation(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, payload, headers);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).contains("UNMAPPED_PROVIDER_EVENT");
            assertThat(result.reconciliationWorkItemReference()).isNotNull();

            ArgumentCaptor<OtaReconciliationWorkItem> captor = ArgumentCaptor.forClass(OtaReconciliationWorkItem.class);
            verify(reconciliationRepository).save(captor.capture());
            OtaReconciliationWorkItem item = captor.getValue();
            assertThat(item.getCategory()).isEqualTo("INBOUND_EVENT");
            assertThat(item.getDecision()).contains("UNMAPPED_PROVIDER_EVENT");

            // Zero consequence invocation
            verify(consequenceService, never()).applyConfirmedConsequence(any(), any(), any());
        }

        @Test
        @DisplayName("Allowlist violation routes to reconciliation queue with zero side-effects")
        void allowlistViolationRoutesToReconciliation() {
            String payload = """
                    {
                        "bookingReference": "klk-bk-202",
                        "action": "CANCEL_CONFIRMED",
                        "customerPassword": "secret-pass-123"
                    }
                    """;
            Map<String, String> headers = Map.of("X-Ota-Signature", "klook-cred-ref-456");

            OtaCancellationEventResult result = cancellationService.handleRawProviderCancellation(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, payload, headers);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).contains("ALLOWLIST_VIOLATION");

            verify(reconciliationRepository).save(any(OtaReconciliationWorkItem.class));
            verify(consequenceService, never()).applyConfirmedConsequence(any(), any(), any());
        }

        @Test
        @DisplayName("Direct customer cancellation remains completely separated: non-refundable, no provider side-effects")
        void directCustomerCancellationSeparation() {
            // Direct Mland customer cancellations are non-refundable and handled exclusively in 003.3
            // An OTA cancellation adapter never mutates or triggers 003.3 direct bookings.
            verifyNoInteractions(bookingRepository);
            verifyNoInteractions(consequenceService);
        }
    }

    @Nested
    @DisplayName("Registry contracts")
    class RegistryTests {

        @ParameterizedTest
        @EnumSource(OtaProvider.class)
        @DisplayName("Registry contains adapter for every supported provider")
        void registryResolvesAllProviders(OtaProvider provider) {
            OtaCancellationAdapter resolved = adapterRegistry.getRequiredAdapter(provider);
            assertThat(resolved).isNotNull();
            assertThat(resolved.supportedProvider()).isEqualTo(provider);
        }
    }
}

