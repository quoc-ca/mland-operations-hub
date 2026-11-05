package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.AddonQuoteResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.DirectBillResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnprocessableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort.DirectBillCreationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaAddonBillLink;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaAddonBillLinkRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopAddOnPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T045/T055: OTA Add-on Billing Integration Tests")
class OtaAddonBillingIT {

    @Mock private OtaChannelBookingRepository bookingRepository;
    @Mock private OtaAddonBillLinkRepository addonBillLinkRepository;
    @Mock private OtaAddonSettlementPort settlementPort;
    @Mock private WorkshopOperationsFacade workshopOperationsFacade;

    private OtaAddonService addonService;
    private final Map<String, OtaAddonBillLink> linkStore = new HashMap<>();

    private OtaChannelBooking booking;

    private static final String CATALOGUE_REF_PREMIUM_STONE = "abc00000-0000-4000-8000-000000000001";
    private static final String CATALOGUE_REF_PLATINUM = "abc00000-0000-4000-8000-000000000002";

    @BeforeEach
    void setUp() {
        linkStore.clear();

        when(addonBillLinkRepository.save(any(OtaAddonBillLink.class))).thenAnswer(invocation -> {
            OtaAddonBillLink link = invocation.getArgument(0);
            linkStore.put(link.getSettlementQuoteReference(), link);
            return link;
        });

        when(addonBillLinkRepository.findBySettlementQuoteReference(any())).thenAnswer(invocation -> {
            String ref = invocation.getArgument(0);
            return Optional.ofNullable(linkStore.get(ref));
        });

        addonService = new OtaAddonService(bookingRepository, addonBillLinkRepository, settlementPort, workshopOperationsFacade);

        OtaChannelMapping mapping = OtaChannelMapping.builder()
                .id(1L)
                .connection(OtaChannelConnection.builder()
                        .providerCode(OtaProvider.KLOOK)
                        .environment(OtaEnvironment.TEST)
                        .build())
                .offerReference("offer-001")
                .sessionReference("sess-001")
                .locationReference("1001")
                .externalProductId("prod-001")
                .externalOptionId("opt-001")
                .status(OtaMappingStatus.BOOKING_ENABLED)
                .build();

        booking = OtaChannelBooking.capture(
                mapping,
                "ext-book-ref-001",
                "CONFIRMED",
                1,
                "mland-book-001",
                "KLOOK",
                "policy-001",
                "v1",
                Instant.now(),
                "Asia/Ho_Chi_Minh",
                100_000L,
                80_000L,
                15_000L,
                5_000L,
                100_000L,
                "VND"
        );

        when(bookingRepository.findByPublicReference(booking.getPublicReference())).thenReturn(Optional.of(booking));
    }

    @Test
    @DisplayName("Free engraving: Zero charge succeeds as RECORDED_FREE and must never create a bill (D16)")
    void freeEngraving_zeroChargeSucceeds_neverCreatesBill() {
        // 1. Zero-charge laser engraving succeeds and status is RECORDED_FREE
        AddonQuoteResult freeQuote = addonService.createLaserEngravingQuote(
                booking.getPublicReference(),
                "Complimentary Laser Engraving"
        );

        assertThat(freeQuote.status()).isEqualTo(OtaAddonService.STATUS_RECORDED_FREE);
        assertThat(freeQuote.amountMinor()).isEqualTo(0L);
        assertThat(freeQuote.addonType()).isEqualTo("LASER_ENGRAVING");
        assertThat(freeQuote.itemCode()).isNull();

        // 2. Direct bill creation for free laser engraving must strictly be rejected
        assertThatThrownBy(() -> addonService.createDirectBill(
                freeQuote.quoteReference(),
                "consent-sig-guest",
                "staff-uuid-01",
                1001L
        )).isInstanceOf(OtaUnprocessableException.class)
          .hasMessageContaining("Laser engraving is free and does not generate a bill");
    }

