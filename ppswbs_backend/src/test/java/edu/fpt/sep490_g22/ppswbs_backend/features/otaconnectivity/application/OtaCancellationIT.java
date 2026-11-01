package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationConsequenceService.CancellationConsequenceResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.AuthenticatedOtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaInboundEventService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T034: OTA Cancellation and Refund Lifecycle IT")
class OtaCancellationIT {

    @Mock private OtaChannelConnectionRepository connectionRepository;
    @Mock private OtaChannelMappingRepository mappingRepository;
    @Mock private OtaChannelBookingRepository bookingRepository;
    @Mock private OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    @Mock private OtaReconciliationWorkItemRepository reconciliationRepository;
    @Mock private OtaInboundEventService inboundEventService;
    @Mock private OtaCancellationConsequenceService consequenceService;

    private OtaCancellationService cancellationService;

    private OtaChannelConnection connection;
    private OtaChannelMapping mapping;
    private OtaChannelBooking booking;

    private final Map<String, OtaChannelBooking> bookingStore = new HashMap<>();
    private final List<OtaCustomerPaymentEvidence> paymentEvidences = new ArrayList<>();
    private final List<OtaReconciliationWorkItem> workItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookingStore.clear();
        paymentEvidences.clear();
        workItems.clear();

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
                .status("ACTIVE")
                .build();

        mapping = OtaChannelMapping.builder()
                .id(10L)
                .connection(connection)
                .offerReference("offer-001")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("1001")
                .externalProductId("prod-001")
                .externalOptionId("opt-001")
                .status(OtaMappingStatus.BOOKING_ENABLED)
                .build();

