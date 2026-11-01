package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

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
                .orElseThrow(() -> new IllegalArgumentException("OTA Channel Booking not found: " + channelBookingPublicRef));

        if (!"VND".equalsIgnoreCase(currencyCode)) {
            throw new IllegalArgumentException("Only VND currency is supported for add-ons");
        }

        String status;
        if (ADDON_TYPE_LASER_ENGRAVING.equalsIgnoreCase(addonType)) {
            if (amountMinor != 0L) {
                throw new IllegalArgumentException("Laser engraving is free of charge and must have zero amount");
            }
            status = STATUS_RECORDED_FREE;
        } else if (ADDON_TYPE_PAID_OPTION.equalsIgnoreCase(addonType)) {
            if (amountMinor <= 0L) {
                throw new IllegalArgumentException("Paid option requires a positive amount");
            }
            status = STATUS_QUOTED;
        } else {
            throw new IllegalArgumentException("Unsupported add-on type: " + addonType);
        }

        String quoteReference = "quote-" + UUID.randomUUID().toString();

        OtaAddonBillLink link = OtaAddonBillLink.builder()
                .channelBooking(booking)
                .settlementQuoteReference(quoteReference)
                .amountMinor(amountMinor)
                .currencyCode(currencyCode.toUpperCase())
                .status(status)
                .build();

        addonBillLinkRepository.save(link);

        return new AddonQuoteResult(
                quoteReference,
                booking.getPublicReference(),
                addonType.toUpperCase(),
                itemCode,
                amountMinor,
                currencyCode.toUpperCase(),
                status
        );
    }

    @Transactional
    public AddonQuoteResult recordCustomerAcceptance(
            String quoteReference,
            Instant acceptedAt,
            String customerConsentReference
    ) {
        OtaAddonBillLink link = addonBillLinkRepository.findBySettlementQuoteReference(quoteReference)
                .orElseThrow(() -> new IllegalArgumentException("Add-on quote not found: " + quoteReference));

        if (customerConsentReference == null || customerConsentReference.isBlank()) {
            throw new IllegalArgumentException("Customer consent reference is required for acceptance");
        }

        if (STATUS_RECORDED_FREE.equals(link.getStatus())) {
            // Free engraving is already recorded free, no paid acceptance needed
            return new AddonQuoteResult(
                    link.getSettlementQuoteReference(),
                    link.getChannelBooking().getPublicReference(),
                    ADDON_TYPE_LASER_ENGRAVING,
                    "FREE-ENGRAVE",
                    link.getAmountMinor(),
                    link.getCurrencyCode(),
                    link.getStatus()
            );
        }

        if (!STATUS_QUOTED.equals(link.getStatus())) {
            throw new IllegalStateException("Only a QUOTED add-on can be accepted, current status: " + link.getStatus());
        }

        link.setStatus(STATUS_ACCEPTED);
        addonBillLinkRepository.save(link);

        return new AddonQuoteResult(
                link.getSettlementQuoteReference(),
                link.getChannelBooking().getPublicReference(),
                ADDON_TYPE_PAID_OPTION,
                "PAID-OPTION",
                link.getAmountMinor(),
                link.getCurrencyCode(),
                link.getStatus()
        );
    }

    @Transactional
    public DirectBillResult createDirectBill(
            String quoteReference,
            String customerConsentReference,
            String staffActorReference,
            Long locationId
    ) {
        OtaAddonBillLink link = addonBillLinkRepository.findBySettlementQuoteReference(quoteReference)
                .orElseThrow(() -> new IllegalArgumentException("Add-on quote not found: " + quoteReference));

        if (STATUS_RECORDED_FREE.equals(link.getStatus())) {
            throw new IllegalStateException("Laser engraving is free and does not generate a bill");
        }

        // Strict Missing-Consent Rejection: Cannot create direct bill if not accepted by customer
        if (!STATUS_ACCEPTED.equals(link.getStatus())) {
            throw new IllegalStateException("Customer acceptance is required before a direct bill can be created (current status: " + link.getStatus() + ")");
        }

        // Delegate to Settlement facade
        CreateDirectBillCommand cmd = new CreateDirectBillCommand(
                link.getChannelBooking().getPublicReference(),
                link.getSettlementQuoteReference(),
                "ADDON-ITEM",
                "Workshop Add-on",
                link.getAmountMinor(),
                link.getCurrencyCode(),
                customerConsentReference,
                staffActorReference,
                locationId
        );

        DirectBillCreationOutcome outcome = settlementPort.createDirectBill(cmd);
        if (!outcome.created()) {
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
}

