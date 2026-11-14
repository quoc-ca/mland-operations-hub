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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.OtaBookingOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import org.junit.jupiter.api.BeforeEach;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEvent;
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
    @Mock private WorkshopBookingFacade workshopBookingFacade;
    @Mock private OtaPayoutLedgerRecorder payoutLedgerRecorder;

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
                workshopBookingFacade,
                payoutLedgerRecorder
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
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));

            List<UUID> operationsTickets = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            when(workshopBookingFacade.createOrReturnOtaBooking(any()))
                    .thenReturn(OtaBookingOutcome.accepted("OTA-BOOKING-REF", operationsTickets));

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
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));

            List<UUID> operationsTickets = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(workshopBookingFacade.createOrReturnOtaBooking(any()))
                    .thenReturn(OtaBookingOutcome.accepted("OTA-BOOKING-REF", operationsTickets));

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
            verify(workshopBookingFacade, times(1)).createOrReturnOtaBooking(any());
            // Booking saved only ONCE
            verify(bookingRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("Payment Evidence: OTA amount and currency are kept as reported (T113)")
    class PaymentEvidenceValidationTests {

        @Test
        @DisplayName("An amount below Mland's retail price, in another currency, still confirms; amount and currency are stored as reported")
        void amountAndCurrencyDifferingFromRetail_areKeptAsReported() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(50_000L), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));
            List<UUID> operationsTickets = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(workshopBookingFacade.createOrReturnOtaBooking(any()))
                    .thenReturn(OtaBookingOutcome.accepted("OTA-BOOKING-REF", operationsTickets));

            OtaBookingEventResult result = eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-002", "ext-book-other-currency",
                    "klook-ring-001", "opt-single", 2, 50_000L, "USD", "pay-ext-002", "cor-002"));

            assertThat(result.status()).isEqualTo("CONFIRMED");
            assertThat(result.ticketReferences()).hasSize(2);
            assertThat(paymentStore).hasSize(1);
            assertThat(paymentStore.get(0).getAmountMinor()).isEqualTo(50_000L);
            assertThat(paymentStore.get(0).getCurrencyCode()).isEqualTo("USD");
            OtaChannelBooking stored = bookingStore.get("ext-book-other-currency");
            assertThat(stored.getAcceptedAmountMinor()).isEqualTo(50_000L);
            assertThat(stored.getCurrencyCode()).isEqualTo("USD");
            verify(payoutLedgerRecorder).openPositionWithSaleFacts(eq(stored), eq(priceSnapshot), eq("pay-ext-002"),
                    any(), any());
        }

        @Test
        @DisplayName("Missing payment evidence is rejected with ZERO tickets and a reconciliation work item")
        void missingPaymentEvidence_isRejected() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), any(), any()))
                    .thenReturn(ReadinessValidationResult.rejected("MISSING_PAYMENT_EVIDENCE"));

            OtaBookingEventResult result = eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-003", "ext-book-no-payment",
                    "klook-ring-001", "opt-single", 2, null, "VND", null, "cor-003"));

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("MISSING_PAYMENT_EVIDENCE");
            assertThat(result.ticketReferences()).isEmpty();

            verify(workshopBookingFacade, never()).createOrReturnOtaBooking(any());
            verify(bookingRepository, never()).save(any());
            verify(customerPaymentRepository, never()).save(any());
            verify(reconciliationRepository, times(1)).save(any(OtaReconciliationWorkItem.class));
        }
    }

    @Nested
    @DisplayName("T106 / T110 / T111 / T114 / T115: replay, payout position, snapshot, unknown mapping")
    class ConvergenceTests {

        @Test
        @DisplayName("T106: a replayed callback returns the stored confirmation and re-runs nothing")
        void replayedCallback_returnsStoredOutcomeWithoutReprocessing() {
            OtaChannelBooking stored = OtaChannelBooking.capture(mapping, "ext-book-replay", "CONFIRMED", 2,
                    "OTA-BOOKING-REF", "KLOOK", "policy-1", "v1", null, null, 150_000L, 120_000L, 30_000L, null,
                    150_000L, "VND");
            ReflectionTestUtils.setField(stored, "id", 55L);
            when(bookingRepository.findByPublicReference(stored.getPublicReference())).thenReturn(Optional.of(stored));
            when(inboundEventService.receive(any(AuthenticatedOtaInboundEvent.class)))
                    .thenReturn(new OtaInboundEventReceipt(100L, true, "ACCEPTED", stored.getPublicReference()));

            OtaBookingEventResult result = eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-001", "ext-book-replay",
                    "klook-ring-001", "opt-single", 2, 150_000L, "VND", "pay-1", "cor-1"));

            assertThat(result.status()).isEqualTo("CONFIRMED");
            assertThat(result.channelBookingReference()).isEqualTo(stored.getPublicReference());
            verify(readinessService, never()).evaluateBookingReadiness(any(), any(), any());
            verify(workshopBookingFacade, never()).createOrReturnOtaBooking(any());
            verify(reconciliationRepository, never()).save(any(OtaReconciliationWorkItem.class));
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("T106: a replayed rejection returns the stored reason without readiness, work item or booking")
        void replayedRejection_returnsStoredReason() {
            when(inboundEventService.receive(any(AuthenticatedOtaInboundEvent.class)))
                    .thenReturn(new OtaInboundEventReceipt(100L, true, "REJECTED", "NO_ACTIVE_COMMERCIAL_TERMS"));

            OtaBookingEventResult result = eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-001", "ext-book-replay-rejected",
                    "klook-ring-001", "opt-single", 2, 150_000L, "VND", "pay-1", "cor-1"));

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("NO_ACTIVE_COMMERCIAL_TERMS");
            verify(readinessService, never()).evaluateBookingReadiness(any(), any(), any());
            verify(workshopBookingFacade, never()).createOrReturnOtaBooking(any());
            verify(reconciliationRepository, never()).save(any(OtaReconciliationWorkItem.class));
        }

        @Test
        @DisplayName("T111: the callback's policy, cutoff and fee are snapshotted; absent ones come from the mapping's terms, never hard-coded")
        void policyCutoffAndFee_areSnapshotted() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), any(), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));
            when(workshopBookingFacade.createOrReturnOtaBooking(any()))
                    .thenReturn(OtaBookingOutcome.accepted("OTA-BOOKING-REF", List.of(UUID.randomUUID())));
            Instant cutoff = Instant.parse("2027-01-01T01:00:00Z");

            eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-p1", "ext-book-policy", "klook-ring-001",
                    "opt-single", 1, 150_000L, "VND", "pay-p1", "cor-p1",
                    "policy-free-cancel", "2026-11", cutoff, "Asia/Bangkok", 7_000L));
            OtaChannelBooking withCallbackFacts = bookingStore.get("ext-book-policy");
            assertThat(withCallbackFacts.getDisplayedPolicyReference()).isEqualTo("policy-free-cancel");
            assertThat(withCallbackFacts.getDisplayedPolicyVersion()).isEqualTo("2026-11");
            assertThat(withCallbackFacts.getBookingCutoffAt()).isEqualTo(cutoff);
            assertThat(withCallbackFacts.getBookingCutoffTimezone()).isEqualTo("Asia/Bangkok");
            assertThat(withCallbackFacts.getFeeAmountMinor()).isEqualTo(7_000L);

            eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-p2", "ext-book-terms", "klook-ring-001",
                    "opt-single", 1, 150_000L, "VND", "pay-p2", "cor-p2"));
            OtaChannelBooking fromTerms = bookingStore.get("ext-book-terms");
            assertThat(fromTerms.getDisplayedPolicyReference()).isEqualTo("evidence-price-klook-001");
            assertThat(fromTerms.getDisplayedPolicyVersion()).isEqualTo(priceSnapshot.getPublicReference());
            assertThat(fromTerms.getBookingCutoffAt()).isNull();
            assertThat(fromTerms.getBookingCutoffTimezone()).isNull();
            assertThat(fromTerms.getFeeAmountMinor()).isNull();
        }

        @Test
        @DisplayName("T111: an unknown cutoff timezone is rejected before any capacity is taken")
        void invalidCutoffTimezone_isRejectedBeforeCapacity() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), any(), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));

            OtaBookingEventResult result = eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-p3", "ext-book-bad-zone", "klook-ring-001",
                    "opt-single", 1, 150_000L, "VND", "pay-p3", "cor-p3",
                    "policy", "v1", null, "Mars/Olympus", null));

            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.rejectionReason()).isEqualTo("INVALID_POLICY_SNAPSHOT");
            verify(workshopBookingFacade, never()).createOrReturnOtaBooking(any());
        }

        @Test
        @DisplayName("T115: an unmapped product opens one INBOUND_EVENT work item linked to the inbound event")
        void unmappedProduct_opensInboundEventWorkItem() {
            OtaInboundEvent event = OtaInboundEvent.builder().id(100L).connection(connection).build();
            when(inboundEventService.referenceFor(100L)).thenReturn(event);
            when(reconciliationRepository.save(any(OtaReconciliationWorkItem.class))).thenAnswer(i -> i.getArgument(0));

            OtaBookingEventResult result = eventHandler.handleBookingEvent(new OtaBookingEventCommand(
                    OtaProvider.KLOOK, OtaEnvironment.TEST, "event-unmapped", "ext-book-unmapped",
                    "unknown-product", "unknown-option", 1, 150_000L, "VND", "pay-u", "cor-u"));

            assertThat(result.rejectionReason()).isEqualTo("MAPPING_NOT_FOUND");
            assertThat(result.reconciliationWorkItemReference()).isNotNull();
            ArgumentCaptor<OtaReconciliationWorkItem> saved = ArgumentCaptor.forClass(OtaReconciliationWorkItem.class);
            verify(reconciliationRepository).save(saved.capture());
            assertThat(saved.getValue().getCategory()).isEqualTo("INBOUND_EVENT");
            assertThat(saved.getValue().getInboundEvent()).isSameAs(event);
        }
    }

    @Nested
    @DisplayName("Mapping State and Competing Final Capacity")
    class LifecycleAndCapacityTests {

        @Test
        @DisplayName("Booking on disabled or non-booking-enabled mapping is rejected")
        void disabledMapping_isRejected() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), any()))
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

            verify(workshopBookingFacade, never()).createOrReturnOtaBooking(any());
        }

        @Test
        @DisplayName("Competing capacity rejection from Operations returns REJECTED with ZERO tickets and no orphaned booking")
        void competingCapacityRejection_returnsRejectedWithZeroTickets() {
            when(readinessService.evaluateBookingReadiness(eq(mapping), eq(150_000L), any()))
                    .thenReturn(ReadinessValidationResult.approved(priceSnapshot));

            when(workshopBookingFacade.createOrReturnOtaBooking(any()))
                    .thenReturn(OtaBookingOutcome.rejected("INSUFFICIENT_CAPACITY"));

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
