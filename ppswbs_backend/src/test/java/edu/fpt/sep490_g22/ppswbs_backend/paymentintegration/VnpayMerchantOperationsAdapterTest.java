package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
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
        // provider_response_code is CHAR(2): a non-provider reason such as RESPONSE_UNVERIFIED is not persisted there.
        verify(journal).complete("query-tampered", "UNKNOWN", null, null, null, null, null);
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
