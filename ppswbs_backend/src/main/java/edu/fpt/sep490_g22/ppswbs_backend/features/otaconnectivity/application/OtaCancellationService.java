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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
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
    private final OtaPayoutLedgerRecorder payoutLedgerRecorder;

    private static final String LATE_REQUEST = "LATE_REQUEST_AFTER_CANCELLATION";

    @Autowired
    public OtaCancellationService(
            OtaChannelConnectionRepository connectionRepository,
            OtaChannelMappingRepository mappingRepository,
            OtaChannelBookingRepository bookingRepository,
            OtaCustomerPaymentEvidenceRepository customerPaymentRepository,
            OtaReconciliationWorkItemRepository reconciliationRepository,
            OtaInboundEventService inboundEventService,
            OtaCancellationConsequenceService consequenceService,
            OtaCancellationAdapterRegistry cancellationAdapterRegistry,
            OtaPayoutLedgerRecorder payoutLedgerRecorder
    ) {
        this.connectionRepository = connectionRepository;
        this.mappingRepository = mappingRepository;
        this.bookingRepository = bookingRepository;
        this.customerPaymentRepository = customerPaymentRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.inboundEventService = inboundEventService;
        this.consequenceService = consequenceService;
        this.cancellationAdapterRegistry = cancellationAdapterRegistry;
        this.payoutLedgerRecorder = payoutLedgerRecorder;
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
                command.externalEventReference(),
                fingerprint,
                "CANCELLATION"
        );

        OtaInboundEventReceipt receipt = inboundEventService.receive(authEvent);

        // T107: a replay returns the stored outcome and runs no evidence, state change or work item.
        if (receipt.duplicate() && receipt.outcomeReference() != null) {
            return storedOutcome(command, receipt);
        }

        Optional<OtaChannelBooking> bookingOpt = bookingRepository
                .findByMapping_Connection_IdAndExternalBookingReference(
                        connection.getId(), command.externalBookingReference());

        if (bookingOpt.isEmpty()) {
            OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                    .publicReference(UUID.randomUUID().toString())
                    .inboundEvent(inboundEventService.referenceFor(receipt.eventId()))
                    .locationReference("GLOBAL")
                    .category("INBOUND_EVENT")
                    .status("OPEN")
                    .decision("UNKNOWN_BOOKING_REFERENCE")
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
        String currency = command.currencyCode() != null && !command.currencyCode().isBlank()
                ? command.currencyCode().trim().toUpperCase(Locale.ROOT)
                : booking.getCurrencyCode();
        // Refund facts come from the OTA only: no amount reported means no refund evidence (T109).
        boolean refundReported = command.refundAmountMinor() != null && command.refundAmountMinor() > 0;

        if ("REQUESTED".equalsIgnoreCase(command.cancellationKind())) {
            if (isFinal(booking)) {
                // T108: status never goes backwards; a late request has no consequence.
                inboundEventService.recordOutcome(receipt.eventId(),
                        new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, LATE_REQUEST));
                return OtaCancellationEventResult.rejected(command.externalBookingReference(), LATE_REQUEST, null);
            }
            if (!"CANCELLATION_REQUESTED".equals(booking.getProviderStatus())) {
                booking.markCancellationRequested();
                bookingRepository.save(booking);
                if (refundReported) {
                    customerPaymentRepository.save(OtaCustomerPaymentEvidence.record(
                            booking.getId(),
                            "REQ-" + receipt.eventId(),
                            "CUSTOMER_REFUND",
                            OtaCustomerPaymentState.REFUND_REQUESTED,
                            command.refundAmountMinor(),
                            currency,
                            Instant.now(),
                            correlation));
                }
            }

            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.ACCEPTED, booking.getPublicReference()));

            return OtaCancellationEventResult.success(
                    "REQUEST_RECORDED",
                    command.externalBookingReference(),
                    booking.getPublicReference(),
                    booking.getProviderStatus()
            );
        }

        boolean refundEvent = "REFUNDED".equalsIgnoreCase(command.cancellationKind());
        if ("CONFIRMED_CANCELLED".equalsIgnoreCase(command.cancellationKind()) || refundEvent) {
            if (!isFinal(booking)) {
                // Seats are released exactly once, before the booking leaves its open state.
                CancellationConsequenceResult consequenceResult = consequenceService
                        .applyConfirmedConsequence(booking, "ota-cancel-" + receipt.eventId(), correlation);

                if (!consequenceResult.successful()) {
                    OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                            .publicReference(UUID.randomUUID().toString())
                            .mapping(booking.getMapping())
                            .inboundEvent(inboundEventService.referenceFor(receipt.eventId()))
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
                booking.markCancelled();
            }

            if (refundEvent && !"REFUNDED".equals(booking.getProviderStatus())) {
                booking.markRefunded();
                if (refundReported) {
                    String evidenceRef = "REF-" + receipt.eventId();
                    customerPaymentRepository.save(OtaCustomerPaymentEvidence.record(
                            booking.getId(),
                            evidenceRef,
                            "CUSTOMER_REFUND",
                            OtaCustomerPaymentState.REFUNDED,
                            command.refundAmountMinor(),
                            currency,
                            Instant.now(),
                            correlation));
                    payoutLedgerRecorder.recordCustomerRefund(booking, evidenceRef, command.refundAmountMinor(),
                            currency, correlation);
                }
            }
            bookingRepository.save(booking);

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

    private static boolean isFinal(OtaChannelBooking booking) {
        return "CANCELLED".equals(booking.getProviderStatus()) || "REFUNDED".equals(booking.getProviderStatus());
    }

    /** Rebuilds the acknowledgement of a first delivery from the safe outcome stored with its inbound event. */
    private OtaCancellationEventResult storedOutcome(OtaCancellationEventCommand command, OtaInboundEventReceipt receipt) {
        if ("ACCEPTED".equals(receipt.processingStatus())) {
            Optional<OtaChannelBooking> stored = bookingRepository.findByPublicReference(receipt.outcomeReference());
            if (stored.isPresent()) {
                String status = "REQUESTED".equalsIgnoreCase(command.cancellationKind())
                        ? "REQUEST_RECORDED" : "CONFIRMED";
                return OtaCancellationEventResult.success(status, command.externalBookingReference(),
                        stored.get().getPublicReference(), stored.get().getProviderStatus());
            }
        }
        return OtaCancellationEventResult.rejected(command.externalBookingReference(),
                receipt.outcomeReference(), null);
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
            return routeRawFailure(provider, environment, rawJsonPayload, "UNSUPPORTED_PROVIDER",
                    "UNSUPPORTED_PROVIDER: " + provider);
        }

        OtaCancellationAdapter adapter = adapterOpt.get();
        OtaCancellationMappingResult mappingResult = adapter.mapCancellationEvent(connection, rawJsonPayload, headers);

        return switch (mappingResult) {
            case OtaCancellationMappingResult.Mapped mapped -> handleCancellationEvent(mapped.command());
            case OtaCancellationMappingResult.Rejected rejected -> routeRawFailure(provider, environment,
                    rawJsonPayload, rejected.failureCategory() + ": " + rejected.reason(),
                    "ADAPTER_REJECTED: " + rejected.failureCategory() + " - " + rejected.reason());
            case OtaCancellationMappingResult.Unmapped unmapped -> routeRawFailure(provider, environment,
                    rawJsonPayload, "UNMAPPED_PROVIDER_EVENT: " + unmapped.reason(),
                    "UNMAPPED_PROVIDER_EVENT: " + unmapped.eventType() + " - " + unmapped.reason());
        };
    }

    /**
     * A raw event that cannot become a command still gets an inbound-event receipt (keyed by the fingerprint of the
     * payload, which is not retained), so a replay opens no second work item and the work item can name its provider.
     */
    private OtaCancellationEventResult routeRawFailure(OtaProvider provider, OtaEnvironment environment,
                                                       String rawJsonPayload, String rejectionReason,
                                                       String decision) {
        String fingerprint = inboundEventService.fingerprintCanonicalAllowlistedPayload(
                rawJsonPayload == null || rawJsonPayload.isBlank() ? "{}" : rawJsonPayload);
        OtaInboundEventReceipt receipt = inboundEventService.receive(new AuthenticatedOtaInboundEvent(
                provider, environment, null, fingerprint, "CANCELLATION"));
        if (receipt.duplicate() && receipt.outcomeReference() != null) {
            return OtaCancellationEventResult.rejected(null, rejectionReason, null);
        }
        OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                .publicReference(UUID.randomUUID().toString())
                .inboundEvent(inboundEventService.referenceFor(receipt.eventId()))
                .locationReference("GLOBAL")
                .category("INBOUND_EVENT")
                .status("OPEN")
                .decision(decision.length() > 64 ? decision.substring(0, 64) : decision)
                .build();
        reconciliationRepository.save(workItem);
        inboundEventService.recordOutcome(receipt.eventId(), new OtaInboundEventOutcome(
                OtaInboundEventOutcome.Status.REJECTED, safeReason(rejectionReason)));
        return OtaCancellationEventResult.rejected(null, rejectionReason, workItem.getPublicReference());
    }

    private static String safeReason(String reason) {
        String safe = reason.replaceAll("[^A-Za-z0-9._:-]", "_");
        return safe.length() > 128 ? safe.substring(0, 128) : safe;
    }
}
