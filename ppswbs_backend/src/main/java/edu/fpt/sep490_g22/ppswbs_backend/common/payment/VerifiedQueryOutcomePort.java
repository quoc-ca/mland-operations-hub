package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

import java.util.Optional;

/** Read-only access to durable, signature-verified QueryDR results. */
public interface VerifiedQueryOutcomePort {
    Optional<VnpayMerchantOperationResult> findVerifiedQuery(String operationReference);
}
