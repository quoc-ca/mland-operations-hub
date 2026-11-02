package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnprocessableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort.CreateDirectBillCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort.DirectBillCreationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaAddonBillLink;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaAddonBillLinkRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtaAddonService {

    public static final String ADDON_TYPE_LASER_ENGRAVING = "LASER_ENGRAVING";
    public static final String ADDON_TYPE_PAID_OPTION = "PAID_OPTION";

    public static final String STATUS_RECORDED_FREE = "RECORDED_FREE";
    public static final String STATUS_QUOTED = "QUOTED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_BILLED = "BILLED";

    private final OtaChannelBookingRepository bookingRepository;
    private final OtaAddonBillLinkRepository addonBillLinkRepository;
    private final OtaAddonSettlementPort settlementPort;

    public record AddonQuoteResult(
            String quoteReference,
            String channelBookingReference,
            String addonType,
            String itemCode,
            long amountMinor,
            String currencyCode,
            String status
    ) {}

    public record DirectBillResult(
            String quoteReference,
            String settlementBillReference,
            String paymentUrlOrQr,
            String status
    ) {}

    @Transactional
    public AddonQuoteResult createQuote(
            String channelBookingPublicRef,
            String addonType,
            String itemCode,
            String itemDescription,
            long amountMinor,
            String currencyCode
    ) {
        OtaChannelBooking booking = bookingRepository.findByPublicReference(channelBookingPublicRef)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA Channel Booking not found: " + channelBookingPublicRef));

        if (!"VND".equalsIgnoreCase(currencyCode)) {
            throw new IllegalArgumentException("Only VND currency is supported for add-ons");
        }

        String status;
        String resolvedItemCode = itemCode;
        if (ADDON_TYPE_LASER_ENGRAVING.equalsIgnoreCase(addonType)) {
            if (amountMinor != 0L) {
                throw new IllegalArgumentException("Laser engraving is free of charge and must have zero amount");
            }
            status = STATUS_RECORDED_FREE;
            if (resolvedItemCode == null || resolvedItemCode.isBlank()) {
                resolvedItemCode = "FREE-ENGRAVE";
            }
        } else if (ADDON_TYPE_PAID_OPTION.equalsIgnoreCase(addonType)) {
            if (amountMinor <= 0L) {
                throw new IllegalArgumentException("Paid option requires a positive amount");
            }
            if (resolvedItemCode == null || resolvedItemCode.isBlank()) {
                throw new IllegalArgumentException("Paid option requires an itemCode");
            }
            status = STATUS_QUOTED;
        } else {
            throw new IllegalArgumentException("Unsupported add-on type: " + addonType);
        }

        String quoteReference = UUID.randomUUID().toString();

        OtaAddonBillLink link = OtaAddonBillLink.builder()
                .channelBooking(booking)
                .settlementQuoteReference(quoteReference)
                .addonType(addonType.toUpperCase())
                .itemCode(resolvedItemCode)
                .itemDescription(itemDescription != null && !itemDescription.isBlank() ? itemDescription : (status.equals(STATUS_RECORDED_FREE) ? "Laser Engraving" : "Workshop Add-on"))
                .amountMinor(amountMinor)
                .currencyCode(currencyCode.toUpperCase())
                .status(status)
                .build();

        addonBillLinkRepository.save(link);

        return toResult(link);
    }

    @Transactional
    public AddonQuoteResult recordCustomerAcceptance(
            String quoteReference,
            Instant acceptedAt,
            String customerConsentReference
    ) {
        OtaAddonBillLink link = findLink(quoteReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("Add-on quote not found: " + quoteReference));

        if (customerConsentReference == null || customerConsentReference.isBlank()) {
            throw new IllegalArgumentException("Customer consent reference is required for acceptance");
        }

        if (STATUS_RECORDED_FREE.equals(link.getStatus())) {
            throw new OtaConflictException("Free laser engraving is already recorded free; paid acceptance is rejected");
        }

        if (STATUS_ACCEPTED.equals(link.getStatus())) {
            // Idempotent replay: if same consent reference, return existing quote
            if (customerConsentReference.trim().equals(link.getCustomerConsentReference())) {
                return toResult(link);
            }
            throw new OtaConflictException("The add-on quote was already accepted with a different consent reference");
        }

        if (STATUS_BILLED.equals(link.getStatus())) {
            throw new OtaConflictException("The add-on quote has already been billed");
        }

        if (!STATUS_QUOTED.equals(link.getStatus())) {
            throw new OtaConflictException("Only a QUOTED add-on can be accepted, current status: " + link.getStatus());
        }

        link.setStatus(STATUS_ACCEPTED);
        link.setCustomerConsentReference(customerConsentReference.trim());
        link.setAcceptedAt(acceptedAt != null ? acceptedAt : Instant.now());
        addonBillLinkRepository.save(link);

        return toResult(link);
    }

    @Transactional
    public DirectBillResult createDirectBill(
            String quoteReference,
            String customerConsentReference,
            String staffActorReference,
            Long locationId
    ) {
        OtaAddonBillLink link = findLink(quoteReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("Add-on quote not found: " + quoteReference));

        if (STATUS_RECORDED_FREE.equals(link.getStatus())) {
            throw new OtaUnprocessableException("Laser engraving is free and does not generate a bill");
        }

        if (STATUS_BILLED.equals(link.getStatus()) && link.getSettlementBillReference() != null) {
            // Idempotent replay: already billed
            return new DirectBillResult(
                    link.getSettlementQuoteReference(),
                    link.getSettlementBillReference(),
                    null,
                    STATUS_BILLED
            );
        }

        // Strict Missing-Consent Rejection: Cannot create direct bill if not accepted by customer
        if (!STATUS_ACCEPTED.equals(link.getStatus())) {
            throw new OtaConflictException("Customer acceptance is required before a direct bill can be created (current status: " + link.getStatus() + ")");
        }

        String consent = customerConsentReference != null && !customerConsentReference.isBlank()
                ? customerConsentReference.trim()
                : link.getCustomerConsentReference();

        if (consent == null || consent.isBlank()) {
            throw new OtaConflictException("Customer consent reference is required before a direct bill can be created");
        }

        String itemCode = link.getItemCode() != null && !link.getItemCode().isBlank() ? link.getItemCode() : "ADDON-ITEM";
        String itemDesc = link.getItemDescription() != null && !link.getItemDescription().isBlank() ? link.getItemDescription() : "Workshop Add-on";

        // Delegate to Settlement facade
        CreateDirectBillCommand cmd = new CreateDirectBillCommand(
                link.getChannelBooking().getPublicReference(),
                link.getSettlementQuoteReference(),
                itemCode,
                itemDesc,
                link.getAmountMinor(),
                link.getCurrencyCode(),
                consent,
                staffActorReference,
                locationId
        );

        DirectBillCreationOutcome outcome = settlementPort.createDirectBill(cmd);
        if (!outcome.created()) {
            if ("IDEMPOTENCY_CONFLICT".equals(outcome.errorCode())) {
                throw new OtaConflictException("Settlement rejected direct bill: " + outcome.errorMessage());
            }
            throw new IllegalStateException("Settlement rejected direct bill creation: " + outcome.errorMessage());
        }

        link.setSettlementBillReference(outcome.settlementBillReference());
        link.setStatus(STATUS_BILLED);
        addonBillLinkRepository.save(link);

        return new DirectBillResult(
                link.getSettlementQuoteReference(),
                outcome.settlementBillReference(),
                outcome.paymentUrlOrQr(),
                STATUS_BILLED
        );
    }

    @Transactional
    public DirectBillResult createDirectBill(
            String quoteReference,
            String staffActorReference,
            Long locationId
    ) {
        return createDirectBill(quoteReference, null, staffActorReference, locationId);
    }

    public Long getLocationIdForBooking(String channelBookingReference) {
        OtaChannelBooking booking = bookingRepository.findByPublicReference(channelBookingReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA Channel Booking not found: " + channelBookingReference));
        String locRef = booking.getMapping().getLocationReference();
        try {
            return Long.valueOf(locRef);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    public Long getLocationIdForQuote(String quoteReference) {
        OtaAddonBillLink link = findLink(quoteReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("Add-on quote not found: " + quoteReference));
        return getLocationIdForBooking(link.getChannelBooking().getPublicReference());
    }

    public OtaAddonBillLink findQuote(String quoteReference) {
        return findLink(quoteReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("Add-on quote not found: " + quoteReference));
    }

    public Optional<OtaAddonBillLink> findLink(String quoteReference) {
        if (quoteReference == null) return Optional.empty();
        Optional<OtaAddonBillLink> found = addonBillLinkRepository.findBySettlementQuoteReference(quoteReference);
        if (found.isPresent()) return found;
        if (quoteReference.startsWith("quote-")) {
            return addonBillLinkRepository.findBySettlementQuoteReference(quoteReference.substring("quote-".length()));
        } else {
            return addonBillLinkRepository.findBySettlementQuoteReference("quote-" + quoteReference);
        }
    }

    private AddonQuoteResult toResult(OtaAddonBillLink link) {
        return new AddonQuoteResult(
                link.getSettlementQuoteReference(),
                link.getChannelBooking().getPublicReference(),
                link.getAddonType() != null ? link.getAddonType() : (link.getAmountMinor() == 0 ? ADDON_TYPE_LASER_ENGRAVING : ADDON_TYPE_PAID_OPTION),
                link.getItemCode() != null ? link.getItemCode() : (link.getAmountMinor() == 0 ? "FREE-ENGRAVE" : "PAID-OPTION"),
                link.getAmountMinor(),
                link.getCurrencyCode(),
                link.getStatus()
        );
    }
}
