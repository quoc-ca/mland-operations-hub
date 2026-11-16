package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * T137 (FR-040): the Admin's technical operation after {@code WORKSHOP_REFUND_ACCOUNT_KEY} was replaced. Every request
 * that still holds an encrypted account number goes back to {@code PENDING_ACCOUNT}, one transaction per request, in
 * batches. Not transactional itself: a failure of one request never undoes the others, and a rerun touches only the
 * requests that still hold a ciphertext. No account number is read, returned or logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundAccountKeyReplacementService {

    static final int BATCH_SIZE = 50;

    private final BankRefundService bankRefunds;

    public record Result(int returnedToPendingAccount, int failed) {
    }

    public Result replace(Long adminAccountId, String reason, String correlationReference) {
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("KEY_REPLACEMENT_REASON_REQUIRED");
        }
        List<String> references = bankRefunds.referencesHoldingAccountNumber();
        int returned = 0;
        int failed = 0;
        for (int from = 0; from < references.size(); from += BATCH_SIZE) {
            for (String reference : references.subList(from, Math.min(references.size(), from + BATCH_SIZE))) {
                try {
                    if (bankRefunds.returnForKeyReplacement(reference, adminAccountId, reason.trim(), correlationReference)) {
                        returned++;
                    }
                } catch (RuntimeException failure) {
                    failed++;
                    log.warn("Key replacement could not return refund {}: exceptionType={}", reference,
                            failure.getClass().getSimpleName());
                }
            }
        }
        log.info("Refund account key replacement: returned={} failed={}", returned, failed);
        return new Result(returned, failed);
    }
}
