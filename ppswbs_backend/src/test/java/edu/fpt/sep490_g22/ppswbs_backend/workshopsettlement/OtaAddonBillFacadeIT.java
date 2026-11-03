package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementAddonBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementHandoffIdempotencyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementFacadeImpl;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaAddonBillFacadeIT {
    @Mock SettlementCancellationCaseRepository cases;
    @Mock SettlementHandoffIdempotencyRepository idempotency;
    @Mock SettlementNotificationHandoffRepository notifications;
    @Mock SettlementAddonBillRepository addonBills;
    @Mock FinalPaymentService finalPayment;
    private WorkshopSettlementFacade facade;

    @BeforeEach
    void setUp() {
        WorkshopSettlementFacadeImpl implementation = new WorkshopSettlementFacadeImpl(
                cases, idempotency, new ObjectMapper(), finalPayment, notifications);
        ReflectionTestUtils.setField(implementation, "addonBills", addonBills);
        facade = implementation;
    }

    @Test
    void createsSeparateShopBillAndDoesNotCreateOtaPayment() {
        when(addonBills.findByQuoteReference("QUOTE-1")).thenReturn(Optional.empty());
        when(addonBills.saveAndFlush(any(SettlementAddonBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkshopSettlementFacade.DirectShopAddonBillOutcome result = facade.createDirectShopAddonBill(
                new WorkshopSettlementFacade.DirectShopAddonBillCommand(
                        "OTA-BK-1", "QUOTE-1", "LASER-UPGRADE", "Special material",
                        25000, "VND", "CONSENT-1", "STAFF-1", 7L));

        assertThat(result.created()).isTrue();
        assertThat(result.billReference()).startsWith("SETTLE-ADDON-BILL-");
        verify(addonBills).saveAndFlush(any(SettlementAddonBill.class));
        verifyNoInteractions(finalPayment);
    }

    @Test
    void replaysExistingQuoteWithoutCreatingAnotherBill() {
        SettlementAddonBill existing = SettlementAddonBill.builder()
                .billReference("SETTLE-ADDON-BILL-1")
                .channelBookingReference("OTA-BK-1")
                .quoteReference("QUOTE-1")
                .amountMinor(10000L)
                .currencyCode("VND")
                .build();
        when(addonBills.findByQuoteReference("QUOTE-1")).thenReturn(Optional.of(existing));

        WorkshopSettlementFacade.DirectShopAddonBillOutcome result = facade.createDirectShopAddonBill(
                new WorkshopSettlementFacade.DirectShopAddonBillCommand(
                        "OTA-BK-1", "QUOTE-1", "UPGRADE", "Upgrade",
                        10000, "VND", "CONSENT-1", "STAFF-1", null));

        assertThat(result.created()).isTrue();
        assertThat(result.billReference()).isEqualTo("SETTLE-ADDON-BILL-1");
        verify(addonBills, never()).saveAndFlush(any());
    }

    @Test
    void rejectsChangedReplayForSameQuoteReference() {
        SettlementAddonBill existing = SettlementAddonBill.builder()
                .billReference("SETTLE-ADDON-BILL-1")
                .channelBookingReference("OTA-BK-1")
                .quoteReference("QUOTE-1")
                .amountMinor(10000L)
                .currencyCode("VND")
                .build();
        when(addonBills.findByQuoteReference("QUOTE-1")).thenReturn(Optional.of(existing));

        // Replay with different amount (20000 != 10000)
        WorkshopSettlementFacade.DirectShopAddonBillOutcome result = facade.createDirectShopAddonBill(
                new WorkshopSettlementFacade.DirectShopAddonBillCommand(
                        "OTA-BK-1", "QUOTE-1", "UPGRADE", "Upgrade",
                        20000, "VND", "CONSENT-1", "STAFF-1", null));

        assertThat(result.created()).isFalse();
        assertThat(result.errorCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
        verify(addonBills, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingConsentAndNonVndBeforePersistence() {
        WorkshopSettlementFacade.DirectShopAddonBillOutcome missingConsent = facade.createDirectShopAddonBill(
                new WorkshopSettlementFacade.DirectShopAddonBillCommand(
                        "OTA-BK-1", "QUOTE-1", "UPGRADE", "Upgrade",
                        10000, "VND", "", "STAFF-1", null));
        WorkshopSettlementFacade.DirectShopAddonBillOutcome wrongCurrency = facade.createDirectShopAddonBill(
                new WorkshopSettlementFacade.DirectShopAddonBillCommand(
                        "OTA-BK-1", "QUOTE-2", "UPGRADE", "Upgrade",
                        10000, "USD", "CONSENT-1", "STAFF-1", null));

        assertThat(missingConsent.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(wrongCurrency.errorCode()).isEqualTo("INVALID_AMOUNT_OR_CURRENCY");
        verifyNoInteractions(addonBills);
    }
}
