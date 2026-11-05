package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationConsequenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T035: OTA Cancellation Callback Security and Reconciliation-Queue IT")
class OtaCancellationCallbackSecurityIT {

    @Mock private OtaChannelConnectionRepository connectionRepository;
    @Mock private OtaChannelMappingRepository mappingRepository;
    @Mock private OtaChannelBookingRepository bookingRepository;
    @Mock private OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    @Mock private OtaReconciliationWorkItemRepository reconciliationRepository;
    @Mock private OtaInboundEventService inboundEventService;
    @Mock private OtaCancellationConsequenceService consequenceService;

    private OtaCancellationService cancellationService;
    private OtaChannelConnection connection;

    @BeforeEach
    void setUp() {
        cancellationService = new OtaCancellationService(
                connectionRepository,
                mappingRepository,
                bookingRepository,
                customerPaymentRepository,
                reconciliationRepository,
                inboundEventService,
                consequenceService
        );

        connection = OtaChannelConnection.builder()
                .id(1L)
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .credentialReference("klook-cred-sec-123")
                .status("ACTIVE")
                .build();

        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(connection));

        when(inboundEventService.fingerprintCanonicalAllowlistedPayload(any())).thenReturn("c".repeat(64));
        when(inboundEventService.receive(any())).thenReturn(
                new edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventReceipt(300L, false, "RECEIVED", null));
    }

    @Test
    @DisplayName("Unknown provider connection throws IllegalArgumentException without saving inbound event")
    void unknownProviderConnection_isRejected() {
        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.GETYOURGUIDE, OtaEnvironment.PRODUCTION))
                .thenReturn(Optional.empty());

        OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                OtaProvider.GETYOURGUIDE,
                OtaEnvironment.PRODUCTION,
                "ev-unknown",
                "ext-unknown-1",
                "CONFIRMED_CANCELLED",
                100_000L,
                "VND",
                "reason",
                "cor-1"
        );

        assertThatThrownBy(() -> cancellationService.handleCancellationEvent(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTA connection not found");

        verify(reconciliationRepository, never()).save(any());
        verify(consequenceService, never()).applyConfirmedConsequence(any(), any(), any());
    }

    @Test
    @DisplayName("Invalid event with unknown booking reference routes to reconciliation queue and preserves audit")
    void unknownBooking_recordsReconciliationItem() {
        when(bookingRepository.findAll()).thenReturn(List.of());

        OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                OtaProvider.KLOOK,
                OtaEnvironment.TEST,
                "ev-invalid-booking",
                "non-existent-booking-ref",
                "CONFIRMED_CANCELLED",
                100_000L,
                "VND",
                "Customer cancelled on OTA",
                "cor-sec-1"
        );

        OtaCancellationEventResult result = cancellationService.handleCancellationEvent(cmd);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.rejectionReason()).isEqualTo("UNKNOWN_BOOKING_REFERENCE");
        assertThat(result.reconciliationWorkItemReference()).isNotNull();

        ArgumentCaptor<OtaReconciliationWorkItem> captor = ArgumentCaptor.forClass(OtaReconciliationWorkItem.class);
        verify(reconciliationRepository).save(captor.capture());

        OtaReconciliationWorkItem savedItem = captor.getValue();
        assertThat(savedItem.getCategory()).isEqualTo("INBOUND_EVENT");
        assertThat(savedItem.getStatus()).isEqualTo("OPEN");
        assertThat(savedItem.getDecision()).contains("UNKNOWN_BOOKING_REFERENCE");

        verify(consequenceService, never()).applyConfirmedConsequence(any(), any(), any());
    }

    @Test
    @DisplayName("Inbound event persistence boundary contains no raw payload, secret, or customer PII field")
    void persistenceBoundary_hasNoRawPayloadOrSecrets() {
        List<String> declaredFields = List.of(OtaInboundEvent.class.getDeclaredFields())
                .stream()
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertThat(declaredFields)
                .doesNotContain("rawPayload", "requestBody", "secret", "credential", "password", "customerData");
    }
}
