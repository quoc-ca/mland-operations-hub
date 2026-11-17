package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.VnpayMerchantOperationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.VnpayMerchantOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class VnpayMerchantOperationJournal {
    private final VnpayMerchantOperationRepository repository;

    @Transactional
    public Prepared prepare(String operationReference, String fingerprint, String kind,
                            String requestId, String merchantReference) {
        Optional<VnpayMerchantOperation> existing = repository.findByOperationReference(operationReference);
        if (existing.isPresent()) return existing(existing.get(), fingerprint, kind);
        VnpayMerchantOperation newOperation = VnpayMerchantOperation.builder()
                .operationReference(operationReference)
                .requestFingerprint(fingerprint)
                .operationKind(kind)
                .providerRequestId(requestId)
                .merchantReference(merchantReference)
                .operationStatus("IN_FLIGHT")
                .build();
        return new Prepared(repository.saveAndFlush(newOperation), true);
    }

    @Transactional(readOnly = true)
    public Optional<VnpayMerchantOperation> findByOperationReference(String operationReference) {
        return repository.findByOperationReference(operationReference);
    }

    @Transactional
    public VnpayMerchantOperation complete(String operationReference, String status,
                                           String responseCode, String transactionType,
                                           String transactionStatus, String transactionNumber,
                                           Long amountVnd) {
        VnpayMerchantOperation operation = repository.findByOperationReference(operationReference)
                .orElseThrow(() -> new IllegalStateException("PAYMENT_OPERATION_NOT_FOUND"));
        operation.setOperationStatus(status);
        operation.setProviderResponseCode(responseCode);
        operation.setProviderTransactionType(transactionType);
        operation.setProviderTransactionStatus(transactionStatus);
        operation.setProviderTransactionNumber(transactionNumber);
        operation.setAmountVnd(amountVnd);
        operation.setUpdatedAt(Instant.now());
        return repository.save(operation);
    }

    private Prepared existing(VnpayMerchantOperation row, String fingerprint, String kind) {
        if (!row.getRequestFingerprint().equals(fingerprint) || !row.getOperationKind().equals(kind))
            throw new IllegalStateException("PAYMENT_OPERATION_CONFLICT");
        return new Prepared(row, false);
    }

    public record Prepared(VnpayMerchantOperation operation, boolean shouldSend) { }
}
