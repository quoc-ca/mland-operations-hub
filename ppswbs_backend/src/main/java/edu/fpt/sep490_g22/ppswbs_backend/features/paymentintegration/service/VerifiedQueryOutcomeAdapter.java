package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.VnpayMerchantOperationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class VerifiedQueryOutcomeAdapter implements VerifiedQueryOutcomePort {
    private final VnpayMerchantOperationRepository operations;
    @Override public Optional<VnpayMerchantOperationResult> findVerifiedQuery(String reference) {
        return operations.findByOperationReference(reference)
                .filter(row -> "QUERYDR".equals(row.getOperationKind()) && "RECONCILED".equals(row.getOperationStatus())
                        && "00".equals(row.getProviderResponseCode()))
                .map(row -> new VnpayMerchantOperationResult(row.getOperationReference(), row.getOperationKind(),
                        row.getOperationStatus(), row.getProviderResponseCode(), row.getProviderTransactionType(),
                        row.getProviderTransactionStatus(), row.getProviderTransactionNumber(), row.getMerchantReference(),
                        row.getAmountVnd(), true));
    }
}
