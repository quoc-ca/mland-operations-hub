package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.AuthenticatedOtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaInboundEventService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtaInboundEventServiceTest {

    @Mock private OtaChannelConnectionRepository connectionRepository;
    @Mock private OtaInboundEventRepository inboundRepository;

    private OtaInboundEventService service;

    @BeforeEach
    void setUp() {
        service = new OtaInboundEventService(connectionRepository, inboundRepository, new OtaAuditCorrelationService());
    }

    @Test
    void receivesOnlyCanonicalAuthenticatedEventMetadataAndPersistsReceiptBeforeOutcome() {
        OtaChannelConnection connection = OtaChannelConnection.builder().id(9L).providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST).status("ACTIVE").build();
        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(connection));
        when(inboundRepository.findByConnectionIdAndDedupeKey(org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenReturn(Optional.empty());
        when(inboundRepository.save(any(OtaInboundEvent.class))).thenAnswer(invocation -> {
            OtaInboundEvent event = invocation.getArgument(0);
            event.setId(31L);
            return event;
        });

        OtaInboundEventReceipt receipt = service.receive(event("event-reference-1"));

        assertThat(receipt.duplicate()).isFalse();
        assertThat(receipt.processingStatus()).isEqualTo("RECEIVED");
        ArgumentCaptor<OtaInboundEvent> captor = ArgumentCaptor.forClass(OtaInboundEvent.class);
        verify(inboundRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthenticationResult()).isEqualTo("VERIFIED");
        assertThat(captor.getValue().getPayloadFingerprint()).isEqualTo("a".repeat(64));
        assertThat(captor.getValue().getDedupeKey()).hasSize(64);
    }

    @Test
    void duplicateReturnsPriorSemanticOutcomeWithoutSavingAnotherEvent() {
        OtaChannelConnection connection = OtaChannelConnection.builder().id(9L).providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST).status("ACTIVE").build();
        OtaInboundEvent prior = OtaInboundEvent.builder().id(31L).connection(connection)
                .payloadFingerprint("a".repeat(64)).dedupeKey("b".repeat(64)).eventKind("BOOKING")
                .authenticationResult("VERIFIED").processingStatus("ACCEPTED")
                .outcomeReference("BOOKING-REF-1").build();
        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(connection));
        when(inboundRepository.findByConnectionIdAndDedupeKey(org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenReturn(Optional.of(prior));

        OtaInboundEventReceipt receipt = service.receive(event("event-reference-1"));

        assertThat(receipt.duplicate()).isTrue();
        assertThat(receipt.processingStatus()).isEqualTo("ACCEPTED");
        assertThat(receipt.outcomeReference()).isEqualTo("BOOKING-REF-1");
        verify(inboundRepository, never()).save(any(OtaInboundEvent.class));
    }

    @Test
    void rejectsUncanonicalizedPayloadFingerprintBeforePersistence() {
        assertThatThrownBy(() -> service.receive(new AuthenticatedOtaInboundEvent(OtaProvider.KLOOK,
                OtaEnvironment.TEST, "event-reference-1", "raw-payload", "BOOKING")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(inboundRepository, never()).save(any(OtaInboundEvent.class));
    }

    @Test
    void fingerprintsCanonicalAllowlistedPayloadWithoutRetainingIt() {
        String canonicalPayload = "{\"event\":\"booking\",\"participants\":2}";

        String first = service.fingerprintCanonicalAllowlistedPayload(canonicalPayload);
        String second = service.fingerprintCanonicalAllowlistedPayload(canonicalPayload);

        assertThat(first).hasSize(64).matches("[a-f0-9]{64}").isEqualTo(second)
                .doesNotContain("booking", "participants");
    }

    private static AuthenticatedOtaInboundEvent event(String externalReference) {
        return new AuthenticatedOtaInboundEvent(OtaProvider.KLOOK, OtaEnvironment.TEST,
                externalReference, "a".repeat(64), "BOOKING");
    }
}
