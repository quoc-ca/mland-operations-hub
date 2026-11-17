package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingReadinessService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingReadinessService.ReadinessValidationResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaPayoutLedgerRecorder;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventReceipt;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.OtaBookingCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.OtaBookingOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * T031: Authenticated provider callback adaptation and idempotent booking orchestration.
 * Interacts with Operations exclusively through WorkshopOperationsFacade.
 * Enforces:
 * - Receipt persistence before downstream business effects.
 * - Idempotent replay: exact duplicate external reference returns prior confirmed booking + tickets.
 * - Full payment evidence verification: no tickets or booking confirmation if payment is partial or mismatched.
 * - Atomic capacity reservation and individual scannable ticket issuance (1 per participant).
 * - No Mland DEPOSIT / FINAL_PAYMENT records created for base OTA package.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtaProviderEventHandler {

    private final OtaChannelConnectionRepository connectionRepository;
    private final OtaChannelMappingRepository mappingRepository;
    private final OtaChannelBookingRepository bookingRepository;
    private final OtaBookingTicketLinkRepository ticketLinkRepository;
    private final OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    private final OtaReconciliationWorkItemRepository reconciliationRepository;
    private final OtaInboundEventService inboundEventService;
    private final OtaBookingReadinessService readinessService;
    private final WorkshopBookingFacade workshopBookingFacade;
    private final OtaPayoutLedgerRecorder payoutLedgerRecorder;

    public record OtaBookingEventCommand(
            OtaProvider provider,
            OtaEnvironment environment,
            String externalEventReference,
            String externalBookingReference,
            String providerProductReference,
            String providerOptionReference,
            int participantCount,
            Long acceptedAmountMinor,
            String currencyCode,
            String externalPaymentEvidenceReference,
            String correlationReference,
            String displayedPolicyReference,
            String displayedPolicyVersion,
            Instant bookingCutoffAt,
            String bookingCutoffTimezone,
            Long feeAmountMinor
    ) {
        public OtaBookingEventCommand {
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(environment, "environment must not be null");
            Objects.requireNonNull(externalBookingReference, "externalBookingReference must not be null");
            Objects.requireNonNull(providerProductReference, "providerProductReference must not be null");
            Objects.requireNonNull(providerOptionReference, "providerOptionReference must not be null");
            Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        }

        /** Callback without the optional policy/cutoff/fee facts; they then come from the mapping's price terms. */
        public OtaBookingEventCommand(OtaProvider provider, OtaEnvironment environment, String externalEventReference,
                                      String externalBookingReference, String providerProductReference,
                                      String providerOptionReference, int participantCount, Long acceptedAmountMinor,
                                      String currencyCode, String externalPaymentEvidenceReference,
                                      String correlationReference) {
            this(provider, environment, externalEventReference, externalBookingReference, providerProductReference,
                    providerOptionReference, participantCount, acceptedAmountMinor, currencyCode,
                    externalPaymentEvidenceReference, correlationReference, null, null, null, null, null);
        }
    }

    public record OtaBookingEventResult(
            String status,
            String channelBookingReference,
            String mlandBookingReference,
            int participantCount,
            List<String> ticketReferences,
            String rejectionReason,
            String reconciliationWorkItemReference
    ) {
        public static OtaBookingEventResult confirmed(
                String channelBookingReference,
                String mlandBookingReference,
                int participantCount,
                List<String> ticketReferences
        ) {
            return new OtaBookingEventResult(
                    "CONFIRMED",
                    channelBookingReference,
                    mlandBookingReference,
                    participantCount,
                    ticketReferences,
                    null,
                    null
            );
        }

        public static OtaBookingEventResult rejected(String reason, String reconciliationReference) {
            return new OtaBookingEventResult(
                    "REJECTED",
                    null,
                    null,
                    0,
                    List.of(),
                    reason,
                    reconciliationReference
            );
        }
    }

    @Transactional
    public OtaBookingEventResult handleBookingEvent(OtaBookingEventCommand command) {
        OtaChannelConnection connection = connectionRepository
                .findByProviderCodeAndEnvironment(command.provider(), command.environment())
                .orElseThrow(() -> new IllegalArgumentException("OTA connection not found for provider "
                        + command.provider() + " and environment " + command.environment()));

        String canonicalPayload = "{\"event\":\"booking\",\"provider\":\"" + command.provider().name()
                + "\",\"externalBookingReference\":\"" + command.externalBookingReference()
                + "\",\"product\":\"" + command.providerProductReference()
                + "\",\"option\":\"" + command.providerOptionReference()
                + "\",\"participants\":" + command.participantCount()
                + ",\"acceptedAmount\":" + command.acceptedAmountMinor()
                + ",\"currency\":\"" + command.currencyCode() + "\"}";

        String fingerprint = inboundEventService.fingerprintCanonicalAllowlistedPayload(canonicalPayload);
        AuthenticatedOtaInboundEvent authEvent = new AuthenticatedOtaInboundEvent(
                command.provider(),
                command.environment(),
                command.externalEventReference() != null ? command.externalEventReference() : command.externalBookingReference(),
                fingerprint,
                "BOOKING"
        );

        OtaInboundEventReceipt receipt = inboundEventService.receive(authEvent);

        // T106: a replay returns the stored outcome; readiness, work items and the Booking facade run once only.
        if (receipt.duplicate() && receipt.outcomeReference() != null) {
            return storedOutcome(receipt);
        }

        Optional<OtaChannelMapping> mappingOpt = mappingRepository
                .findByConnectionIdAndExternalProductIdAndExternalOptionId(
                        connection.getId(),
                        command.providerProductReference(),
                        command.providerOptionReference()
                );

        if (mappingOpt.isEmpty()) {
            // T115: one INBOUND_EVENT work item per inbound event (a replay returns above, before this point).
            String workItemReference = null;
            if (!reconciliationRepository.existsByInboundEvent_IdAndCategory(receipt.eventId(), "INBOUND_EVENT")) {
                OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                        .publicReference(UUID.randomUUID().toString())
                        .inboundEvent(inboundEventService.referenceFor(receipt.eventId()))
                        .locationReference("GLOBAL")
                        .category("INBOUND_EVENT")
                        .status("OPEN")
                        .decision("MAPPING_NOT_FOUND")
                        .build();
                reconciliationRepository.save(workItem);
                workItemReference = workItem.getPublicReference();
            }
            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, "MAPPING_NOT_FOUND"));
            return OtaBookingEventResult.rejected("MAPPING_NOT_FOUND", workItemReference);
        }

        OtaChannelMapping mapping = mappingOpt.get();

        Optional<OtaChannelBooking> existingBooking = bookingRepository
                .findByMappingIdAndExternalBookingReference(mapping.getId(), command.externalBookingReference());

        if (existingBooking.isPresent()) {
            OtaChannelBooking booking = existingBooking.get();
            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.ACCEPTED, booking.getPublicReference()));
            return confirmedResult(booking);
        }

        ReadinessValidationResult readiness = readinessService.evaluateBookingReadiness(
                mapping,
                command.acceptedAmountMinor(),
                Instant.now()
        );

        if (!readiness.ready()) {
            return rejectWithWorkItem(receipt, mapping, readiness.rejectionReason());
        }

        OtaChannelPriceSnapshot priceSnapshot = readiness.priceSnapshot();

        // T111: the policy/cutoff/fee facts are fixed before capacity is taken, from the callback or the mapping's terms.
        String policyReference = firstNonBlank(command.displayedPolicyReference(), priceSnapshot.getTermsReference());
        String policyVersion = firstNonBlank(command.displayedPolicyVersion(), priceSnapshot.getPublicReference());
        String cutoffZone = blankToNull(command.bookingCutoffTimezone());
        if (policyReference == null || policyVersion == null) {
            return rejectWithWorkItem(receipt, mapping, "MISSING_POLICY_SNAPSHOT");
        }
        if (policyReference.length() > 128 || policyVersion.length() > 64 || !validZone(cutoffZone)
                || (command.feeAmountMinor() != null && command.feeAmountMinor() < 0)) {
            return rejectWithWorkItem(receipt, mapping, "INVALID_POLICY_SNAPSHOT");
        }

        String operationRef = "ota-book-" + UUID.randomUUID();
        String correlationRef = command.correlationReference() != null
                ? command.correlationReference()
                : UUID.randomUUID().toString();

        OtaBookingOutcome bookingOutcome = workshopBookingFacade.createOrReturnOtaBooking(new OtaBookingCommand(
                command.provider().name(), command.externalBookingReference(), mapping.getOfferReference(),
                UUID.fromString(mapping.getSessionReference()), command.participantCount(), operationRef, correlationRef));

        if (bookingOutcome.status() != OtaBookingOutcome.Status.ACCEPTED) {
            String rejectionReason = bookingOutcome.rejectionReason() == null
                    ? "BOOKING_CAPACITY_REJECTED" : bookingOutcome.rejectionReason();

            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, rejectionReason));

            return OtaBookingEventResult.rejected(rejectionReason, null);
        }

        long retailAmount = priceSnapshot.getRetailAmountMinor();
        long targetNetAmount = priceSnapshot.getTargetNetAmountMinor();
        long commissionAmount = Math.max(0L, retailAmount - targetNetAmount);

        OtaChannelBooking channelBooking = OtaChannelBooking.capture(
                mapping,
                command.externalBookingReference(),
                "CONFIRMED",
                command.participantCount(),
                bookingOutcome.bookingReference(),
                command.provider().name(),
                policyReference,
                policyVersion,
                command.bookingCutoffAt(),
                cutoffZone,
                retailAmount,
                targetNetAmount,
                commissionAmount,
                command.feeAmountMinor(),
                command.acceptedAmountMinor(),
                command.currencyCode()
        );

        channelBooking = bookingRepository.save(channelBooking);

        String paymentEvidenceRef = command.externalPaymentEvidenceReference() != null
                ? command.externalPaymentEvidenceReference()
                : "PAY-" + UUID.randomUUID();

        Instant confirmedAt = Instant.now();
        OtaCustomerPaymentEvidence paymentEvidence = OtaCustomerPaymentEvidence.record(
                channelBooking.getId(),
                paymentEvidenceRef,
                "OTA_GUEST_PAYMENT",
                OtaCustomerPaymentState.PAID_ON_CHANNEL,
                command.acceptedAmountMinor(),
                command.currencyCode(),
                confirmedAt,
                correlationRef
        );
        customerPaymentRepository.save(paymentEvidence);

        // T110 / T114: payout position and sale facts are created with the confirmation, in this transaction.
        payoutLedgerRecorder.openPositionWithSaleFacts(channelBooking, priceSnapshot, paymentEvidenceRef,
                confirmedAt, correlationRef);

        List<UUID> ticketRefs = bookingOutcome.ticketReferences();
        for (int i = 0; i < ticketRefs.size(); i++) {
            OtaBookingTicketLink link = OtaBookingTicketLink.link(
                    channelBooking.getId(),
                    i + 1,
                    ticketRefs.get(i).toString()
            );
            ticketLinkRepository.save(link);
        }

        inboundEventService.recordOutcome(receipt.eventId(),
                new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.ACCEPTED, channelBooking.getPublicReference()));

        List<String> ticketReferenceStrings = ticketRefs.stream()
                .map(UUID::toString)
                .toList();

        return OtaBookingEventResult.confirmed(
                channelBooking.getPublicReference(),
                bookingOutcome.bookingReference(),
                channelBooking.getParticipantCount(),
                ticketReferenceStrings
        );
    }

    private OtaBookingEventResult rejectWithWorkItem(OtaInboundEventReceipt receipt, OtaChannelMapping mapping,
                                                     String reason) {
        OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                .publicReference(UUID.randomUUID().toString())
                .mapping(mapping)
                .inboundEvent(inboundEventService.referenceFor(receipt.eventId()))
                .locationReference(mapping.getLocationReference())
                .category("INBOUND_EVENT")
                .status("OPEN")
                .decision(reason)
                .build();
        reconciliationRepository.save(workItem);
        inboundEventService.recordOutcome(receipt.eventId(),
                new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, reason));
        return OtaBookingEventResult.rejected(reason, workItem.getPublicReference());
    }

    /** Rebuilds the acknowledgement of a first delivery from the safe outcome stored with its inbound event. */
    private OtaBookingEventResult storedOutcome(OtaInboundEventReceipt receipt) {
        if ("ACCEPTED".equals(receipt.processingStatus())) {
            Optional<OtaChannelBooking> stored = bookingRepository.findByPublicReference(receipt.outcomeReference());
            if (stored.isPresent()) {
                return confirmedResult(stored.get());
            }
        }
        return OtaBookingEventResult.rejected(receipt.outcomeReference(), null);
    }

    private OtaBookingEventResult confirmedResult(OtaChannelBooking booking) {
        List<String> tickets = ticketLinkRepository
                .findAllByChannelBookingIdOrderByParticipantOrdinalAsc(booking.getId())
                .stream()
                .map(OtaBookingTicketLink::getOperationsTicketReference)
                .toList();
        return OtaBookingEventResult.confirmed(
                booking.getPublicReference(),
                booking.getMlandBookingReference(),
                booking.getParticipantCount(),
                tickets
        );
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String value = blankToNull(preferred);
        return value != null ? value : blankToNull(fallback);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean validZone(String zone) {
        if (zone == null) return true;
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }
}
