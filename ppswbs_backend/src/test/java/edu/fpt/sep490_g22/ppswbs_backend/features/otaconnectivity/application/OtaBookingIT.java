package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingReadinessService.ReadinessValidationResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventReceipt;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.AuthenticatedOtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaInboundEventService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaBookingTicketLink;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaBookingTicketLinkRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.AcceptOtaBookingCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OtaBookingCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("T027: OTA Booking Idempotency, Concurrency, and Payment Evidence IT")
class OtaBookingIT {

    @Mock private OtaChannelConnectionRepository connectionRepository;
    @Mock private OtaChannelMappingRepository mappingRepository;
    @Mock private OtaChannelBookingRepository bookingRepository;
    @Mock private OtaBookingTicketLinkRepository ticketLinkRepository;
    @Mock private OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    @Mock private OtaReconciliationWorkItemRepository reconciliationRepository;
    @Mock private OtaInboundEventService inboundEventService;
    @Mock private OtaBookingReadinessService readinessService;
    @Mock private WorkshopOperationsFacade workshopOperationsFacade;

    private OtaProviderEventHandler eventHandler;

    private OtaChannelConnection connection;
    private OtaChannelMapping mapping;
    private OtaChannelPriceSnapshot priceSnapshot;

    private final Map<String, OtaChannelBooking> bookingStore = new HashMap<>();
    private final List<OtaBookingTicketLink> ticketLinks = new ArrayList<>();
    private final List<OtaCustomerPaymentEvidence> paymentStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookingStore.clear();
        ticketLinks.clear();
        paymentStore.clear();

        eventHandler = new OtaProviderEventHandler(
                connectionRepository,
                mappingRepository,
                bookingRepository,
                ticketLinkRepository,
                customerPaymentRepository,
                reconciliationRepository,
                inboundEventService,
                readinessService,
                workshopOperationsFacade
        );

