package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaCancellationAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaCancellationAdapterRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter.OtaCancellationMappingResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationConsequenceService.CancellationConsequenceResult;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * T036 / T038: Cancellation-request versus confirmed-cancellation/refund state handling
 * and safe reconciliation routing via adapter boundary.
 * Rules:
 * - A request records REQUEST_RECORDED / REFUND_REQUESTED without invoking Operations or releasing inventory.
 * - An authenticated confirmed cancellation invokes Operations consequence exactly once.
 * - Duplicate requested or duplicate confirmed events are idempotent.
 * - Out-of-order, unmapped, or invalid events safely route to OtaReconciliationWorkItem.
 * - Raw provider webhooks are translated via provider-specific OtaCancellationAdapter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtaCancellationService {

    private final OtaChannelConnectionRepository connectionRepository;
    private final OtaChannelMappingRepository mappingRepository;
    private final OtaChannelBookingRepository bookingRepository;
    private final OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    private final OtaReconciliationWorkItemRepository reconciliationRepository;
    private final OtaInboundEventService inboundEventService;
    private final OtaCancellationConsequenceService consequenceService;
    private final OtaCancellationAdapterRegistry cancellationAdapterRegistry;

    public OtaCancellationService(
            OtaChannelConnectionRepository connectionRepository,
            OtaChannelMappingRepository mappingRepository,
            OtaChannelBookingRepository bookingRepository,
            OtaCustomerPaymentEvidenceRepository customerPaymentRepository,
            OtaReconciliationWorkItemRepository reconciliationRepository,
            OtaInboundEventService inboundEventService,
            OtaCancellationConsequenceService consequenceService
    ) {
        this(connectionRepository, mappingRepository, bookingRepository,
                customerPaymentRepository, reconciliationRepository,
                inboundEventService, consequenceService, null);
    }

    public record OtaCancellationEventCommand(
            OtaProvider provider,
            OtaEnvironment environment,
            String externalEventReference,
            String externalBookingReference,
            String cancellationKind, // "REQUESTED", "CONFIRMED_CANCELLED", "REFUNDED"
            Long refundAmountMinor,
            String currencyCode,
            String reason,
            String correlationReference
    ) {
        public OtaCancellationEventCommand {
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(environment, "environment must not be null");
            Objects.requireNonNull(externalBookingReference, "externalBookingReference must not be null");
            Objects.requireNonNull(cancellationKind, "cancellationKind must not be null");
        }
    }

    public record OtaCancellationEventResult(
            String status,
            String externalBookingReference,
            String channelBookingReference,
            String currentBookingStatus,
            String rejectionReason,
            String reconciliationWorkItemReference
    ) {
        public static OtaCancellationEventResult success(
                String status,
                String externalBookingReference,
                String channelBookingReference,
                String currentBookingStatus
        ) {
            return new OtaCancellationEventResult(
                    status,
                    externalBookingReference,
                    channelBookingReference,
                    currentBookingStatus,
                    null,
                    null
            );
        }

        public static OtaCancellationEventResult rejected(
                String externalBookingReference,
                String reason,
                String reconciliationWorkItemReference
        ) {
            return new OtaCancellationEventResult(
                    "REJECTED",
                    externalBookingReference,
                    null,
                    null,
                    reason,
                    reconciliationWorkItemReference
            );
        }
    }

    @Transactional
    public OtaCancellationEventResult handleCancellationEvent(OtaCancellationEventCommand command) {
        OtaChannelConnection connection = connectionRepository
                .findByProviderCodeAndEnvironment(command.provider(), command.environment())
                .orElseThrow(() -> new IllegalArgumentException("OTA connection not found for provider "
                        + command.provider() + " and environment " + command.environment()));

        String canonicalPayload = "{\"event\":\"cancellation\",\"provider\":\"" + command.provider().name()
                + "\",\"externalBookingReference\":\"" + command.externalBookingReference()
                + "\",\"kind\":\"" + command.cancellationKind()
                + "\",\"refundAmount\":" + command.refundAmountMinor()
                + ",\"currency\":\"" + command.currencyCode() + "\"}";

        String fingerprint = inboundEventService.fingerprintCanonicalAllowlistedPayload(canonicalPayload);
        AuthenticatedOtaInboundEvent authEvent = new AuthenticatedOtaInboundEvent(
                command.provider(),
                command.environment(),
                command.externalEventReference() != null ? command.externalEventReference() : UUID.randomUUID().toString(),
                fingerprint,
                "CANCELLATION"
        );

        OtaInboundEventReceipt receipt = inboundEventService.receive(authEvent);

        Optional<OtaChannelBooking> bookingOpt = bookingRepository
                .findAll()
                .stream()
                .filter(b -> b.getMapping().getConnection().getId().equals(connection.getId())
                        && b.getExternalBookingReference().equals(command.externalBookingReference()))
                .findFirst();

        if (bookingOpt.isEmpty()) {
            OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                    .publicReference(UUID.randomUUID().toString())
                    .locationReference("GLOBAL")
                    .category("INBOUND_EVENT")
                    .status("OPEN")
                    .decision("UNKNOWN_BOOKING_REFERENCE: " + command.externalBookingReference())
                    .build();
            reconciliationRepository.save(workItem);

            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, "UNKNOWN_BOOKING_REFERENCE"));

            return OtaCancellationEventResult.rejected(
                    command.externalBookingReference(),
                    "UNKNOWN_BOOKING_REFERENCE",
                    workItem.getPublicReference()
            );
        }

        OtaChannelBooking booking = bookingOpt.get();
        String correlation = command.correlationReference() != null && !command.correlationReference().isBlank()
                ? command.correlationReference()
                : UUID.randomUUID().toString();

        long refundAmount = command.refundAmountMinor() != null && command.refundAmountMinor() > 0
                ? command.refundAmountMinor()
                : booking.getAcceptedAmountMinor();
        String currency = command.currencyCode() != null && !command.currencyCode().isBlank()
                ? command.currencyCode()
                : booking.getCurrencyCode();

        if ("REQUESTED".equalsIgnoreCase(command.cancellationKind())) {
            booking.markCancellationRequested();
            bookingRepository.save(booking);

            String evidenceRef = "PAY-REF-REQ-" + UUID.randomUUID();
            OtaCustomerPaymentEvidence evidence = OtaCustomerPaymentEvidence.record(
                    booking.getId(),
                    evidenceRef,
                    "CUSTOMER_REFUND",
                    OtaCustomerPaymentState.REFUND_REQUESTED,
                    refundAmount,
                    currency,
                    Instant.now(),
                    correlation
            );
            customerPaymentRepository.save(evidence);

            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.ACCEPTED, booking.getPublicReference()));

            return OtaCancellationEventResult.success(
                    "REQUEST_RECORDED",
                    command.externalBookingReference(),
                    booking.getPublicReference(),
                    booking.getProviderStatus()
            );
        }

        if ("CONFIRMED_CANCELLED".equalsIgnoreCase(command.cancellationKind())
                || "REFUNDED".equalsIgnoreCase(command.cancellationKind())) {

            if ("CANCELLED".equals(booking.getProviderStatus()) || "REFUNDED".equals(booking.getProviderStatus())) {
                return OtaCancellationEventResult.success(
                        "CONFIRMED",
                        command.externalBookingReference(),
                        booking.getPublicReference(),
                        booking.getProviderStatus()
                );
            }

            booking.markCancelled();
            bookingRepository.save(booking);

            String evidenceRef = "PAY-REF-" + UUID.randomUUID();
            OtaCustomerPaymentEvidence evidence = OtaCustomerPaymentEvidence.record(
                    booking.getId(),
                    evidenceRef,
                    "CUSTOMER_REFUND",
                    OtaCustomerPaymentState.REFUNDED,
                    refundAmount,
                    currency,
                    Instant.now(),
                    correlation
            );
            customerPaymentRepository.save(evidence);

            CancellationConsequenceResult consequenceResult = consequenceService
                    .applyConfirmedConsequence(booking, evidenceRef, correlation);

            if (!consequenceResult.successful()) {
                OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                        .publicReference(UUID.randomUUID().toString())
                        .mapping(booking.getMapping())
                        .locationReference(booking.getMapping().getLocationReference())
                        .channelBookingId(booking.getId())
                        .category("INBOUND_EVENT")
                        .status("OPEN")
                        .decision("CONSEQUENCE_REJECTED: " + consequenceResult.rejectionReason())
                        .build();
                reconciliationRepository.save(workItem);

                inboundEventService.recordOutcome(receipt.eventId(),
                        new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, consequenceResult.rejectionReason()));

                return OtaCancellationEventResult.rejected(
                        command.externalBookingReference(),
                        consequenceResult.rejectionReason(),
                        workItem.getPublicReference()
                );
            }

            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.ACCEPTED, booking.getPublicReference()));

            return OtaCancellationEventResult.success(
                    "CONFIRMED",
                    command.externalBookingReference(),
                    booking.getPublicReference(),
                    booking.getProviderStatus()
            );
        }

        inboundEventService.recordOutcome(receipt.eventId(),
                new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, "INVALID_CANCELLATION_KIND"));

        return OtaCancellationEventResult.rejected(
                command.externalBookingReference(),
                "INVALID_CANCELLATION_KIND",
                null
        );
    }

    @Transactional
    public OtaCancellationEventResult handleRawProviderCancellation(
            OtaProvider provider,
            OtaEnvironment environment,
            String rawJsonPayload,
            Map<String, String> headers
    ) {
        OtaChannelConnection connection = connectionRepository
                .findByProviderCodeAndEnvironment(provider, environment)
                .orElseThrow(() -> new IllegalArgumentException("OTA connection not found for provider "
                        + provider + " and environment " + environment));

        if (cancellationAdapterRegistry == null) {
            throw new IllegalStateException("OtaCancellationAdapterRegistry is not configured");
        }

        Optional<OtaCancellationAdapter> adapterOpt = cancellationAdapterRegistry.findAdapter(provider);
        if (adapterOpt.isEmpty()) {
            OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                    .publicReference(UUID.randomUUID().toString())
                    .locationReference("GLOBAL")
                    .category("INBOUND_EVENT")
                    .status("OPEN")
                    .decision("UNSUPPORTED_PROVIDER: " + provider)
                    .build();
            reconciliationRepository.save(workItem);
            return OtaCancellationEventResult.rejected(null, "UNSUPPORTED_PROVIDER", workItem.getPublicReference());
        }

        OtaCancellationAdapter adapter = adapterOpt.get();
        OtaCancellationMappingResult mappingResult = adapter.mapCancellationEvent(connection, rawJsonPayload, headers);

        return switch (mappingResult) {
            case OtaCancellationMappingResult.Mapped mapped -> handleCancellationEvent(mapped.command());
            case OtaCancellationMappingResult.Rejected rejected -> {
                OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                        .publicReference(UUID.randomUUID().toString())
                        .locationReference("GLOBAL")
                        .category("INBOUND_EVENT")
                        .status("OPEN")
                        .decision("ADAPTER_REJECTED: " + rejected.failureCategory() + " - " + rejected.reason())
                        .build();
                reconciliationRepository.save(workItem);
                yield OtaCancellationEventResult.rejected(null, rejected.failureCategory() + ": " + rejected.reason(), workItem.getPublicReference());
            }
            case OtaCancellationMappingResult.Unmapped unmapped -> {
                OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                        .publicReference(UUID.randomUUID().toString())
                        .locationReference("GLOBAL")
                        .category("INBOUND_EVENT")
                        .status("OPEN")
                        .decision("UNMAPPED_PROVIDER_EVENT: " + unmapped.eventType() + " - " + unmapped.reason())
                        .build();
                reconciliationRepository.save(workItem);
                yield OtaCancellationEventResult.rejected(null, "UNMAPPED_PROVIDER_EVENT: " + unmapped.reason(), workItem.getPublicReference());
            }
        };
    }
}

