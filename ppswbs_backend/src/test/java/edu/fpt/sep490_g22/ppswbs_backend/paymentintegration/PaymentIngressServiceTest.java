package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPaymentEvidenceFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.facade.CustomOrderPaymentFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentIngressServiceTest {
    @Mock VnpayChecksumService checksum;
    @Mock PaymentOwnerRegistry registry;
    @Mock PaymentIngressReceiptRepository receipts;
    @Mock WorkshopBookingFacade booking;
    @Mock WorkshopSettlementFacade settlement;
    @Mock RetailPaymentEvidenceFacade retail;
    @Mock CustomOrderPaymentFacade customOrderPayment;

    @Test void rejectsInvalidSignatureWithoutResolvingOwner() {
        when(checksum.valid(anyMap(), any())).thenReturn(false);
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("INVALID_SIGNATURE");
        assertThat(outcome.rspCode()).isEqualTo("97");
        verifyNoInteractions(registry, receipts, booking, settlement);
    }

    @Test void rejectsUnknownReference() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.empty());
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("UNKNOWN_REFERENCE");
        assertThat(outcome.rspCode()).isEqualTo("01");
        verifyNoInteractions(receipts, booking, settlement);
    }

    @Test void retailCallbackIsSentOnlyToRetailReceiver() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        var retailOwner = PaymentOwnerRegistration.builder().merchantReference("RETAIL-1")
                .bookingReference("CHECKOUT-1").paymentKind(PaymentOwnerKind.RETAIL)
                .amountVnd(300_000L).currencyCode("VND").build();
        when(registry.lockOwner("RETAIL-1")).thenReturn(Optional.of(retailOwner));
        when(registry.lockOwner("RETAIL-1")).thenReturn(Optional.of(retailOwner));

        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service().receive(params("RETAIL-1", "30000000", "00", "TX-1")).message()).isEqualTo("OK");
        verify(retail).applyVerifiedPayment(any());
        verifyNoInteractions(booking, settlement);
    }

    @Test void rejectsAmountMismatchBeforeDispatch() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        var outcome = service().receive(params("DEP-BK-1-1", "5100000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("AMOUNT_CURRENCY_MISMATCH");
        assertThat(outcome.rspCode()).isEqualTo("04");
        // not a group payment: the facade answers false, so no receipt is stored and nothing is dispatched
        verify(booking).recordMismatchedGroupPaymentEvidence(any());
        verify(receipts, never()).saveAndFlush(any());
        verifyNoInteractions(settlement);
    }

    @Test void recordsVerifiedMismatchedGroupEvidenceOnceAndStillAnswers04() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(booking.recordMismatchedGroupPaymentEvidence(any())).thenReturn(true);
        var outcome = service().receive(params("DEP-BK-1-1", "5100000", "00", "TX-1"));
        assertThat(outcome.rspCode()).isEqualTo("04");
        verify(receipts).saveAndFlush(argThat(r -> r.getAmountVnd() == 51_000L && r.isTerminalOutcome()
                && "DEP-BK-1-1|TX-1|00|00".equals(r.getDeliveryReference())));
        verify(booking, never()).applyVerifiedDepositPaymentEvidence(any());

        // replay of the same delivery: the receipt exists, so nothing more is recorded
        when(receipts.existsByDeliveryReference("DEP-BK-1-1|TX-1|00|00")).thenReturn(true);
        assertThat(service().receive(params("DEP-BK-1-1", "5100000", "00", "TX-1")).rspCode()).isEqualTo("04");
        verify(booking, times(1)).recordMismatchedGroupPaymentEvidence(any());
        verify(receipts, times(1)).saveAndFlush(any());
    }

    @Test void doesNotRecordAMismatchedOutcomeThatIsNotAVerifiedSuccess() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        var outcome = service().receive(params("DEP-BK-1-1", "5100000", "24", "TX-1"));
        assertThat(outcome.rspCode()).isEqualTo("04");
        verifyNoInteractions(receipts, booking, settlement);
    }

    @Test void rejectsCurrencyMismatchBeforeDispatch() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        var callback = params("DEP-BK-1-1", "5000000", "00", "TX-1");
        callback.put("vnp_CurrCode", "USD");
        var outcome = service().receive(callback);
        assertThat(outcome.message()).isEqualTo("AMOUNT_CURRENCY_MISMATCH");
        assertThat(outcome.rspCode()).isEqualTo("04");
        verify(booking).recordMismatchedGroupPaymentEvidence(any());
        verify(receipts, never()).saveAndFlush(any());
        verifyNoInteractions(settlement);
    }

    @Test void acceptsVnPayCallbackWhenCurrencyFieldIsOmitted() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference("DEP-BK-1-1|TX-1|00|00")).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", "00", "TX-1");
        callback.remove("vnp_CurrCode");

        var outcome = service().receive(callback);

        assertThat(outcome.message()).isEqualTo("OK");
        verify(booking).applyVerifiedDepositPaymentEvidence(any());
    }

    @Test void deduplicatesSuccessfulDeliveryWhileAllowingConsumerProjectionRepair() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference("DEP-BK-1-1|TX-1|00|00")).thenReturn(true);
        var outcome = service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("Already received");
        assertThat(outcome.rspCode()).isEqualTo("02");
        verify(receipts, never()).saveAndFlush(any());
        verify(booking).applyVerifiedDepositPaymentEvidence(any());
        verifyNoInteractions(settlement);
    }

    @Test void dispatchesValidDepositExactlyOnce() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference("DEP-BK-1-1|TX-1|00|00")).thenReturn(false);
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
                .providerCreateDate("20261030181359")
                .build();
        when(registry.lockOwner("FINAL-BK-1-1")).thenReturn(Optional.of(settlementOwner));
        when(receipts.existsByDeliveryReference("FINAL-BK-1-1|TX-1|00|00")).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlement.applyVerifiedSettlementPaymentEvidence(any())).thenReturn(SettlementPaymentEvidenceOutcome.accepted());
        var outcome = service().receive(params("FINAL-BK-1-1", "7500000", "00", "TX-1"));
        assertThat(outcome.message()).isEqualTo("OK");
        verify(settlement).applyVerifiedSettlementPaymentEvidence(any());
        verifyNoInteractions(booking);
    }

    @Test void dispatchesVerifiedCustomOrderFullPaymentToCustomOrderFacade() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        var owner = PaymentOwnerRegistration.builder()
                .merchantReference("CUSTOM-ORDER-1")
                .bookingReference("CO-1")
                .paymentKind(PaymentOwnerKind.CUSTOM_ORDER_FULL_PAYMENT)
                .amountVnd(4_500_000L)
                .currencyCode("VND")
                .build();
        when(registry.lockOwner("CUSTOM-ORDER-1")).thenReturn(Optional.of(owner));
        when(receipts.existsByDeliveryReference("CUSTOM-ORDER-1|TX-1|00|00")).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var outcome = serviceWithCustomPayment().receive(
                params("CUSTOM-ORDER-1", "450000000", "00", "TX-1"));

        assertThat(outcome.message()).isEqualTo("OK");
        verify(customOrderPayment).applyVerifiedFullPayment(
                "CO-1", "CUSTOM-ORDER-1", "TX-1", 4_500_000L, "VND");
        verifyNoInteractions(booking, settlement, retail);
    }

    @Test void acknowledgesFailedProviderResponseWithoutDispatch() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference(anyString())).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", "24", "TX-1");
        callback.put("vnp_TransactionStatus", "02");
        var outcome = service().receive(callback);
        assertThat(outcome.message()).isEqualTo("OK");
        var saved = org.mockito.ArgumentCaptor.forClass(PaymentIngressReceipt.class);
        verify(receipts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isTerminalOutcome()).isTrue();
        // FR-032 (2026-11-19): a verified failure is reported to Booking, never a confirmation.
        verify(booking).recordDefinitePaymentFailure(any());
        verifyNoMoreInteractions(booking);
        verifyNoInteractions(settlement);
    }

    @Test void acknowledgesStatus01AsNonterminalWithoutDispatch() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference(anyString())).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", "00", "TX-1");
        callback.put("vnp_TransactionStatus", "01");

        var outcome = service().receive(callback);

        assertThat(outcome.message()).isEqualTo("OK");
        var saved = org.mockito.ArgumentCaptor.forClass(PaymentIngressReceipt.class);
        verify(receipts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isTerminalOutcome()).isFalse();
        verifyNoInteractions(booking, settlement);
    }

    @Test void doesNotConfirmWhenResponseCodeIsSuccessButTransactionStatusIsTerminalFailure() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference(anyString())).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", "00", "TX-1");
        callback.put("vnp_TransactionStatus", "02");

        var outcome = service().receive(callback);

        assertThat(outcome.message()).isEqualTo("OK");
        var saved = org.mockito.ArgumentCaptor.forClass(PaymentIngressReceipt.class);
        verify(receipts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isTerminalOutcome()).isTrue();
        // FR-032 (2026-11-19): a verified failure is reported to Booking, never a confirmation.
        verify(booking).recordDefinitePaymentFailure(any());
        verifyNoMoreInteractions(booking);
        verifyNoInteractions(settlement);
    }

    @Test void allowsLaterSuccessAfterSameTransactionWasPreviouslyReportedAsStatus01() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.existsByDeliveryReference(anyString())).thenReturn(false);
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var pending = params("DEP-BK-1-1", "5000000", "00", "TX-1");
        pending.put("vnp_TransactionStatus", "01");

        assertThat(service().receive(pending).message()).isEqualTo("OK");
        assertThat(service().receive(params("DEP-BK-1-1", "5000000", "00", "TX-1")).message()).isEqualTo("OK");

        verify(receipts, times(2)).saveAndFlush(any());
        verify(booking).applyVerifiedDepositPaymentEvidence(any());
    }

    // ---- 003.1 FR-032 definite failure (T168/T171) ----

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"09", "10", "11", "12", "13", "24", "51", "65", "79"})
    void aVerifiedIpnWithAListedFailureCodeIsReportedToBookingAsADefiniteFailure(String code) {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", code, "TX-1");
        callback.put("vnp_TransactionStatus", "01");
        assertThat(service().receive(callback).rspCode()).isEqualTo("00");
        verify(booking).recordDefinitePaymentFailure(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking
                .facade.RecordDefinitePaymentFailureCommand("DEP-BK-1-1", code, "01", "DEP-BK-1-1|TX-1|" + code + "|01"));
        verify(booking, never()).applyVerifiedDepositPaymentEvidence(any());
    }

    @Test void transactionStatus02IsADefiniteFailureWhateverTheResponseCode() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", "99", "TX-1");
        callback.put("vnp_TransactionStatus", "02");
        service().receive(callback);
        verify(booking).recordDefinitePaymentFailure(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"01", "07", "75", "99", "42"})
    void pendingSuspectedBankMaintenanceOtherAndUnknownCodesChangeNothing(String code) {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        when(receipts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var callback = params("DEP-BK-1-1", "5000000", code, "TX-1");
        callback.put("vnp_TransactionStatus", "01");
        service().receive(callback);
        verify(booking, never()).recordDefinitePaymentFailure(any());
    }

    @Test void aWrongAmountOrSignatureNeverReportsAFailure() {
        when(checksum.valid(anyMap(), any())).thenReturn(true);
        when(registry.lockOwner("DEP-BK-1-1")).thenReturn(Optional.of(owner(50_000L)));
        service().receive(params("DEP-BK-1-1", "5100000", "24", "TX-1"));
        when(checksum.valid(anyMap(), any())).thenReturn(false);
        service().receive(params("DEP-BK-1-1", "5000000", "24", "TX-2"));
        verify(booking, never()).recordDefinitePaymentFailure(any());
    }

    private PaymentIngressService service() { return new PaymentIngressService(checksum, registry, receipts, booking, settlement, retail); }
    private PaymentIngressService serviceWithCustomPayment() {
        return new PaymentIngressService(checksum, registry, receipts, booking, settlement, retail,
                Optional.of(customOrderPayment));
    }
    private PaymentOwnerRegistration owner(long amount) { return PaymentOwnerRegistration.builder().merchantReference("DEP-BK-1-1").bookingReference("BK-1").paymentKind(PaymentOwnerKind.DEPOSIT).amountVnd(amount).currencyCode("VND").build(); }
    private Map<String,String> params(String ref, String amount, String response, String txn) {
        return new HashMap<>(Map.of("vnp_TxnRef", ref, "vnp_Amount", amount, "vnp_CurrCode", "VND", "vnp_ResponseCode", response, "vnp_TransactionStatus", "00", "vnp_TransactionNo", txn, "vnp_SecureHash", "hash"));
    }
}
