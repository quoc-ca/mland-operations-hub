package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.PaymentIngressService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class VnpayPaymentIngressIT {

    @Autowired private PaymentIngressService ingress;
    @Autowired private PaymentOwnerRegistry owners;
    @Autowired private PaymentIngressReceiptRepository receipts;

    @MockBean private VnpayChecksumService checksum;
    @MockBean private WorkshopBookingFacade booking;
    @MockBean private WorkshopSettlementFacade settlement;

    @Test
    void invalidSignatureDoesNotResolveOrPersistOrDispatch() {
        String merchant = "IT-INVALID-" + UUID.randomUUID();
        owners.register(merchant, "BK-INVALID", PaymentOwnerKind.DEPOSIT, 50_000L, "VND");
        when(checksum.valid(anyMap(), any())).thenReturn(false);

        PaymentIngressService.Outcome outcome = ingress.receive(params(merchant, "5000000", "00", "TX-INVALID"));

        assertThat(outcome.message()).isEqualTo("INVALID_SIGNATURE");
        assertThat(receipts.existsByDeliveryReference(merchant + "|TX-INVALID")).isFalse();
        verifyNoInteractions(booking, settlement);
    }

    @Test
    void verifiedOwnerDispatchesOnlyToItsConsumerAndDuplicateIsRecognized() {
        String depositMerchant = "IT-DEPOSIT-" + UUID.randomUUID();
        String finalMerchant = "IT-FINAL-" + UUID.randomUUID();
        owners.register(depositMerchant, "BK-DEPOSIT", PaymentOwnerKind.DEPOSIT, 50_000L, "VND");
        owners.register(finalMerchant, "BK-FINAL", PaymentOwnerKind.FINAL_PAYMENT, 75_000L, "VND", "20261030181359");
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(settlement.applyVerifiedSettlementPaymentEvidence(any()))
                .thenReturn(SettlementPaymentEvidenceOutcome.accepted());

        PaymentIngressService.Outcome firstDeposit = ingress.receive(
                params(depositMerchant, "5000000", "00", "TX-D-1"));
        PaymentIngressService.Outcome duplicateDeposit = ingress.receive(
                params(depositMerchant, "5000000", "00", "TX-D-1"));
        PaymentIngressService.Outcome finalPayment = ingress.receive(
                params(finalMerchant, "7500000", "00", "TX-F-1"));

        assertThat(firstDeposit.message()).isEqualTo("OK");
        assertThat(duplicateDeposit.message()).isEqualTo("Already received");
        assertThat(finalPayment.message()).isEqualTo("OK");
        assertThat(receipts.existsByDeliveryReference(depositMerchant + "|TX-D-1|00|00")).isTrue();
        assertThat(receipts.existsByDeliveryReference(finalMerchant + "|TX-F-1|00|00")).isTrue();
        verify(booking, times(2)).applyVerifiedDepositPaymentEvidence(any());
        verify(settlement).applyVerifiedSettlementPaymentEvidence(any());
        verifyNoMoreInteractions(booking, settlement);
    }

    @Test
    void unknownOwnerIsNotBroadcastOrRecorded() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        String merchant = "IT-UNKNOWN-" + UUID.randomUUID();

        PaymentIngressService.Outcome outcome = ingress.receive(params(merchant, "5000000", "00", "TX-U-1"));

        assertThat(outcome.message()).isEqualTo("UNKNOWN_REFERENCE");
        assertThat(receipts.existsByDeliveryReference(merchant + "|TX-U-1")).isFalse();
        verifyNoInteractions(booking, settlement);
    }

    private static Map<String, String> params(String merchant, String amount, String response, String transaction) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", merchant);
        params.put("vnp_Amount", amount);
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_ResponseCode", response);
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", transaction);
        params.put("vnp_SecureHash", "test-signature");
        return params;
    }
}