    @Test
    @DisplayName("Paid option D17: Derives price/description from 003.2 catalogue; rejects bill before customer acceptance")
    void paidOption_derivesFromCatalogue_requiresAcceptanceBeforeBill() {
        // 1. Mock active 003.2 catalogue add-on for booking offer "offer-001" and location 1001L
        when(workshopOperationsFacade.getWorkshopAddOnPrice(
                new WorkshopAddOnPriceQuery("offer-001", 1001L, CATALOGUE_REF_PREMIUM_STONE)))
                .thenReturn(new WorkshopAddOnPriceSnapshot(CATALOGUE_REF_PREMIUM_STONE, "Premium Stone Inlay", 200_000L, "VND"));

        // 2. Quote the paid add-on using catalogue reference
        AddonQuoteResult quote = addonService.createPaidOptionQuote(
                booking.getPublicReference(),
                CATALOGUE_REF_PREMIUM_STONE
        );

        assertThat(quote.status()).isEqualTo(OtaAddonService.STATUS_QUOTED);
        assertThat(quote.amountMinor()).isEqualTo(200_000L);
        assertThat(quote.currencyCode()).isEqualTo("VND");
        assertThat(quote.itemCode()).isEqualTo(CATALOGUE_REF_PREMIUM_STONE);
        String quoteRef = quote.quoteReference();

        // 3. Attempting to create direct bill before customer acceptance throws OtaConflictException
        assertThatThrownBy(() -> addonService.createDirectBill(
                quoteRef,
                "consent-sig-guest",
                "staff-uuid-01",
                1001L
        )).isInstanceOf(OtaConflictException.class)
          .hasMessageContaining("Customer acceptance is required");

        // 4. Record customer acceptance with consent proof
        AddonQuoteResult accepted = addonService.recordCustomerAcceptance(
                quoteRef,
                Instant.now(),
                "consent-signed-form-reference-001"
        );
        assertThat(accepted.status()).isEqualTo(OtaAddonService.STATUS_ACCEPTED);

        // 5. Now creating direct bill succeeds with Settlement facade
        when(settlementPort.createDirectBill(any())).thenReturn(
                DirectBillCreationOutcome.success("bill-settle-009", "https://vnpay.mock/pay/009")
        );

        DirectBillResult billResult = addonService.createDirectBill(
                quoteRef,
                "consent-signed-form-reference-001",
                "staff-uuid-01",
                1001L
        );

        assertThat(billResult.status()).isEqualTo(OtaAddonService.STATUS_BILLED);
        assertThat(billResult.settlementBillReference()).isEqualTo("bill-settle-009");
    }

    @Test
    @DisplayName("Paid option: Missing, inactive, or out-of-scope catalogue reference is strictly rejected")
    void paidOption_missingOrInactiveCatalogueReference_rejected() {
        // Mock facade returning null (inactive or out of scope for package/location)
        when(workshopOperationsFacade.getWorkshopAddOnPrice(
                new WorkshopAddOnPriceQuery("offer-001", 1001L, "missing-or-inactive-ref")))
                .thenReturn(null);

        assertThatThrownBy(() -> addonService.createPaidOptionQuote(
                booking.getPublicReference(),
                "missing-or-inactive-ref"
        )).isInstanceOf(OtaUnprocessableException.class)
          .hasMessageContaining("Catalogue add-on is missing, inactive, or not valid");
    }

    @Test
    @DisplayName("Paid add-on financial separation: OTA booking and channel payment facts remain unchanged")
    void paidAddon_preservesOtaChannelFinancialFacts() {
        // Given initial OTA booking facts
        long initialAccepted = booking.getAcceptedAmountMinor();
        long initialRetail = booking.getRetailAmountMinor();
        long initialCommission = booking.getCommissionAmountMinor();
        long initialFee = booking.getFeeAmountMinor();

        when(workshopOperationsFacade.getWorkshopAddOnPrice(
                new WorkshopAddOnPriceQuery("offer-001", 1001L, CATALOGUE_REF_PLATINUM)))
                .thenReturn(new WorkshopAddOnPriceSnapshot(CATALOGUE_REF_PLATINUM, "Platinum Plating", 300_000L, "VND"));

        // Quote, accept, and bill a 300,000 VND add-on
        AddonQuoteResult quote = addonService.createPaidOptionQuote(
                booking.getPublicReference(),
                CATALOGUE_REF_PLATINUM
        );
        addonService.recordCustomerAcceptance(quote.quoteReference(), Instant.now(), "consent-ref-99");

        when(settlementPort.createDirectBill(any())).thenReturn(
                DirectBillCreationOutcome.success("bill-settle-010", "https://vnpay.mock/pay/010")
        );

        DirectBillResult bill = addonService.createDirectBill(
                quote.quoteReference(),
                "consent-ref-99",
                "staff-uuid-01",
                1001L
        );
        assertThat(bill.settlementBillReference()).isEqualTo("bill-settle-010");

        // Assert: OTA Channel Booking financials remain strictly identical and isolated
        assertThat(booking.getAcceptedAmountMinor()).isEqualTo(initialAccepted);
        assertThat(booking.getRetailAmountMinor()).isEqualTo(initialRetail);
        assertThat(booking.getCommissionAmountMinor()).isEqualTo(initialCommission);
        assertThat(booking.getFeeAmountMinor()).isEqualTo(initialFee);
    }
}
