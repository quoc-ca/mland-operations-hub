package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.AddonQuoteResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService.DirectBillResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

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
@DisplayName("T045: OTA Add-on Billing Integration Tests")
class OtaAddonBillingIT {

    @Mock private OtaChannelBookingRepository bookingRepository;
    @Mock private OtaAddonBillLinkRepository addonBillLinkRepository;
    @Mock private OtaAddonSettlementPort settlementPort;

    private OtaAddonService addonService;
    private final Map<String, OtaAddonBillLink> linkStore = new HashMap<>();

    private OtaChannelBooking booking;

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

        addonService = new OtaAddonService(bookingRepository, addonBillLinkRepository, settlementPort);

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
    @DisplayName("Free engraving: Zero charge succeeds as RECORDED_FREE; non-zero charge is strictly rejected")
    void freeEngraving_zeroChargeSucceeds_nonZeroChargeRejected() {
        // 1. Zero-charge laser engraving succeeds and status is RECORDED_FREE
        AddonQuoteResult freeQuote = addonService.createQuote(
                booking.getPublicReference(),
                OtaAddonService.ADDON_TYPE_LASER_ENGRAVING,
                "ENGRAVE-STD",
                "Complimentary Laser Engraving",
                0L,
                "VND"
        );

        assertThat(freeQuote.status()).isEqualTo(OtaAddonService.STATUS_RECORDED_FREE);
        assertThat(freeQuote.amountMinor()).isEqualTo(0L);
        assertThat(freeQuote.addonType()).isEqualTo("LASER_ENGRAVING");

        // 2. Attempting laser engraving with non-zero fee throws IllegalArgumentException
        assertThatThrownBy(() -> addonService.createQuote(
                booking.getPublicReference(),
                OtaAddonService.ADDON_TYPE_LASER_ENGRAVING,
                "ENGRAVE-STD",
                "Complimentary Laser Engraving",
                50_000L, // Non-zero fee invalid for free laser engraving
                "VND"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Laser engraving is free of charge and must have zero amount");
    }

    @Test
    @DisplayName("Missing consent rejection: Direct bill creation without customer acceptance is rejected")
    void missingConsent_directBillCreationRejected() {
        // 1. Quote a paid add-on (e.g. premium stone setting)
        AddonQuoteResult quote = addonService.createQuote(
                booking.getPublicReference(),
                OtaAddonService.ADDON_TYPE_PAID_OPTION,
                "OPT-STONE-PREM",
                "Premium Stone Inlay",
                200_000L,
                "VND"
        );

        assertThat(quote.status()).isEqualTo(OtaAddonService.STATUS_QUOTED);
        String quoteRef = quote.quoteReference();

        // 2. Attempting to create direct bill before customer acceptance throws IllegalStateException
        assertThatThrownBy(() -> addonService.createDirectBill(
                quoteRef,
                "consent-sig-guest",
                "staff-uuid-01",
                1001L
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Customer acceptance is required");

        // 3. Record customer acceptance with consent proof
        AddonQuoteResult accepted = addonService.recordCustomerAcceptance(
                quoteRef,
                Instant.now(),
                "consent-signed-form-reference-001"
        );
        assertThat(accepted.status()).isEqualTo(OtaAddonService.STATUS_ACCEPTED);

        // 4. Now creating direct bill succeeds
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
    @DisplayName("Paid add-on financial separation: OTA booking and channel payment facts remain unchanged")
    void paidAddon_preservesOtaChannelFinancialFacts() {
        // Given initial OTA booking facts
        long initialAccepted = booking.getAcceptedAmountMinor();
        long initialRetail = booking.getRetailAmountMinor();
        long initialCommission = booking.getCommissionAmountMinor();
        long initialFee = booking.getFeeAmountMinor();

        // Quote, accept, and bill a 300,000 VND add-on
        AddonQuoteResult quote = addonService.createQuote(
                booking.getPublicReference(),
                OtaAddonService.ADDON_TYPE_PAID_OPTION,
                "OPT-PLATINUM",
                "Platinum Plating",
                300_000L,
                "VND"
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
