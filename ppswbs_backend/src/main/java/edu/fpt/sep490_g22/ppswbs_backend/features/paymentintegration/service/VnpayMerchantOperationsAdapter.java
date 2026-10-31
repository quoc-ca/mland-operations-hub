package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.VnpayMerchantOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class VnpayMerchantOperationsAdapter implements VnpayMerchantOperationsPort {
    private static final DateTimeFormatter PROVIDER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId PROVIDER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VnpayProperties properties;
    private final PaymentOwnerRegistry owners;
    private final VnpayMerchantOperationJournal journal;
    private final VnpayMerchantApiSigner signer;
    private final VnpayMerchantApiTransport transport;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VnpayMerchantOperationResult queryTransaction(VnpayQueryDrCommand command) {
        validateCommon(command.operationReference(), command.merchantReference(), command.originalTransactionDate(), command.orderInfo(), command.ipAddress());
        PaymentOwnerRegistration owner = requireVndOwner(command.merchantReference());
        String requestFingerprint = fingerprint("QUERYDR", command.operationReference(), command.merchantReference(),
                command.originalTransactionDate(), command.orderInfo(), command.ipAddress());
        PreparedCall call = prepare(command.operationReference(), requestFingerprint, "QUERYDR", command.merchantReference());
        if (!call.shouldSend()) return result(call.operation(), "QUERYDR", "IN_FLIGHT".equals(call.operation().getOperationStatus()) ? "UNKNOWN" : null);

        Map<String, String> request = queryRequest(command, call.operation().getProviderRequestId());
        try {
            Map<String, String> response = transport.post(properties.getQueryDrUrl(), signer.signQueryDr(request));
            if (!signer.verifyQueryDrResponse(response) || !matches(response, command.merchantReference(), owner, true))
                return completeUnknown(command.operationReference(), "QUERYDR", "RESPONSE_UNVERIFIED");
            String responseCode = response.get("vnp_ResponseCode");
            if ("99".equals(responseCode) || "94".equals(responseCode))
                return completeUnknown(command.operationReference(), "QUERYDR", responseCode);
            String status = ("00".equals(responseCode) || "91".equals(responseCode)) ? "RECONCILED" : "REJECTED";
            Long amountVnd = minorToVnd(response.get("vnp_Amount"));
            VnpayMerchantOperation saved = journal.complete(command.operationReference(), status, responseCode,
                    response.get("vnp_TransactionType"), response.get("vnp_TransactionStatus"),
                    response.get("vnp_TransactionNo"), amountVnd);
            return result(saved, "QUERYDR", null, true);
        } catch (RuntimeException transportOrParseFailure) {
            return completeUnknown(command.operationReference(), "QUERYDR", null);
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VnpayMerchantOperationResult requestFullRefund(VnpayRefundCommand command) {
        validateCommon(command.operationReference(), command.merchantReference(), command.originalTransactionDate(), command.orderInfo(), command.ipAddress());
        required(command.createdBy(), "createdBy");
        PaymentOwnerRegistration owner = requireVndOwner(command.merchantReference());
        if (command.amountVnd() <= 0 || command.amountVnd() != owner.getAmountVnd())
            throw new IllegalArgumentException("REFUND_MUST_MATCH_ORIGINAL_PAYMENT_AMOUNT");
        if (command.providerTransactionNumber() != null && command.providerTransactionNumber().length() > 15)
            throw new IllegalArgumentException("INVALID_PROVIDER_TRANSACTION_NUMBER");

        String requestFingerprint = fingerprint("REFUND", command.operationReference(), command.merchantReference(),
                command.providerTransactionNumber(), command.originalTransactionDate(), command.amountVnd(),
                command.orderInfo(), command.createdBy(), command.ipAddress());
        PreparedCall call = prepare(command.operationReference(), requestFingerprint, "REFUND", command.merchantReference());
        if (!call.shouldSend()) return result(call.operation(), "REFUND", "IN_FLIGHT".equals(call.operation().getOperationStatus()) ? "UNKNOWN" : null);

        Map<String, String> request = refundRequest(command, call.operation().getProviderRequestId());
        try {
            Map<String, String> response = transport.post(properties.getRefundUrl(), signer.signRefund(request));
            if (!signer.verifyRefundResponse(response) || !matches(response, command.merchantReference(), owner, false))
                return completeUnknown(command.operationReference(), "REFUND", "RESPONSE_UNVERIFIED");
            String responseCode = response.get("vnp_ResponseCode");
            if ("99".equals(responseCode) || "94".equals(responseCode))
                return completeUnknown(command.operationReference(), "REFUND", responseCode);
            String status = "00".equals(responseCode) ? "ACCEPTED" : "REJECTED";
            Long amountVnd = minorToVnd(response.get("vnp_Amount"));
            VnpayMerchantOperation saved = journal.complete(command.operationReference(), status, responseCode,
                    response.get("vnp_TransactionType"), response.get("vnp_TransactionStatus"),
                    response.get("vnp_TransactionNo"), amountVnd);
            return result(saved, "REFUND", null, true);
        } catch (RuntimeException transportOrParseFailure) {
            return completeUnknown(command.operationReference(), "REFUND", null);
        }
    }

    private PreparedCall prepare(String operationReference, String fingerprint, String kind, String merchantReference) {
        properties.validateForEnabledRuntime();
        try {
            VnpayMerchantOperationJournal.Prepared prepared = journal.prepare(operationReference, fingerprint, kind,
                    UUID.randomUUID().toString().replace("-", ""), merchantReference);
            return new PreparedCall(prepared.operation(), prepared.shouldSend());
        } catch (DataIntegrityViolationException concurrentInsert) {
            VnpayMerchantOperation existing = journal.findByOperationReference(operationReference)
                    .orElseThrow(() -> concurrentInsert);
            if (!existing.getRequestFingerprint().equals(fingerprint) || !existing.getOperationKind().equals(kind))
                throw new IllegalStateException("PAYMENT_OPERATION_CONFLICT");
            return new PreparedCall(existing, false);
        }
    }

    private VnpayMerchantOperationResult completeUnknown(String operationReference, String kind, String code) {
        VnpayMerchantOperation saved = journal.complete(operationReference, "UNKNOWN", code, null, null, null, null);
        return result(saved, kind, null);
    }

    private Map<String, String> queryRequest(VnpayQueryDrCommand command, String requestId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", requestId);
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_TxnRef", command.merchantReference());
        fields.put("vnp_OrderInfo", command.orderInfo());
        fields.put("vnp_TransactionDate", command.originalTransactionDate());
        fields.put("vnp_CreateDate", PROVIDER_DATE.withZone(PROVIDER_ZONE).format(java.time.Instant.now()));
        fields.put("vnp_IpAddr", command.ipAddress());
        return fields;
    }

    private Map<String, String> refundRequest(VnpayRefundCommand command, String requestId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", requestId);
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "refund");
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_TransactionType", "02");
        fields.put("vnp_TxnRef", command.merchantReference());
        fields.put("vnp_Amount", String.valueOf(Math.multiplyExact(command.amountVnd(), 100L)));
        fields.put("vnp_TransactionNo", command.providerTransactionNumber() == null ? "" : command.providerTransactionNumber());
        fields.put("vnp_TransactionDate", command.originalTransactionDate());
        fields.put("vnp_CreateBy", command.createdBy());
        fields.put("vnp_CreateDate", PROVIDER_DATE.withZone(PROVIDER_ZONE).format(java.time.Instant.now()));
        fields.put("vnp_IpAddr", command.ipAddress());
        fields.put("vnp_OrderInfo", command.orderInfo());
        return fields;
    }

    private PaymentOwnerRegistration requireVndOwner(String merchantReference) {
        PaymentOwnerRegistration owner = owners.resolve(merchantReference)
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_PAYMENT_REFERENCE"));
        if (!"VND".equals(owner.getCurrencyCode())) throw new IllegalArgumentException("UNSUPPORTED_PAYMENT_CURRENCY");
        return owner;
    }

    private boolean matches(Map<String, String> response, String merchantReference,
                            PaymentOwnerRegistration owner, boolean requireAmount) {
        if (!properties.getTmnCode().equals(response.get("vnp_TmnCode")) ||
                !merchantReference.equals(response.get("vnp_TxnRef"))) return false;
        String rawAmount = response.get("vnp_Amount");
        if (rawAmount == null) return "91".equals(response.get("vnp_ResponseCode"));
        Long amountVnd = minorToVnd(rawAmount);
        return amountVnd != null && ("91".equals(response.get("vnp_ResponseCode")) || amountVnd.equals(owner.getAmountVnd()));
    }

    private static Long minorToVnd(String raw) {
        try {
            long minor = Long.parseLong(raw);
            return minor > 0 && minor % 100 == 0 ? minor / 100 : null;
        } catch (RuntimeException invalid) { return null; }
    }

    private static void validateCommon(String operationReference, String merchantReference,
                                       String transactionDate, String orderInfo, String ipAddress) {
        required(operationReference, "operationReference");
        required(merchantReference, "merchantReference");
        required(orderInfo, "orderInfo");
        required(ipAddress, "ipAddress");
        validateMerchantApiIpAddress(ipAddress);
        if (operationReference.length() > 128 || merchantReference.length() > 128)
            throw new IllegalArgumentException("INVALID_PAYMENT_REFERENCE_LENGTH");
        if (transactionDate == null || !transactionDate.matches("\\d{14}"))
            throw new IllegalArgumentException("INVALID_ORIGINAL_TRANSACTION_DATE");
    }

    private static void validateMerchantApiIpAddress(String value) {
        if (!value.matches("[0-9.]+|[0-9a-fA-F:]+"))
            throw new IllegalArgumentException("INVALID_VNPAY_MERCHANT_API_IP_ADDRESS");
        try {
            InetAddress address = InetAddress.getByName(value);
            byte[] bytes = address.getAddress();
            boolean uniqueLocalIpv6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocalIpv6)
                throw new IllegalArgumentException("INVALID_VNPAY_MERCHANT_API_IP_ADDRESS");
        } catch (java.net.UnknownHostException invalid) {
            throw new IllegalArgumentException("INVALID_VNPAY_MERCHANT_API_IP_ADDRESS", invalid);
        }
    }

    private static void required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("MISSING_" + field.toUpperCase());
    }

    private static String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = java.util.Arrays.stream(values).map(String::valueOf)
                    .map(value -> value.length() + ":" + value)
                    .collect(java.util.stream.Collectors.joining());
            return java.util.HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private VnpayMerchantOperationResult result(VnpayMerchantOperation operation, String kind, String statusOverride) {
        String status = statusOverride == null ? operation.getOperationStatus() : statusOverride;
        boolean verified = "ACCEPTED".equals(status) || "REJECTED".equals(status) || "RECONCILED".equals(status);
        return result(operation, kind, statusOverride, verified);
    }

    private VnpayMerchantOperationResult result(VnpayMerchantOperation operation, String kind,
                                                String statusOverride, boolean verified) {
        return new VnpayMerchantOperationResult(operation.getOperationReference(), kind,
                statusOverride == null ? operation.getOperationStatus() : statusOverride,
                operation.getProviderResponseCode(), operation.getProviderTransactionType(),
                operation.getProviderTransactionStatus(), operation.getProviderTransactionNumber(),
                operation.getMerchantReference(), operation.getAmountVnd(), verified);
    }

    private record PreparedCall(VnpayMerchantOperation operation, boolean shouldSend) { }
}