        booking = OtaChannelBooking.capture(
                mapping,
                "ext-book-canc-001",
                "CONFIRMED",
                2,
                "OTA-mland-12345",
                "KLOOK",
                "POLICY_DEFAULT",
                "1.0",
                null,
                "Asia/Ho_Chi_Minh",
                150_000L,
                120_000L,
                30_000L,
                0L,
                150_000L,
                "VND"
        );
        ReflectionTestUtils.setField(booking, "id", 1001L);
        bookingStore.put(booking.getExternalBookingReference(), booking);

        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(connection));

        when(bookingRepository.findAll()).thenAnswer(inv -> new ArrayList<>(bookingStore.values()));
        when(bookingRepository.save(any(OtaChannelBooking.class))).thenAnswer(inv -> {
            OtaChannelBooking b = inv.getArgument(0);
            bookingStore.put(b.getExternalBookingReference(), b);
            return b;
        });

        when(customerPaymentRepository.save(any(OtaCustomerPaymentEvidence.class))).thenAnswer(inv -> {
            OtaCustomerPaymentEvidence p = inv.getArgument(0);
            paymentEvidences.add(p);
            return p;
        });

        when(reconciliationRepository.save(any(OtaReconciliationWorkItem.class))).thenAnswer(inv -> {
            OtaReconciliationWorkItem w = inv.getArgument(0);
            workItems.add(w);
            return w;
        });

        when(inboundEventService.fingerprintCanonicalAllowlistedPayload(any())).thenReturn("b".repeat(64));
        when(inboundEventService.receive(any(AuthenticatedOtaInboundEvent.class)))
                .thenReturn(new OtaInboundEventReceipt(200L, false, "RECEIVED", null));

        when(consequenceService.applyConfirmedConsequence(any(), any(), any()))
                .thenReturn(CancellationConsequenceResult.success());
    }

    @Nested
    @DisplayName("Cancellation Request Lifecycle")
    class CancellationRequestTests {

        @Test
        @DisplayName("Cancellation request transitions booking to CANCELLATION_REQUESTED and records REFUND_REQUESTED without invoking Operations")
        void cancellationRequested_doesNotInvokeOperations() {
            OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-canc-req-1",
                    "ext-book-canc-001",
                    "REQUESTED",
                    150_000L,
                    "VND",
                    "Customer requested cancellation on Klook",
                    "cor-canc-1"
            );

            OtaCancellationEventResult result = cancellationService.handleCancellationEvent(cmd);

            assertThat(result.status()).isEqualTo("REQUEST_RECORDED");
            assertThat(booking.getProviderStatus()).isEqualTo("CANCELLATION_REQUESTED");

            assertThat(paymentEvidences).hasSize(1);
            assertThat(paymentEvidences.get(0).getEvidenceStatus()).isEqualTo(OtaCustomerPaymentState.REFUND_REQUESTED);

            // Crucial architectural rule: a request NEVER releases inventory or invokes Operations!
            verify(consequenceService, never()).applyConfirmedConsequence(any(), any(), any());
        }

        @Test
        @DisplayName("Duplicate cancellation request is idempotent")
        void duplicateCancellationRequest_isIdempotent() {
            OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-canc-req-1",
                    "ext-book-canc-001",
                    "REQUESTED",
                    150_000L,
                    "VND",
                    "Customer requested cancellation on Klook",
                    "cor-canc-1"
            );

            cancellationService.handleCancellationEvent(cmd);
            OtaCancellationEventResult result2 = cancellationService.handleCancellationEvent(cmd);

            assertThat(result2.status()).isEqualTo("REQUEST_RECORDED");
            verify(consequenceService, never()).applyConfirmedConsequence(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Confirmed Cancellation Lifecycle")
    class ConfirmedCancellationTests {

        @Test
        @DisplayName("Confirmed cancellation transitions booking to CANCELLED, records REFUNDED evidence, and invokes Operations consequence once")
        void confirmedCancellation_invokesConsequenceOnce() {
            OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-canc-conf-1",
                    "ext-book-canc-001",
                    "CONFIRMED_CANCELLED",
                    150_000L,
                    "VND",
                    "Klook confirmed cancellation",
                    "cor-canc-2"
            );

            OtaCancellationEventResult result = cancellationService.handleCancellationEvent(cmd);

            assertThat(result.status()).isEqualTo("CONFIRMED");
            assertThat(booking.getProviderStatus()).isEqualTo("CANCELLED");

            assertThat(paymentEvidences).hasSize(1);
            assertThat(paymentEvidences.get(0).getEvidenceStatus()).isEqualTo(OtaCustomerPaymentState.REFUNDED);

            verify(consequenceService, times(1)).applyConfirmedConsequence(eq(booking), any(), eq("cor-canc-2"));
        }

        @Test
        @DisplayName("Duplicate confirmed cancellation is idempotent and does not invoke Operations a second time")
        void duplicateConfirmedCancellation_doesNotInvokeOperationsAgain() {
            OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-canc-conf-1",
                    "ext-book-canc-001",
                    "CONFIRMED_CANCELLED",
                    150_000L,
                    "VND",
                    "Klook confirmed cancellation",
                    "cor-canc-2"
            );

            // First confirmed call
            cancellationService.handleCancellationEvent(cmd);

            // Second confirmed call
            OtaCancellationEventResult result2 = cancellationService.handleCancellationEvent(cmd);

            assertThat(result2.status()).isEqualTo("CONFIRMED");

            // Verify Operations consequence was invoked only ONCE
            verify(consequenceService, times(1)).applyConfirmedConsequence(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Out-of-Order and Invalid Event Routing")
    class OutOfOrderAndInvalidRoutingTests {

        @Test
        @DisplayName("Cancellation for unknown booking reference safely routes to reconciliation queue")
        void unknownBooking_routesToReconciliation() {
            OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-canc-unknown",
                    "ext-book-unknown-999",
                    "CONFIRMED_CANCELLED",
                    150_000L,
                    "VND",
                    "Cancellation for non-existent booking",
                    "cor-canc-unknown"
            );

            OtaCancellationEventResult result = cancellationService.handleCancellationEvent(cmd);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("UNKNOWN_BOOKING_REFERENCE");
            assertThat(result.reconciliationWorkItemReference()).isNotNull();

            assertThat(workItems).hasSize(1);
            assertThat(workItems.get(0).getCategory()).isEqualTo("INBOUND_EVENT");
            assertThat(workItems.get(0).getDecision()).contains("UNKNOWN_BOOKING_REFERENCE");

            verify(consequenceService, never()).applyConfirmedConsequence(any(), any(), any());
        }

        @Test
        @DisplayName("Operations rejection of consequence safely routes to reconciliation work item")
        void operationsRejection_routesToReconciliation() {
            when(consequenceService.applyConfirmedConsequence(any(), any(), any()))
                    .thenReturn(CancellationConsequenceResult.rejected("INVALID_STATE"));

            OtaCancellationEventCommand cmd = new OtaCancellationEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-canc-fail",
                    "ext-book-canc-001",
                    "CONFIRMED_CANCELLED",
                    150_000L,
                    "VND",
                    "Klook confirmed cancellation",
                    "cor-canc-fail"
            );

            OtaCancellationEventResult result = cancellationService.handleCancellationEvent(cmd);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("INVALID_STATE");
            assertThat(workItems).hasSize(1);
            assertThat(workItems.get(0).getDecision()).contains("CONSEQUENCE_REJECTED: INVALID_STATE");
        }
    }
}

