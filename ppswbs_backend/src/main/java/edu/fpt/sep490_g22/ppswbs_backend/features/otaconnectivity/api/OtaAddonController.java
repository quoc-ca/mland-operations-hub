package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.AddonAcceptanceRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.AddonQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.AddonQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaAddonDtos.DirectBillLinkResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaChannelRequests.Money;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.AddonQuoteResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.DirectBillResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCurrencyMoney;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnprocessableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OtaAddonController {

    private final OtaAuthorizationService authorizationService;
    private final OtaAddonService addonService;

    @PostMapping(value = "/api/v1/ota-channel-bookings/{channelBookingId}/add-on-quotes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddonQuoteResponse> createOtaAddonQuote(
            @PathVariable String channelBookingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AddonQuoteRequest request
    ) {
        UUID.fromString(channelBookingId);

        Long locationId = addonService.getLocationIdForBooking(channelBookingId);
        authorizationService.requireStaffLocation(authorization, locationId);

        if (!"VND".equalsIgnoreCase(request.amount().currency())) {
            throw new OtaUnprocessableException("Only VND currency is supported for add-ons");
        }

        long amountMinor = OtaCurrencyMoney.toMinor(request.amount().amount(), request.amount().currency());

        if (OtaAddonService.ADDON_TYPE_LASER_ENGRAVING.equalsIgnoreCase(request.addonType())) {
            if (amountMinor != 0L) {
                throw new OtaUnprocessableException("Laser engraving is free of charge and must have zero amount");
            }
        } else if (OtaAddonService.ADDON_TYPE_PAID_OPTION.equalsIgnoreCase(request.addonType())) {
            if (request.itemCode() == null || request.itemCode().isBlank()) {
                throw new IllegalArgumentException("itemCode is required for PAID_OPTION");
            }
            if (amountMinor <= 0L) {
                throw new OtaUnprocessableException("Paid option requires a positive amount");
            }
        }

        AddonQuoteResult quote = addonService.createQuote(
                channelBookingId,
                request.addonType(),
                request.itemCode(),
                request.itemDescription(),
                amountMinor,
                request.amount().currency()
        );

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
        authorizationService.requireStaffLocation(authorization, locationId);

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
        AccountSummary actor = authorizationService.requireStaffLocation(authorization, locationId);

        String staffActorReference = actor != null ? "STAFF-" + actor.accountId() : "STAFF";

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