        connection = OtaChannelConnection.builder()
                .id(1L)
                .providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST)
                .status("ACTIVE")
                .credentialReference("klook-cred-123")
                .build();

        mapping = OtaChannelMapping.builder()
                .id(10L)
                .connection(connection)
                .offerReference("offer-ring-basic")
                .sessionReference(UUID.randomUUID().toString())
                .locationReference("1001")
                .externalProductId("klook-ring-001")
                .externalOptionId("opt-single")
                .status(OtaMappingStatus.BOOKING_ENABLED)
                .build();

        priceSnapshot = OtaChannelPriceSnapshot.providerConfirmed(
                mapping,
                150_000L,
                120_000L,
                30_000L,
                "VND",
                "evidence-price-klook-001",
                Instant.now().minusSeconds(3600),
                null
        );

        when(connectionRepository.findByProviderCodeAndEnvironment(OtaProvider.KLOOK, OtaEnvironment.TEST))
                .thenReturn(Optional.of(connection));
        when(mappingRepository.findByConnectionIdAndExternalProductIdAndExternalOptionId(1L, "klook-ring-001", "opt-single"))
                .thenReturn(Optional.of(mapping));

        when(inboundEventService.fingerprintCanonicalAllowlistedPayload(any())).thenReturn("a".repeat(64));
        when(inboundEventService.receive(any(AuthenticatedOtaInboundEvent.class)))
                .thenReturn(new OtaInboundEventReceipt(100L, false, "RECEIVED", null));

        when(bookingRepository.save(any(OtaChannelBooking.class))).thenAnswer(invocation -> {
            OtaChannelBooking b = invocation.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 101L);
            bookingStore.put(b.getExternalBookingReference(), b);
            return b;
        });

        when(bookingRepository.findByMappingIdAndExternalBookingReference(eq(10L), any())).thenAnswer(invocation -> {
            String ref = invocation.getArgument(1);
            return Optional.ofNullable(bookingStore.get(ref));
        });

        when(customerPaymentRepository.save(any(OtaCustomerPaymentEvidence.class))).thenAnswer(invocation -> {
            OtaCustomerPaymentEvidence p = invocation.getArgument(0);
            paymentStore.add(p);
            return p;
        });

        when(ticketLinkRepository.save(any(OtaBookingTicketLink.class))).thenAnswer(invocation -> {
            OtaBookingTicketLink t = invocation.getArgument(0);
            ticketLinks.add(t);
            return t;
        });

        when(ticketLinkRepository.findAllByChannelBookingIdOrderByParticipantOrdinalAsc(any())).thenAnswer(invocation ->
                new ArrayList<>(ticketLinks));
    }

    @Nested
    @DisplayName("User Story 1: Happy Path & N-Participant Ticket Issuance")
    class HappyPathTests {

        @Test
        @DisplayName("Valid full-payment booking for 3 participants reserves capacity, captures booking, and issues 3 tickets")
        void fullPayment_issuesNTicketsAndCapturesBooking() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), eq("VND"), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));

            List<UUID> operationsTickets = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            when(workshopOperationsFacade.acceptOtaBooking(any(AcceptOtaBookingCommand.class)))
                    .thenReturn(OtaBookingCapacityOutcome.accepted(operationsTickets));

            OtaBookingEventCommand cmd = new OtaBookingEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-001",
                    "ext-book-777",
                    "klook-ring-001",
                    "opt-single",
                    3,
                    150_000L,
                    "VND",
                    "pay-ext-001",
                    "cor-001"
            );

            OtaBookingEventResult result = eventHandler.handleBookingEvent(cmd);

            assertThat(result.status()).isEqualTo("CONFIRMED");
            assertThat(result.participantCount()).isEqualTo(3);
            assertThat(result.ticketReferences()).hasSize(3);
            assertThat(ticketLinks).hasSize(3);
            assertThat(ticketLinks.get(0).getOperationsTicketReference()).isEqualTo(operationsTickets.get(0).toString());
            assertThat(ticketLinks.get(1).getOperationsTicketReference()).isEqualTo(operationsTickets.get(1).toString());
            assertThat(ticketLinks.get(2).getOperationsTicketReference()).isEqualTo(operationsTickets.get(2).toString());

            assertThat(paymentStore).hasSize(1);
            assertThat(paymentStore.get(0).getEvidenceStatus()).isEqualTo(OtaCustomerPaymentState.PAID_ON_CHANNEL);
            assertThat(paymentStore.get(0).getAmountMinor()).isEqualTo(150_000L);
            assertThat(paymentStore.get(0).getCurrencyCode()).isEqualTo("VND");

            verify(inboundEventService).recordOutcome(eq(100L), any());
        }
    }

    @Nested
    @DisplayName("Idempotency: Duplicate External Reference & Key Replay")
    class IdempotencyTests {

        @Test
        @DisplayName("Duplicate external booking reference returns original booking and tickets without duplicate Mland booking")
        void duplicateExternalReference_returnsOriginalBookingAndTickets() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), eq("VND"), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));

            List<UUID> operationsTickets = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(workshopOperationsFacade.acceptOtaBooking(any(AcceptOtaBookingCommand.class)))
                    .thenReturn(OtaBookingCapacityOutcome.accepted(operationsTickets));

            OtaBookingEventCommand cmd = new OtaBookingEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-001",
                    "ext-book-duplicate",
                    "klook-ring-001",
                    "opt-single",
                    2,
                    150_000L,
                    "VND",
                    "pay-ext-001",
                    "cor-001"
            );

            // First call
            OtaBookingEventResult firstResult = eventHandler.handleBookingEvent(cmd);
            assertThat(firstResult.status()).isEqualTo("CONFIRMED");

            // Replay call with same external reference
            OtaBookingEventResult replayResult = eventHandler.handleBookingEvent(cmd);

            assertThat(replayResult.status()).isEqualTo("CONFIRMED");
            assertThat(replayResult.channelBookingReference()).isEqualTo(firstResult.channelBookingReference());
            assertThat(replayResult.ticketReferences()).containsExactlyElementsOf(firstResult.ticketReferences());

            // Operations facade called only ONCE
            verify(workshopOperationsFacade, times(1)).acceptOtaBooking(any());
            // Booking saved only ONCE
            verify(bookingRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("Payment Evidence: Missing, Partial, and Currency Mismatch")
    class PaymentEvidenceValidationTests {

        @Test
        @DisplayName("Partial payment (accepted amount < retail amount) is rejected with ZERO tickets and reconciliation work item")
        void partialPayment_isRejected() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(50_000L), eq("VND"), any()))
                    .thenReturn(ReadinessValidationResult.rejected("PARTIAL_PAYMENT_REJECTED"));

            OtaBookingEventCommand cmd = new OtaBookingEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-002",
                    "ext-book-partial",
                    "klook-ring-001",
                    "opt-single",
                    2,
                    50_000L,
                    "VND",
                    "pay-ext-002",
                    "cor-002"
            );

            OtaBookingEventResult result = eventHandler.handleBookingEvent(cmd);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("PARTIAL_PAYMENT_REJECTED");
            assertThat(result.ticketReferences()).isEmpty();

            verify(workshopOperationsFacade, never()).acceptOtaBooking(any());
            verify(bookingRepository, never()).save(any());
            verify(customerPaymentRepository, never()).save(any());
            verify(reconciliationRepository, times(1)).save(any(OtaReconciliationWorkItem.class));
        }

        @Test
        @DisplayName("Currency mismatch (e.g. USD vs expected VND) is rejected with ZERO tickets")
        void currencyMismatch_isRejected() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), eq("USD"), any()))
                    .thenReturn(ReadinessValidationResult.rejected("CURRENCY_MISMATCH"));

            OtaBookingEventCommand cmd = new OtaBookingEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-003",
                    "ext-book-currency-mismatch",
                    "klook-ring-001",
                    "opt-single",
                    2,
                    150_000L,
                    "USD",
                    "pay-ext-003",
                    "cor-003"
            );

            OtaBookingEventResult result = eventHandler.handleBookingEvent(cmd);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("CURRENCY_MISMATCH");
            assertThat(result.ticketReferences()).isEmpty();

            verify(workshopOperationsFacade, never()).acceptOtaBooking(any());
            verify(bookingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Mapping State and Competing Final Capacity")
    class LifecycleAndCapacityTests {

        @Test
        @DisplayName("Booking on disabled or non-booking-enabled mapping is rejected")
        void disabledMapping_isRejected() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), eq("VND"), any()))
                    .thenReturn(ReadinessValidationResult.rejected("MAPPING_DISABLED"));

            OtaBookingEventCommand cmd = new OtaBookingEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-004",
                    "ext-book-disabled",
                    "klook-ring-001",
                    "opt-single",
                    1,
                    150_000L,
                    "VND",
                    "pay-ext-004",
                    "cor-004"
            );

            OtaBookingEventResult result = eventHandler.handleBookingEvent(cmd);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).contains("MAPPING_DISABLED");
            assertThat(result.ticketReferences()).isEmpty();

            verify(workshopOperationsFacade, never()).acceptOtaBooking(any());
        }

        @Test
        @DisplayName("Competing capacity rejection from Operations returns REJECTED with ZERO tickets and no orphaned booking")
        void competingCapacityRejection_returnsRejectedWithZeroTickets() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), eq("VND"), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));

            when(workshopOperationsFacade.acceptOtaBooking(any(AcceptOtaBookingCommand.class)))
                    .thenReturn(OtaBookingCapacityOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY));

            OtaBookingEventCommand cmd = new OtaBookingEventCommand(
                    OtaProvider.KLOOK,
                    OtaEnvironment.TEST,
                    "event-005",
                    "ext-book-no-capacity",
                    "klook-ring-001",
                    "opt-single",
                    4,
                    150_000L,
                    "VND",
                    "pay-ext-005",
                    "cor-005"
            );

            OtaBookingEventResult result = eventHandler.handleBookingEvent(cmd);

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("INSUFFICIENT_CAPACITY");
            assertThat(result.ticketReferences()).isEmpty();

            verify(bookingRepository, never()).save(any());
            verify(customerPaymentRepository, never()).save(any());
            verify(ticketLinkRepository, never()).save(any());
        }
    }
}
