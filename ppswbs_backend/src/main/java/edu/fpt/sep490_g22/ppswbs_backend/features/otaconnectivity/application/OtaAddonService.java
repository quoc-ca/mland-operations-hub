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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
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
    private final WorkshopOperationsFacade workshopOperationsFacade;

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
    public AddonQuoteResult createLaserEngravingQuote(
            String channelBookingPublicRef,
            String itemDescription
    ) {
        OtaChannelBooking booking = bookingRepository.findByPublicReference(channelBookingPublicRef)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA Channel Booking not found: " + channelBookingPublicRef));

        String quoteReference = UUID.randomUUID().toString();
        String desc = (itemDescription != null && !itemDescription.isBlank()) ? itemDescription.trim() : "Laser Engraving";

        OtaAddonBillLink link = OtaAddonBillLink.builder()
                .channelBooking(booking)
                .settlementQuoteReference(quoteReference)
                .addonType(ADDON_TYPE_LASER_ENGRAVING)
                .itemCode(null)
                .itemDescription(desc)
                .amountMinor(0L)
                .currencyCode("VND")
                .status(STATUS_RECORDED_FREE)
                .build();

        addonBillLinkRepository.save(link);
        return toResult(link);
    }

    @Transactional
    public AddonQuoteResult createPaidOptionQuote(
            String channelBookingPublicRef,
            String catalogueAddOnReference
    ) {
        if (catalogueAddOnReference == null || catalogueAddOnReference.isBlank()) {
            throw new IllegalArgumentException("catalogueAddOnReference is required for PAID_OPTION");
        }

        OtaChannelBooking booking = bookingRepository.findByPublicReference(channelBookingPublicRef)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA Channel Booking not found: " + channelBookingPublicRef));

        String offerReference = booking.getMapping() != null ? booking.getMapping().getOfferReference() : null;
        String locationRef = booking.getMapping() != null ? booking.getMapping().getLocationReference() : null;
        Long locationId = null;
        if (locationRef != null) {
            try {
                locationId = Long.valueOf(locationRef);
            } catch (NumberFormatException ignored) {}
        }

        WorkshopAddOnPriceSnapshot snapshot = workshopOperationsFacade.getWorkshopAddOnPrice(
                new WorkshopAddOnPriceQuery(offerReference, locationId, catalogueAddOnReference.trim()));

        if (snapshot == null) {
            throw new OtaUnprocessableException("Catalogue add-on is missing, inactive, or not valid for this booking's package and location");
        }

        if (snapshot.amountVnd() == null || snapshot.amountVnd() <= 0L) {
            throw new OtaUnprocessableException("Catalogue add-on price must be positive");
        }

        String quoteReference = UUID.randomUUID().toString();
        OtaAddonBillLink link = OtaAddonBillLink.builder()
                .channelBooking(booking)
                .settlementQuoteReference(quoteReference)
                .addonType(ADDON_TYPE_PAID_OPTION)
                .itemCode(snapshot.addOnReference())
                .itemDescription(snapshot.description() != null && !snapshot.description().isBlank() ? snapshot.description() : "Workshop Add-on")
                .amountMinor(snapshot.amountVnd())
                .currencyCode(snapshot.currencyCode() != null && !snapshot.currencyCode().isBlank() ? snapshot.currencyCode() : "VND")
                .status(STATUS_QUOTED)
                .build();

        addonBillLinkRepository.save(link);
        return toResult(link);
    }

    /** The OTA booking behind a Mland booking reference, as the check-in desk needs it. */
    public record BookingLookup(
            String channelBookingId,
            String providerCode,
            String locationReference,
            Long locationId,
            String mlandBookingReference,
            boolean canSellAddon
    ) {}

    @Transactional(readOnly = true)
    public Optional<BookingLookup> findByMlandReference(String mlandBookingReference) {
        if (mlandBookingReference == null || mlandBookingReference.isBlank()) return Optional.empty();
        return bookingRepository.findByMlandBookingReference(mlandBookingReference.trim()).map(booking -> {
            var mapping = booking.getMapping();
            String locationReference = mapping != null ? mapping.getLocationReference() : null;
            Long locationId = null;
            if (locationReference != null) {
                try {
                    locationId = Long.valueOf(locationReference);
                } catch (NumberFormatException notNumeric) {
                    locationId = null;
                }
            }
            String providerCode = mapping != null && mapping.getConnection() != null
                    && mapping.getConnection().getProviderCode() != null
                    ? mapping.getConnection().getProviderCode().name() : "OTA";
            String providerStatus = booking.getProviderStatus();
            boolean canSell = !"CANCELLED".equalsIgnoreCase(providerStatus)
                    && !"REFUNDED".equalsIgnoreCase(providerStatus)
                    && !"CANCELLATION_REQUESTED".equalsIgnoreCase(providerStatus);
            return new BookingLookup(booking.getPublicReference(), providerCode, locationReference, locationId,
                    booking.getMlandBookingReference(), canSell);
        });
    }

    @Transactional
    public AddonQuoteResult createQuote(
            String channelBookingPublicRef,
            String addonType,
            String catalogueAddOnReference,
            String itemDescription
    ) {
        if (ADDON_TYPE_LASER_ENGRAVING.equalsIgnoreCase(addonType)) {
            return createLaserEngravingQuote(channelBookingPublicRef, itemDescription);
        } else if (ADDON_TYPE_PAID_OPTION.equalsIgnoreCase(addonType)) {
            return createPaidOptionQuote(channelBookingPublicRef, catalogueAddOnReference);
        } else {
            throw new IllegalArgumentException("Unsupported add-on type: " + addonType);
        }
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
                locationId,
                link.getChannelBooking().getMlandBookingReference()
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

    /** The Mland booking holding the tickets of an OTA channel booking; null when none is mapped yet. */
    public String getMlandReferenceForBooking(String channelBookingReference) {
        return bookingRepository.findByPublicReference(channelBookingReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA Channel Booking not found: " + channelBookingReference))
                .getMlandBookingReference();
    }

    public String getMlandReferenceForQuote(String quoteReference) {
        OtaAddonBillLink link = findLink(quoteReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("Add-on quote not found: " + quoteReference));
        return link.getChannelBooking().getMlandBookingReference();
    }

    /** A Staff member may act on an OTA booking only while assigned to a session its Mland booking holds tickets for. */
    public boolean isStaffAssignedToBooking(Long staffAccountId, String mlandBookingReference) {
        return mlandBookingReference != null && !mlandBookingReference.isBlank()
                && workshopOperationsFacade.isStaffAssignedToBooking(
                        new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.StaffBookingAssignmentQuery(
                                staffAccountId, mlandBookingReference.trim()));
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
                link.getItemCode(),
                link.getAmountMinor(),
                link.getCurrencyCode(),
                link.getStatus()
        );
    }
}
