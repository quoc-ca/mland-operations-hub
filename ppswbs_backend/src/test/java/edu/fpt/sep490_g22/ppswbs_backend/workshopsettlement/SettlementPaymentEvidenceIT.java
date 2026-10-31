package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.PaymentIngressService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class SettlementPaymentEvidenceIT {
    private final VnpayChecksumService checksum = mock(VnpayChecksumService.class);
    private final PaymentOwnerRegistry owners = mock(PaymentOwnerRegistry.class);
    private final PaymentIngressReceiptRepository receipts = mock(PaymentIngressReceiptRepository.class);
    private final WorkshopBookingFacade booking = mock(WorkshopBookingFacade.class);
    private final WorkshopSettlementFacade settlement = mock(WorkshopSettlementFacade.class);
    private final PaymentIngressService ingress = new PaymentIngressService(
            checksum, owners, receipts, booking, settlement);

    @Test
    void verifiedFinalPaymentDispatchesOnlyRedactedEvidenceToSettlementOwner() {
        String merchant = "FINAL-REF-1";
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(owners.resolve(merchant)).thenReturn(Optional.of(owner(merchant, "BK-1")));
        when(receipts.existsByDeliveryReference(any())).thenReturn(false);
        when(settlement.applyVerifiedSettlementPaymentEvidence(any()))
                .thenReturn(SettlementPaymentEvidenceOutcome.accepted());

        var outcome = ingress.receive(parameters(merchant, "5000000", "00", "00", "TX-1"));

        assertThat(outcome.message()).isEqualTo("OK");
        ArgumentCaptor<ApplyVerifiedSettlementPaymentEvidenceCommand> captor =
                ArgumentCaptor.forClass(ApplyVerifiedSettlementPaymentEvidenceCommand.class);
        verify(settlement).applyVerifiedSettlementPaymentEvidence(captor.capture());
        ApplyVerifiedSettlementPaymentEvidenceCommand command = captor.getValue();
        assertThat(command.bookingReference()).isEqualTo("BK-1");
        assertThat(command.paymentKind()).isEqualTo("FINAL_PAYMENT");
        assertThat(command.amountVnd()).isEqualTo(50_000L);
        assertThat(command.currencyCode()).isEqualTo("VND");
        assertThat(command.providerTransactionNumber()).isEqualTo("TX-1");
        assertThat(command.originalProviderCreateDate()).isEqualTo("20261030181359");
        assertThat(command.verifiedEvidenceReference()).isEqualTo("VNPAY-TX-1");
        assertThat(command.toString()).doesNotContain("vnp_SecureHash", "secret", "raw");
        verifyNoInteractions(booking);
        verify(receipts).saveAndFlush(any());
    }

    @Test
    void invalidSignatureNeverReachesSettlementOrReceiptStore() {
        when(checksum.valid(anyMap(), any())).thenReturn(false);

        var outcome = ingress.receive(parameters("FINAL-REF-2", "5000000", "00", "00", "TX-2"));

        assertThat(outcome.message()).isEqualTo("INVALID_SIGNATURE");
        verifyNoInteractions(owners, receipts, booking, settlement);
    }

    @Test
    void amountMismatchNeverDispatchesSettlementEvidence() {
        String merchant = "FINAL-REF-3";
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(owners.resolve(merchant)).thenReturn(Optional.of(owner(merchant, "BK-3")));

        var outcome = ingress.receive(parameters(merchant, "4999900", "00", "00", "TX-3"));

        assertThat(outcome.message()).isEqualTo("AMOUNT_CURRENCY_MISMATCH");
        verifyNoInteractions(receipts, booking, settlement);
    }

    private static PaymentOwnerRegistration owner(String merchant, String bookingReference) {
        return PaymentOwnerRegistration.builder().merchantReference(merchant).bookingReference(bookingReference)
                .paymentKind(PaymentOwnerKind.FINAL_PAYMENT).amountVnd(50_000L).currencyCode("VND")
                .providerCreateDate("20261030181359").build();
    }

    private static Map<String, String> parameters(String merchant, String amount, String response,
                                                   String status, String transaction) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", merchant);
        params.put("vnp_Amount", amount);
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_ResponseCode", response);
        params.put("vnp_TransactionStatus", status);
        params.put("vnp_TransactionNo", transaction);
        params.put("vnp_SecureHash", "sensitive-provider-checksum");
        params.put("vnp_OrderInfo", "private provider payload");
        return params;
    }
}
