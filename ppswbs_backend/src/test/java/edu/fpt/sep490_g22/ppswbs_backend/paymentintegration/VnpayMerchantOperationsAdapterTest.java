package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.VnpayMerchantOperation;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnpayMerchantOperationsAdapterTest {
    private static final String SECRET = "merchant-api-test-secret";
    @Mock PaymentOwnerRegistry owners;
    @Mock VnpayMerchantOperationJournal journal;
    @Mock VnpayMerchantApiTransport transport;

    @Test
    void queryUsesOriginalTransactionDateAndReturnsOnlyVerifiedReconciliation() {
        var operation = operation("op-query", "QUERYDR", "IN_FLIGHT");
        when(owners.resolve("DEP-BOOKING-1")).thenReturn(Optional.of(owner()));
        when(journal.prepare(eq("op-query"), anyString(), eq("QUERYDR"), anyString(), eq("DEP-BOOKING-1")))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq("https://sandbox.example/query"), anyMap())).thenReturn(signedQueryResponse());
        when(journal.complete(eq("op-query"), eq("RECONCILED"), eq("00"), eq("01"), eq("00"), eq("12345678"), eq(50_000L)))
                .thenAnswer(invocation -> completed(operation, "RECONCILED", "00", "01", "00", "12345678", 50_000L));

        var result = adapter().queryTransaction(new VnpayQueryDrCommand("op-query", "DEP-BOOKING-1",
                "20261030181359", "Workshop deposit", "203.0.113.10"));

        ArgumentCaptor<Map<String, String>> request = ArgumentCaptor.forClass(Map.class);
        verify(transport).post(eq("https://sandbox.example/query"), request.capture());
        assertThat(request.getValue().get("vnp_TransactionDate")).isEqualTo("20261030181359");
        assertThat(result.status()).isEqualTo("RECONCILED");
        assertThat(result.responseSignatureVerified()).isTrue();
        assertThat(result.amountVnd()).isEqualTo(50_000L);
    }

    @Test
    void refundResponse00MeansAcceptedButNotSettled() {
        var operation = operation("op-refund", "REFUND", "IN_FLIGHT");
        when(owners.resolve("DEP-BOOKING-1")).thenReturn(Optional.of(owner()));
        when(journal.prepare(eq("op-refund"), anyString(), eq("REFUND"), anyString(), eq("DEP-BOOKING-1")))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq("https://sandbox.example/refund"), anyMap())).thenReturn(signedRefundResponse("00"));
        when(journal.complete(eq("op-refund"), eq("ACCEPTED"), eq("00"), eq("02"), eq("06"), eq("22345679"), eq(50_000L)))
                .thenAnswer(invocation -> completed(operation, "ACCEPTED", "00", "02", "06", "22345679", 50_000L));

        var result = adapter().requestFullRefund(new VnpayRefundCommand("op-refund", "DEP-BOOKING-1",
                "12345678", "20261030181359", 50_000L, "Workshop deposit", "manager-test", "203.0.113.10"));

        ArgumentCaptor<Map<String, String>> request = ArgumentCaptor.forClass(Map.class);
        verify(transport).post(eq("https://sandbox.example/refund"), request.capture());
        assertThat(request.getValue().get("vnp_TransactionType")).isEqualTo("02");
        assertThat(request.getValue().get("vnp_Amount")).isEqualTo("5000000");
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.transactionStatus()).isEqualTo("06");
        assertThat(result.responseSignatureVerified()).isTrue();
    }

    @Test
    void acceptedRefundReplayReturnsRecordedStateWithoutSendingAgain() {
        var accepted = completed(operation("op-refund-replay", "REFUND", "ACCEPTED"),
                "ACCEPTED", "00", "02", "06", "22345679", 50_000L);
        when(owners.resolve("DEP-BOOKING-1")).thenReturn(Optional.of(owner()));
        when(journal.prepare(eq("op-refund-replay"), anyString(), eq("REFUND"), anyString(), eq("DEP-BOOKING-1")))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(accepted, false));

        var result = adapter().requestFullRefund(new VnpayRefundCommand("op-refund-replay", "DEP-BOOKING-1",
                "12345678", "20261030181359", 50_000L, "Workshop deposit", "manager-test", "203.0.113.10"));

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.responseSignatureVerified()).isTrue();
        verifyNoInteractions(transport);
    }

    @Test
    void changedRequestUnderSameOperationReferenceConflictsWithoutSending() {
        when(owners.resolve("DEP-BOOKING-1")).thenReturn(Optional.of(owner()));
        when(journal.prepare(eq("op-refund-conflict"), anyString(), eq("REFUND"), anyString(), eq("DEP-BOOKING-1")))
                .thenThrow(new IllegalStateException("PAYMENT_OPERATION_CONFLICT"));

        var command = new VnpayRefundCommand("op-refund-conflict", "DEP-BOOKING-1", "12345678",
                "20261030181359", 50_000L, "Workshop deposit", "manager-test", "203.0.113.10");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter().requestFullRefund(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PAYMENT_OPERATION_CONFLICT");
        verifyNoInteractions(transport);
    }

    @Test
    void rejectsLoopbackAndPrivateMerchantApiAddressesBeforeAnyProviderOperation() {
        var loopback = new VnpayQueryDrCommand("op-loopback", "DEP-BOOKING-1",
                "20261030181359", "Workshop deposit", "127.0.0.1");
        var privateNetwork = new VnpayQueryDrCommand("op-private", "DEP-BOOKING-1",
                "20261030181359", "Workshop deposit", "192.168.1.20");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter().queryTransaction(loopback))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_VNPAY_MERCHANT_API_IP_ADDRESS");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter().queryTransaction(privateNetwork))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_VNPAY_MERCHANT_API_IP_ADDRESS");
        verifyNoInteractions(owners, journal, transport);
    }

    @Test
    void timeoutBecomesUnknownAndReplayDoesNotSendAgain() {
        var inFlight = operation("op-timeout", "REFUND", "IN_FLIGHT");
        var unknown = operation("op-timeout", "REFUND", "UNKNOWN");
        when(owners.resolve("DEP-BOOKING-1")).thenReturn(Optional.of(owner()));
        when(journal.prepare(eq("op-timeout"), anyString(), eq("REFUND"), anyString(), eq("DEP-BOOKING-1")))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(inFlight, true))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(unknown, false));
        when(transport.post(anyString(), anyMap())).thenThrow(new IllegalStateException("timeout"));
        when(journal.complete(eq("op-timeout"), eq("UNKNOWN"), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenAnswer(invocation -> completed(unknown, "UNKNOWN", null, null, null, null, null));

        var command = new VnpayRefundCommand("op-timeout", "DEP-BOOKING-1", "12345678",
                "20261030181359", 50_000L, "Workshop deposit", "manager-test", "203.0.113.10");
        var first = adapter().requestFullRefund(command);
        var replay = adapter().requestFullRefund(command);

        assertThat(first.status()).isEqualTo("UNKNOWN");
        assertThat(replay.status()).isEqualTo("UNKNOWN");
        verify(transport, times(1)).post(anyString(), anyMap());
    }

    @Test
    void providerCode99BecomesUnknown() {
        var operation = operation("op-99", "QUERYDR", "IN_FLIGHT");
        when(owners.resolve("DEP-BOOKING-1")).thenReturn(Optional.of(owner()));
        when(journal.prepare(eq("op-99"), anyString(), eq("QUERYDR"), anyString(), eq("DEP-BOOKING-1")))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(anyString(), anyMap())).thenReturn(signedQueryResponse("99"));
        when(journal.complete(eq("op-99"), eq("UNKNOWN"), eq("99"), isNull(), isNull(), isNull(), isNull()))
                .thenAnswer(invocation -> completed(operation, "UNKNOWN", "99", null, null, null, null));

        var result = adapter().queryTransaction(new VnpayQueryDrCommand("op-99", "DEP-BOOKING-1",
                "20261030181359", "Workshop deposit", "203.0.113.10"));

        assertThat(result.status()).isEqualTo("UNKNOWN");
        verify(journal).complete("op-99", "UNKNOWN", "99", null, null, null, null);
    }

    @Test
    void invalidProviderResponseSignatureBecomesUnknown() {
        var operation = operation("op-tamper", "QUERYDR", "IN_FLIGHT");
        Map<String, String> tampered = signedQueryResponse();
        tampered.put("vnp_Amount", "123");
        when(owners.resolve("DEP-BOOKING-1")).thenReturn(Optional.of(owner()));
        when(journal.prepare(eq("op-tamper"), anyString(), eq("QUERYDR"), anyString(), eq("DEP-BOOKING-1")))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(anyString(), anyMap())).thenReturn(tampered);
        when(journal.complete(eq("op-tamper"), eq("UNKNOWN"), eq("RESPONSE_UNVERIFIED"), isNull(), isNull(), isNull(), isNull()))
                .thenAnswer(invocation -> completed(operation, "UNKNOWN", "RESPONSE_UNVERIFIED", null, null, null, null));

        var result = adapter().queryTransaction(new VnpayQueryDrCommand("op-tamper", "DEP-BOOKING-1",
                "20261030181359", "Workshop deposit", "203.0.113.10"));

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.responseSignatureVerified()).isFalse();
    }

    private VnpayMerchantOperationsAdapter adapter() {
        var properties = properties();
        return new VnpayMerchantOperationsAdapter(properties, owners, journal,
                new VnpayMerchantApiSigner(properties), transport);
    }

    private static VnpayProperties properties() {
        var p = new VnpayProperties();
        p.setEnabled(true);
        p.setTmnCode("TEST1234");
        p.setHashSecret(SECRET);
        p.setPayUrl("https://sandbox.example/pay");
        p.setReturnUrl("https://merchant.example/return");
        p.setIpnUrl("https://merchant.example/ipn");
        p.setQueryDrUrl("https://sandbox.example/query");
        p.setRefundUrl("https://sandbox.example/refund");
        return p;
    }

    private static PaymentOwnerRegistration owner() {
        return PaymentOwnerRegistration.builder().merchantReference("DEP-BOOKING-1")
                .bookingReference("BK-1").paymentKind(PaymentOwnerKind.DEPOSIT)
                .amountVnd(50_000L).currencyCode("VND").build();
    }

    private static VnpayMerchantOperation operation(String reference, String kind, String status) {
        return VnpayMerchantOperation.builder().operationReference(reference).operationKind(kind)
                .operationStatus(status).providerRequestId("0123456789abcdef0123456789abcdef")
                .merchantReference("DEP-BOOKING-1").requestFingerprint("f".repeat(64)).build();
    }

    private static VnpayMerchantOperation completed(VnpayMerchantOperation operation, String status,
                                                     String code, String type, String transactionStatus,
                                                     String transactionNumber, Long amount) {
        operation.setOperationStatus(status);
        operation.setProviderResponseCode(code);
        operation.setProviderTransactionType(type);
        operation.setProviderTransactionStatus(transactionStatus);
        operation.setProviderTransactionNumber(transactionNumber);
        operation.setAmountVnd(amount);
        return operation;
    }

    private static Map<String, String> signedQueryResponse() { return signedQueryResponse("00"); }

    private static Map<String, String> signedQueryResponse(String code) {
        var response = new LinkedHashMap<String, String>();
        response.put("vnp_ResponseId", "response-1");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", code);
        response.put("vnp_Message", "OK");
        response.put("vnp_TmnCode", "TEST1234");
        response.put("vnp_TxnRef", "DEP-BOOKING-1");
        response.put("vnp_Amount", "5000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261030181540");
        response.put("vnp_TransactionNo", "12345678");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Workshop deposit");
        response.put("vnp_PromotionCode", "");
        response.put("vnp_PromotionAmount", "");
        response.put("vnp_SecureHash", hmac(String.join("|", "response-1", "querydr", code, "OK",
                "TEST1234", "DEP-BOOKING-1", "5000000", "NCB", "20261030181540", "12345678",
                "01", "00", "Workshop deposit", "", "")));
        return response;
    }

    private static Map<String, String> signedRefundResponse(String code) {
        var response = new LinkedHashMap<String, String>();
        response.put("vnp_ResponseId", "response-2");
        response.put("vnp_Command", "refund");
        response.put("vnp_ResponseCode", code);
        response.put("vnp_Message", "Accepted");
        response.put("vnp_TmnCode", "TEST1234");
        response.put("vnp_TxnRef", "DEP-BOOKING-1");
        response.put("vnp_Amount", "5000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261031120100");
        response.put("vnp_TransactionNo", "22345679");
        response.put("vnp_TransactionType", "02");
        response.put("vnp_TransactionStatus", "06");
        response.put("vnp_OrderInfo", "Workshop deposit");
        response.put("vnp_SecureHash", hmac(String.join("|", "response-2", "refund", code, "Accepted",
                "TEST1234", "DEP-BOOKING-1", "5000000", "NCB", "20261031120100", "22345679",
                "02", "06", "Workshop deposit")));
        return response;
    }

    private static String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
