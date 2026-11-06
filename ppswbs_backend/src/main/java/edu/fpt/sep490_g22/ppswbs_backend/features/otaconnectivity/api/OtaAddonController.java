package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.AddonAcceptanceRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.AddonQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.AddonQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.DirectBillLinkResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.OtaBookingLookupResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaChannelRequests.Money;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.AddonQuoteResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.DirectBillResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCurrencyMoney;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnprocessableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OtaAddonController {

    private final OtaAuthorizationService authorizationService;
    private final OtaAddonService addonService;

    /**
     * Resolves an OTA booking from the Mland booking reference shown at check-in. A booking that does not exist and
     * one outside the caller's location scope answer identically (404), so the endpoint cannot be used to probe which
     * Mland bookings came through an OTA.
     */
    @GetMapping(value = "/api/v1/ota-channel-bookings/lookups")
    public ResponseEntity<OtaBookingLookupResponse> lookupByMlandReference(
            @RequestParam("mlandBookingReference") String mlandBookingReference,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authorizationService.authenticate(authorization);
        if (mlandBookingReference == null || mlandBookingReference.isBlank()) {
            throw new IllegalArgumentException("mlandBookingReference is required");
        }
        var lookup = addonService.findByMlandReference(mlandBookingReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA Channel Booking not found"));
        try {
            authorizationService.requireStaffOrManagerLocation(authorization, lookup.locationId());
        } catch (OtaForbiddenException outOfScope) {
            throw new OtaResourceNotFoundException("OTA Channel Booking not found");
        }
        return ResponseEntity.ok(new OtaBookingLookupResponse(lookup.channelBookingId(), lookup.providerCode(),
                lookup.locationReference(), lookup.mlandBookingReference(), lookup.canSellAddon()));
    }

    @PostMapping(value = "/api/v1/ota-channel-bookings/{channelBookingId}/add-on-quotes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddonQuoteResponse> createOtaAddonQuote(
            @PathVariable String channelBookingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AddonQuoteRequest request
    ) {
        UUID.fromString(channelBookingId);

        Long locationId = addonService.getLocationIdForBooking(channelBookingId);
        authorizationService.requireStaffOrManagerLocation(authorization, locationId);

        AddonQuoteResult quote;
        if (OtaAddonService.ADDON_TYPE_LASER_ENGRAVING.equalsIgnoreCase(request.addonType())) {
            if (request.catalogueAddOnReference() != null) {
                throw new IllegalArgumentException("catalogueAddOnReference is not permitted for LASER_ENGRAVING");
            }
            if (request.itemCode() != null) {
                throw new IllegalArgumentException("itemCode is not permitted for LASER_ENGRAVING");
            }
            if (request.itemDescription() == null || request.itemDescription().isBlank()) {
                throw new IllegalArgumentException("itemDescription is required for LASER_ENGRAVING");
            }
            if (request.amount() == null) {
                throw new IllegalArgumentException("amount is required for LASER_ENGRAVING");
            }
            if (!"VND".equalsIgnoreCase(request.amount().currency())) {
                throw new OtaUnprocessableException("Only VND currency is supported for add-ons");
            }

            long amountMinor = OtaCurrencyMoney.toMinor(request.amount().amount(), request.amount().currency());
            if (amountMinor != 0L) {
                throw new OtaUnprocessableException("Laser engraving is free of charge and must have zero amount");
            }

            quote = addonService.createLaserEngravingQuote(channelBookingId, request.itemDescription());
        } else if (OtaAddonService.ADDON_TYPE_PAID_OPTION.equalsIgnoreCase(request.addonType())) {
            if (request.amount() != null) {
                throw new IllegalArgumentException("Staff-entered amount is not permitted for PAID_OPTION");
            }
            if (request.itemCode() != null) {
                throw new IllegalArgumentException("Staff-entered itemCode is not permitted for PAID_OPTION");
            }
            if (request.itemDescription() != null) {
                throw new IllegalArgumentException("itemDescription is not permitted for PAID_OPTION");
            }
            if (request.catalogueAddOnReference() == null || request.catalogueAddOnReference().isBlank()) {
                throw new IllegalArgumentException("catalogueAddOnReference is required for PAID_OPTION");
            }

            quote = addonService.createPaidOptionQuote(channelBookingId, request.catalogueAddOnReference());
        } else {
            throw new IllegalArgumentException("Unsupported add-on type: " + request.addonType());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(quote));
    }

    @PostMapping(value = "/api/v1/ota-addon-quotes/{quoteId}/acceptance", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddonQuoteResponse> acceptOtaAddonQuote(
            @PathVariable String quoteId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AddonAcceptanceRequest request
    ) {
        UUID.fromString(quoteId);

        Long locationId = addonService.getLocationIdForQuote(quoteId);
        authorizationService.requireStaffOrManagerLocation(authorization, locationId);

        AddonQuoteResult quote = addonService.recordCustomerAcceptance(
                quoteId,
                request.acceptedAt(),
                request.acceptanceReference()
        );

        return ResponseEntity.ok(toResponse(quote));
    }

    @PostMapping(value = "/api/v1/ota-addon-quotes/{quoteId}/direct-bill")
    public ResponseEntity<DirectBillLinkResponse> createDirectMlandAddonBill(
            @PathVariable String quoteId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest servletRequest
    ) {
        UUID.fromString(quoteId);

        Long locationId = addonService.getLocationIdForQuote(quoteId);
        AccountSummary actor = authorizationService.requireStaffOrManagerLocation(authorization, locationId);

        String staffActorReference = actor != null
                ? (actor.role() == ActorRole.MANAGER ? "MANAGER-" + actor.accountId() : "STAFF-" + actor.accountId())
                : "STAFF";

        DirectBillResult billResult = addonService.createDirectBill(
                quoteId,
                staffActorReference,
                locationId
        );

        String correlationId = CorrelationIdFilter.current(servletRequest);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        DirectBillLinkResponse response = new DirectBillLinkResponse(
                quoteId,
                billResult.settlementBillReference(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private AddonQuoteResponse toResponse(AddonQuoteResult quote) {
        BigDecimal decimalAmount = OtaCurrencyMoney.fromMinor(quote.amountMinor(), quote.currencyCode());
        Money money = new Money(decimalAmount.toPlainString(), quote.currencyCode());
        return new AddonQuoteResponse(
                quote.quoteReference(),
                quote.channelBookingReference(),
                quote.addonType(),
                quote.itemCode(),
                money,
                quote.status()
        );
    }
}
