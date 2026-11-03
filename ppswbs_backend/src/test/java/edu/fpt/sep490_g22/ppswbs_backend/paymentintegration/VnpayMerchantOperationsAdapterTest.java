package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundReconciliationCommand;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.VnpayMerchantOperation;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayMerchantApiSigner;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayMerchantApiTransport;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayMerchantOperationJournal;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayMerchantOperationsAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VnpayMerchantOperationsAdapterTest {
    private static final String SECRET = "merchant-api-test-secret";
    private static final String MERCHANT_REFERENCE = "DEP-BOOKING-1";
    private static final String QUERY_URL = "https://sandbox.example.test/querydr";
    private static final String REFUND_URL = "https://sandbox.example.test/refund";

    private PaymentOwnerRegistry owners;
    private VnpayMerchantOperationJournal journal;
    private VnpayMerchantApiTransport transport;
    private VnpayMerchantOperationsAdapter adapter;

    @BeforeEach
    void setUp() {
        owners = mock(PaymentOwnerRegistry.class);
        journal = mock(VnpayMerchantOperationJournal.class);
        transport = mock(VnpayMerchantApiTransport.class);
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TEST1234");
        properties.setHashSecret(SECRET);
        properties.setPayUrl("https://sandbox.example.test/pay");
        properties.setReturnUrl("https://app.example.test/return");
        properties.setIpnUrl("https://app.example.test/ipn");
        properties.setQueryDrUrl(QUERY_URL);
        properties.setRefundUrl(REFUND_URL);
        adapter = new VnpayMerchantOperationsAdapter(properties, owners, journal,
                new VnpayMerchantApiSigner(properties), transport);
        when(owners.resolve(MERCHANT_REFERENCE)).thenReturn(Optional.of(PaymentOwnerRegistration.builder()
                .merchantReference(MERCHANT_REFERENCE).bookingReference("BOOKING-1")
                .paymentKind(PaymentOwnerKind.DEPOSIT).amountVnd(50_000L).currencyCode("VND")
                .providerCreateDate("20261030181359")
                .lifecycleStatus("OPEN").build()));
    }

    @Test
    void queryDrPreparesJournalBeforeSendingSignedRequestAndMapsVerifiedOutcome() {
        VnpayMerchantOperation operation = operation("query-1", "QUERYDR", "provider-request-1");
        when(journal.prepare(eq("query-1"), anyString(), eq("QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(QUERY_URL), anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Map<String, String> request = invocation.getArgument(1);
            assertThat(request.get("vnp_SecureHash")).isNotBlank();
            assertThat(request.get("vnp_TxnRef")).isEqualTo(MERCHANT_REFERENCE);
            assertThat(request.get("vnp_TransactionDate")).isEqualTo("20261030181359");
            assertThat(request.get("vnp_RequestId")).isEqualTo("provider-request-1");
            return queryResponse("00", "5000000");
        });
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.queryTransaction(query("query-1"));

        assertThat(result.status()).isEqualTo("RECONCILED");
        assertThat(result.responseSignatureVerified()).isTrue();
        assertThat(result.amountVnd()).isEqualTo(50_000L);
        InOrder order = inOrder(journal, transport);
        order.verify(journal).prepare(eq("query-1"), anyString(), eq("QUERYDR"), anyString(), eq(MERCHANT_REFERENCE));
        order.verify(transport).post(eq(QUERY_URL), anyMap());
        verify(journal).complete("query-1", "RECONCILED", "00", "01", "00", "12345678", 50_000L);
    }

    @Test
    void tamperedQueryDrResponseIsRetainedAsUnknown() {
        VnpayMerchantOperation operation = operation("query-tampered", "QUERYDR", "provider-request-tampered");
        when(journal.prepare(eq("query-tampered"), anyString(), eq("QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(QUERY_URL), anyMap())).thenAnswer(invocation -> {
            Map<String, String> response = queryResponse("00", "5000000");
            response.put("vnp_Amount", "5000001");
            return response;
        });
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.queryTransaction(query("query-tampered"));

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.responseSignatureVerified()).isFalse();
        verify(journal).complete("query-tampered", "UNKNOWN", "RESPONSE_UNVERIFIED", null, null, null, null);
    }

    @Test
    void providerCode99IsUnknownAndTransportIsNeverRetried() {
        VnpayMerchantOperation operation = operation("query-99", "QUERYDR", "provider-request-99");
        when(journal.prepare(eq("query-99"), anyString(), eq("QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(QUERY_URL), anyMap())).thenReturn(queryResponse("99", "5000000"));
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.queryTransaction(query("query-99"));

        assertThat(result.status()).isEqualTo("UNKNOWN");
        verify(transport, times(1)).post(eq(QUERY_URL), anyMap());
        verify(journal).complete("query-99", "UNKNOWN", "99", null, null, null, null);
    }

    @Test
    void identicalReplayReturnsRecordedStateWithoutSecondProviderRequestAndChangedReuseConflicts() {
        VnpayMerchantOperation operation = operation("query-replay", "QUERYDR", "provider-request-stable");
        operation.setOperationStatus("RECONCILED");
        operation.setProviderResponseCode("00");
        operation.setProviderTransactionType("01");
        operation.setProviderTransactionStatus("00");
        operation.setProviderTransactionNumber("12345678");
        operation.setAmountVnd(50_000L);
        when(journal.prepare(eq("query-replay"), anyString(), eq("QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, false));
        when(transport.post(eq(QUERY_URL), anyMap())).thenReturn(queryResponse("00", "5000000"));
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult first = adapter.queryTransaction(query("query-replay"));
        VnpayMerchantOperationResult replay = adapter.queryTransaction(query("query-replay"));

        assertThat(first).isEqualTo(replay);
        verify(transport, times(1)).post(eq(QUERY_URL), anyMap());
        verify(journal, times(2)).prepare(eq("query-replay"), anyString(), eq("QUERYDR"), anyString(), eq(MERCHANT_REFERENCE));

        when(journal.prepare(eq("query-replay"), anyString(), eq("QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenThrow(new IllegalStateException("PAYMENT_OPERATION_CONFLICT"));
        assertThatThrownBy(() -> adapter.queryTransaction(new VnpayQueryDrCommand(
                "query-replay", MERCHANT_REFERENCE, "20261030181359", "changed order info", "8.8.8.8")))
                .isInstanceOf(IllegalStateException.class).hasMessage("PAYMENT_OPERATION_CONFLICT");
        verifyNoMoreInteractions(transport);
    }

    @Test
    void acceptedRefundRemainsAcceptedRatherThanSettled() {
        VnpayMerchantOperation operation = operation("refund-1", "REFUND", "provider-refund-1");
        when(journal.prepare(eq("refund-1"), anyString(), eq("REFUND"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(REFUND_URL), anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Map<String, String> request = invocation.getArgument(1);
            assertThat(request.get("vnp_Amount")).isEqualTo("5000000");
            assertThat(request.get("vnp_TransactionDate")).isEqualTo("20261030181359");
            return refundResponse();
        });
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.requestFullRefund(new VnpayRefundCommand(
                "refund-1", MERCHANT_REFERENCE, "12345678", "20261030181359", 50_000L,
                "Workshop deposit", "manager-test", "8.8.8.8"));

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.responseSignatureVerified()).isTrue();
        assertThat(result.status()).isNotEqualTo("SETTLED");
        verify(journal).complete("refund-1", "ACCEPTED", "00", "02", "06", "12345679", 50_000L);
    }

    @Test
    void partialRefundUsesVnpayPartialTransactionTypeAndMatchesRequestedAmount() {
        VnpayMerchantOperation operation = operation("refund-partial", "REFUND", "provider-refund-partial");
        when(journal.prepare(eq("refund-partial"), anyString(), eq("REFUND"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(REFUND_URL), anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Map<String, String> request = invocation.getArgument(1);
            assertThat(request.get("vnp_TransactionType")).isEqualTo("03");
            assertThat(request.get("vnp_Amount")).isEqualTo("2500000");
            assertThat(request.get("vnp_TransactionDate")).isEqualTo("20261030181359");
            return refundResponse("2500000", "03");
        });
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.requestRefund(new VnpayRefundCommand(
                "refund-partial", MERCHANT_REFERENCE, "12345678", "20261030181359", 25_000L,
                "Workshop deposit partial", "manager-test", "8.8.8.8"));

        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.amountVnd()).isEqualTo(25_000L);
        verify(journal).complete("refund-partial", "ACCEPTED", "00", "03", "06", "12345679", 25_000L);
    }

    @Test
    void refundQueryVerifiesOnlySignedMatchingTerminalRefundTransaction() {
        VnpayMerchantOperation operation = operation("refund-query-verified", "REFUND_QUERYDR", "refund-query-provider-id");
        when(journal.prepare(eq("refund-query-verified"), anyString(), eq("REFUND_QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(QUERY_URL), anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Map<String, String> request = invocation.getArgument(1);
            assertThat(request.get("vnp_TransactionNo")).isEqualTo("12345679");
            assertThat(request.get("vnp_TransactionDate")).isEqualTo("20261030181359");
            Map<String, String> response = refundQueryResponse("2500000", "03", "00", "12345679");
            return response;
        });
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.queryRefund(refundQuery("refund-query-verified"));

        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.responseSignatureVerified()).isTrue();
        assertThat(result.providerTransactionNumber()).isEqualTo("12345679");
        assertThat(result.amountVnd()).isEqualTo(25_000L);
        verify(journal).complete("refund-query-verified", "VERIFIED", "00", "03", "00", "12345679", 25_000L);
    }

    @Test
    void refundQueryKeepsProviderStatuses05And06PendingAnd09Rejected() {
        for (String transactionStatus : List.of("05", "06", "09")) {
            String operationReference = "refund-query-status-" + transactionStatus;
            VnpayMerchantOperation operation = operation(operationReference, "REFUND_QUERYDR", "provider-" + transactionStatus);
            when(journal.prepare(eq(operationReference), anyString(), eq("REFUND_QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                    .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
            when(transport.post(eq(QUERY_URL), anyMap()))
                    .thenReturn(refundQueryResponse("2500000", "03", transactionStatus, "12345679"));
            completeAsJournalMutation(operation);

            VnpayMerchantOperationResult result = adapter.queryRefund(refundQuery(operationReference));

            assertThat(result.status()).isEqualTo("09".equals(transactionStatus) ? "REJECTED" : "PENDING");
        }
    }

    @Test
    void refundQueryMismatchRemainsUnknown() {
        VnpayMerchantOperation operation = operation("refund-query-mismatch", "REFUND_QUERYDR", "refund-query-mismatch-id");
        when(journal.prepare(eq("refund-query-mismatch"), anyString(), eq("REFUND_QUERYDR"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(QUERY_URL), anyMap()))
                .thenReturn(refundQueryResponse("2500001", "03", "00", "wrong-provider-transaction"));
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.queryRefund(refundQuery("refund-query-mismatch"));

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.responseSignatureVerified()).isTrue();
        verify(journal).complete("refund-query-mismatch", "UNKNOWN", "00", "03", "00",
                "wrong-provider-transaction", null);
    }

    @Test
    void partialRefundCannotExceedOriginalPayment() {
        assertThatThrownBy(() -> adapter.requestRefund(new VnpayRefundCommand(
                "refund-too-large", MERCHANT_REFERENCE, "12345678", "20261030181359", 50_001L,
                "Workshop deposit", "manager-test", "8.8.8.8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REFUND_AMOUNT_MUST_BE_WITHIN_ORIGINAL_PAYMENT");
        verifyNoInteractions(transport, journal);
    }

    @Test
    void refundRejectsOriginalPaymentDateThatDiffersFromImmutableOwnerRegistration() {
        assertThatThrownBy(() -> adapter.requestRefund(new VnpayRefundCommand(
                "refund-wrong-date", MERCHANT_REFERENCE, "12345678", "20261030181358", 25_000L,
                "Workshop deposit", "manager-test", "8.8.8.8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ORIGINAL_PAYMENT_DATE_MISMATCH");
        verifyNoInteractions(transport, journal);
    }

    @Test
    void refundRejectsLegacyOwnerWithoutImmutableOriginalPaymentDate() {
        when(owners.resolve(MERCHANT_REFERENCE)).thenReturn(Optional.of(PaymentOwnerRegistration.builder()
                .merchantReference(MERCHANT_REFERENCE).bookingReference("BOOKING-1")
                .paymentKind(PaymentOwnerKind.DEPOSIT).amountVnd(50_000L).currencyCode("VND")
                .lifecycleStatus("OPEN").build()));

        assertThatThrownBy(() -> adapter.requestRefund(new VnpayRefundCommand(
                "refund-legacy-date", MERCHANT_REFERENCE, "12345678", "20261030181359", 25_000L,
                "Workshop deposit", "manager-test", "8.8.8.8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ORIGINAL_PAYMENT_DATE_MISSING");
        verifyNoInteractions(transport, journal);
    }

    @Test
    void refundRejectsNonNumericProviderTransactionNumberBeforeDispatch() {
        assertThatThrownBy(() -> adapter.requestRefund(new VnpayRefundCommand(
                "refund-invalid-transaction", MERCHANT_REFERENCE, "TX-123", "20261030181359", 25_000L,
                "Workshop deposit", "manager-test", "8.8.8.8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_PROVIDER_TRANSACTION_NUMBER");
        verifyNoInteractions(transport, journal);
    }

    @Test
    void transportFailureIsUnknownAndDoesNotRetry() {
        VnpayMerchantOperation operation = operation("refund-timeout", "REFUND", "provider-refund-timeout");
        when(journal.prepare(eq("refund-timeout"), anyString(), eq("REFUND"), anyString(), eq(MERCHANT_REFERENCE)))
                .thenReturn(new VnpayMerchantOperationJournal.Prepared(operation, true));
        when(transport.post(eq(REFUND_URL), anyMap())).thenThrow(new IllegalStateException("simulated timeout"));
        completeAsJournalMutation(operation);

        VnpayMerchantOperationResult result = adapter.requestFullRefund(new VnpayRefundCommand(
                "refund-timeout", MERCHANT_REFERENCE, "12345678", "20261030181359", 50_000L,
                "Workshop deposit", "manager-test", "8.8.8.8"));

        assertThat(result.status()).isEqualTo("UNKNOWN");
        verify(transport, times(1)).post(eq(REFUND_URL), anyMap());
        verify(journal).complete("refund-timeout", "UNKNOWN", null, null, null, null, null);
    }

    private void completeAsJournalMutation(VnpayMerchantOperation operation) {
        when(journal.complete(anyString(), anyString(), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(Long.class))).thenAnswer(invocation -> {
            operation.setOperationStatus(invocation.getArgument(1));
            operation.setProviderResponseCode(invocation.getArgument(2));
            operation.setProviderTransactionType(invocation.getArgument(3));
            operation.setProviderTransactionStatus(invocation.getArgument(4));
            operation.setProviderTransactionNumber(invocation.getArgument(5));
            operation.setAmountVnd(invocation.getArgument(6));
            return operation;
        });
    }

    private static VnpayQueryDrCommand query(String operationReference) {
        return new VnpayQueryDrCommand(operationReference, MERCHANT_REFERENCE,
                "20261030181359", "Workshop deposit", "8.8.8.8");
    }

    private static VnpayRefundReconciliationCommand refundQuery(String operationReference) {
        return new VnpayRefundReconciliationCommand(operationReference, MERCHANT_REFERENCE,
                "12345679", "20261030181359", 25_000L, "Workshop deposit refund", "8.8.8.8");
    }

    private static VnpayMerchantOperation operation(String reference, String kind, String providerRequestId) {
        return VnpayMerchantOperation.builder().operationReference(reference).requestFingerprint("f".repeat(64))
                .operationKind(kind).providerRequestId(providerRequestId).merchantReference(MERCHANT_REFERENCE)
                .operationStatus("IN_FLIGHT").build();
    }

    private static Map<String, String> queryResponse(String responseCode, String amount) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("vnp_ResponseId", "response-query-1");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", responseCode);
        response.put("vnp_Message", "Provider response");
        response.put("vnp_TmnCode", "TEST1234");
        response.put("vnp_TxnRef", MERCHANT_REFERENCE);
        response.put("vnp_Amount", amount);
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261030181540");
        response.put("vnp_TransactionNo", "12345678");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Workshop deposit");
        response.put("vnp_PromotionCode", "");
        response.put("vnp_PromotionAmount", "");
        response.put("vnp_SecureHash", responseHash(response, List.of("vnp_ResponseId", "vnp_Command",
                "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount",
                "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
                "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount")));
        return response;
    }

    private static Map<String, String> refundResponse() {
        return refundResponse("5000000", "02");
    }

    private static Map<String, String> refundResponse(String amount, String transactionType) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("vnp_ResponseId", "response-refund-1");
        response.put("vnp_Command", "refund");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Accepted");
        response.put("vnp_TmnCode", "TEST1234");
        response.put("vnp_TxnRef", MERCHANT_REFERENCE);
        response.put("vnp_Amount", amount);
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261031120100");
        response.put("vnp_TransactionNo", "12345679");
        response.put("vnp_TransactionType", transactionType);
        response.put("vnp_TransactionStatus", "06");
        response.put("vnp_OrderInfo", "Workshop deposit");
        response.put("vnp_SecureHash", responseHash(response, List.of("vnp_ResponseId", "vnp_Command",
                "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount",
                "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
                "vnp_TransactionStatus", "vnp_OrderInfo")));
        return response;
    }

    private static Map<String, String> refundQueryResponse(String amount, String transactionType,
                                                            String transactionStatus, String transactionNumber) {
        Map<String, String> response = queryResponse("00", amount);
        response.put("vnp_TransactionNo", transactionNumber);
        response.put("vnp_TransactionType", transactionType);
        response.put("vnp_TransactionStatus", transactionStatus);
        response.put("vnp_SecureHash", responseHash(response, List.of("vnp_ResponseId", "vnp_Command",
                "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount",
                "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
                "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount")));
        return response;
    }

    private static String responseHash(Map<String, String> fields, List<String> order) {
        try {
            String canonical = String.join("|", order.stream().map(field -> fields.getOrDefault(field, "")).toList());
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
