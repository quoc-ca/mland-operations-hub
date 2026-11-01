package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingReadinessService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingReadinessService.ReadinessValidationResult;
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

import java.time.Instant;
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
            String correlationReference
    ) {
        public OtaBookingEventCommand {
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(environment, "environment must not be null");
            Objects.requireNonNull(externalBookingReference, "externalBookingReference must not be null");
            Objects.requireNonNull(providerProductReference, "providerProductReference must not be null");
            Objects.requireNonNull(providerOptionReference, "providerOptionReference must not be null");
            Objects.requireNonNull(currencyCode, "currencyCode must not be null");
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

        Optional<OtaChannelMapping> mappingOpt = mappingRepository
                .findByConnectionIdAndExternalProductIdAndExternalOptionId(
                        connection.getId(),
                        command.providerProductReference(),
                        command.providerOptionReference()
                );

        if (mappingOpt.isEmpty()) {
            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, "MAPPING_NOT_FOUND"));
            return OtaBookingEventResult.rejected("MAPPING_NOT_FOUND", null);
        }

        OtaChannelMapping mapping = mappingOpt.get();

        Optional<OtaChannelBooking> existingBooking = bookingRepository
                .findByMappingIdAndExternalBookingReference(mapping.getId(), command.externalBookingReference());

        if (existingBooking.isPresent()) {
            OtaChannelBooking booking = existingBooking.get();
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

        ReadinessValidationResult readiness = readinessService.evaluateBookingReadiness(
                mapping,
                command.acceptedAmountMinor(),
                command.currencyCode(),
                Instant.now()
        );

        if (!readiness.ready()) {
            OtaReconciliationWorkItem workItem = OtaReconciliationWorkItem.builder()
                    .publicReference(UUID.randomUUID().toString())
                    .mapping(mapping)
                    .locationReference(mapping.getLocationReference())
                    .category("INBOUND_EVENT")
                    .status("OPEN")
                    .decision(readiness.rejectionReason())
                    .build();
            reconciliationRepository.save(workItem);

            inboundEventService.recordOutcome(receipt.eventId(),
                    new OtaInboundEventOutcome(OtaInboundEventOutcome.Status.REJECTED, readiness.rejectionReason()));

            return OtaBookingEventResult.rejected(readiness.rejectionReason(), workItem.getPublicReference());
        }

        OtaChannelPriceSnapshot priceSnapshot = readiness.priceSnapshot();

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
                "POLICY_DEFAULT",
                "1.0",
                null,
                "Asia/Ho_Chi_Minh",
                retailAmount,
                targetNetAmount,
                commissionAmount,
                0L,
                command.acceptedAmountMinor(),
                command.currencyCode()
        );

        channelBooking = bookingRepository.save(channelBooking);

        String paymentEvidenceRef = command.externalPaymentEvidenceReference() != null
                ? command.externalPaymentEvidenceReference()
                : "PAY-" + UUID.randomUUID();

        OtaCustomerPaymentEvidence paymentEvidence = OtaCustomerPaymentEvidence.record(
                channelBooking.getId(),
                paymentEvidenceRef,
                "OTA_GUEST_PAYMENT",
                OtaCustomerPaymentState.PAID_ON_CHANNEL,
                command.acceptedAmountMinor(),
                command.currencyCode(),
                Instant.now(),
                correlationRef
        );
        customerPaymentRepository.save(paymentEvidence);

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
}
