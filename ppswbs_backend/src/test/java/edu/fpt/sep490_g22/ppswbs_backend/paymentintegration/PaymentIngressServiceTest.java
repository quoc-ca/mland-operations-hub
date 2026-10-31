package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentIngressServiceTest {
    @Mock VnpayChecksumService checksum;
    @Mock PaymentOwnerRegistry registry;
    @Mock PaymentIngressReceiptRepository receipts;
    @Mock WorkshopBookingFacade booking;
    @Mock WorkshopSettlementFacade settlement;

    @Test void rejectsInvalidSignatureWithoutResolvingOwner() {
        when(checksum.valid(anyMap(), any())).thenReturn(false);
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("INVALID_SIGNATURE");
        verifyNoInteractions(registry, receipts, booking, settlement);
    }

    @Test void rejectsUnknownReference() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.resolve("DEP-BK-1-1")).thenReturn(Optional.empty());
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("UNKNOWN_REFERENCE");
        verifyNoInteractions(receipts, booking, settlement);
    }

    @Test void rejectsAmountMismatchBeforeDispatch() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.resolve("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        var outcome = service().receive(params("DEP-BK-1-1", "5100000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("AMOUNT_CURRENCY_MISMATCH");
        verifyNoInteractions(receipts, booking, settlement);
    }

    @Test void rejectsCurrencyMismatchBeforeDispatch() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.resolve("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        var callback = params("DEP-BK-1-1", "5000000", "00", "TX-1");
        callback.put("vnp_CurrCode", "USD");
        var outcome = service().receive(callback);
        assertThat(outcome.message()).isEqualTo("AMOUNT_CURRENCY_MISMATCH");
        verifyNoInteractions(receipts, booking, settlement);
    }

    @Test void acceptsVnPayCallbackWhenCurrencyFieldIsOmitted() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.resolve("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference("DEP-BK-1-1|TX-1")).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", "00", "TX-1");
        callback.remove("vnp_CurrCode");

        var outcome = service().receive(callback);

        assertThat(outcome.message()).isEqualTo("OK");
        verify(booking).applyVerifiedDepositPaymentEvidence(any());
    }

    @Test void deduplicatesSuccessfulDeliveryWhileAllowingConsumerProjectionRepair() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.resolve("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference("DEP-BK-1-1|TX-1")).thenReturn(true);
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("Already received");
        verify(receipts, never()).saveAndFlush(any());
        verify(booking).applyVerifiedDepositPaymentEvidence(any());
        verifyNoInteractions(settlement);
    }

    @Test void dispatchesValidDepositExactlyOnce() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.resolve("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference("DEP-BK-1-1|TX-1")).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("OK");
        verify(booking).applyVerifiedDepositPaymentEvidence(any());
        verifyNoInteractions(settlement);
    }

    @Test void dispatchesValidSettlementExactlyOnceWithoutBroadcast() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        var settlementOwner = PaymentOwnerRegistration.builder()
                .merchantReference("FINAL-BK-1-1")
                .bookingReference("BK-1")
                .paymentKind(PaymentOwnerKind.FINAL_PAYMENT)
                .amountVnd(75_000L)
                .currencyCode("VND")
                .build();
        when(registry.resolve("FINAL-BK-1-1")).thenReturn(Optional.of(settlementOwner));
        when(receipts.existsByDeliveryReference("FINAL-BK-1-1|TX-1")).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlement.applyVerifiedSettlementPaymentEvidence(any())).thenReturn(SettlementPaymentEvidenceOutcome.accepted());
        var outcome = service().receive(params("FINAL-BK-1-1", "7500000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("OK");
        verify(settlement).applyVerifiedSettlementPaymentEvidence(any());
        verifyNoInteractions(booking);
    }

    @Test void acknowledgesFailedProviderResponseWithoutDispatch() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.resolve("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "24", "TX-1"));
        assertThat(outcome.message()).isEqualTo("OK");
        verifyNoInteractions(receipts, booking, settlement);
    }

    private PaymentIngressService service() { return new PaymentIngressService(checksum, registry, receipts, booking, settlement); }
    private PaymentOwnerRegistration owner(long amount) { return PaymentOwnerRegistration.builder().merchantReference("DEP-BK-1-1").bookingReference("BK-1").paymentKind(PaymentOwnerKind.DEPOSIT).amountVnd(amount).currencyCode("VND").build(); }
    private Map<String,String> params(String ref, String amount, String response, String txn) {
        return new HashMap<>(Map.of("vnp_TxnRef", ref, "vnp_Amount", amount, "vnp_CurrCode", "VND", "vnp_ResponseCode", response, "vnp_TransactionNo", txn, "vnp_SecureHash", "hash"));
    }
}
