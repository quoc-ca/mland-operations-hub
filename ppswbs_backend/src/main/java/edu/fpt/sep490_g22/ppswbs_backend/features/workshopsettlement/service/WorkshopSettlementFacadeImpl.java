package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementHandoffIdempotencyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.HandoffIdempotencyStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCancellationCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementHandoffIdempotency;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.CancellationHandoffOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.OpenCancellationHandoffCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkshopSettlementFacadeImpl implements WorkshopSettlementFacade {
    private final SettlementCancellationCaseRepository caseRepository;
    private final SettlementHandoffIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final SettlementEvidenceConclusionRepository evidenceRepository;
    private final SettlementFinalBillRepository billRepository;
    private final SettlementChargeRepository chargeRepository;

    /**
     * Payment evidence is accepted only after the bill/evidence consumer exists.
     * Keeping this boundary fail-closed prevents a paymentintegration caller from
     * accidentally changing settlement truth while the bill foundation is being built.
     */
    @Override
    @Transactional
    public SettlementPaymentEvidenceOutcome applyVerifiedSettlementPaymentEvidence(
            ApplyVerifiedSettlementPaymentEvidenceCommand command) {
        if (command == null) throw new WorkshopSettlementValidationException("command is required");
        var byOperation = evidenceRepository.findByOperationReference(command.operationReference().trim());
        if (byOperation.isPresent()) return replay(byOperation.get(), command);
        var byEvidence = evidenceRepository.findByEvidenceReference(command.verifiedEvidenceReference().trim());
        if (byEvidence.isPresent()) return replay(byEvidence.get(), command);

        SettlementFinalBill bill = billRepository.findByBookingReferenceForUpdate(command.bookingReference().trim()).orElse(null);
        if (bill == null) return SettlementPaymentEvidenceOutcome.rejected("FINAL_BILL_NOT_FOUND");
        if (!"VND".equals(command.currencyCode()) || !bill.getCurrencyCode().equals(command.currencyCode())) {
            return SettlementPaymentEvidenceOutcome.rejected("CURRENCY_MISMATCH");
        }
        if (command.amountVnd() == null || command.amountVnd() <= 0) {
            return SettlementPaymentEvidenceOutcome.rejected("INVALID_AMOUNT");
        }

        String conclusion;
        if ("FINAL_PAYMENT".equals(command.paymentKind())) {
            if (!"OPEN".equals(bill.getLifecycleStatus())) return SettlementPaymentEvidenceOutcome.rejected("BILL_NOT_OPEN");
            long confirmedCharges = chargeRepository.findByBillReferenceAndLifecycleStatusOrderByIdAsc(
                    bill.getBillReference(), "CONFIRMED").stream().mapToLong(c -> c.getAmountVnd() == null ? 0L : c.getAmountVnd()).sum();
            long priorFinalPayments = total(command.bookingReference(), "FINAL_PAYMENT_VERIFIED");
            long outstanding;
            try { outstanding = Math.subtractExact(Math.addExact(bill.getRemainingAmountVnd(), confirmedCharges), priorFinalPayments); }
            catch (ArithmeticException invalidAmount) { return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH"); }
            if (outstanding <= 0 || command.amountVnd() > outstanding) return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
            conclusion = "FINAL_PAYMENT_VERIFIED";
        } else {
            long collected;
            try {
                collected = Math.subtractExact(Math.addExact(bill.getAcceptedDepositAmountVnd(),
                        total(command.bookingReference(), "FINAL_PAYMENT_VERIFIED")), total(command.bookingReference(), "REFUND_VERIFIED"));
            } catch (ArithmeticException invalidAmount) { return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH"); }
            if (command.amountVnd() > collected) return SettlementPaymentEvidenceOutcome.rejected("AMOUNT_MISMATCH");
            conclusion = "REFUND_VERIFIED";
        }

        evidenceRepository.saveAndFlush(SettlementEvidenceConclusion.builder()
                .evidenceReference(command.verifiedEvidenceReference().trim())
                .bookingReference(command.bookingReference().trim())
                .providerTransactionReference(command.merchantReference().trim())
                .conclusion(conclusion)
                .amountVnd(command.amountVnd())
                .currencyCode(command.currencyCode())
                .operationReference(command.operationReference().trim())
                .createdAt(Instant.now()).build());
        return SettlementPaymentEvidenceOutcome.accepted();
    }

    private long total(String bookingReference, String conclusion) {
        return evidenceRepository.findByBookingReferenceAndConclusion(bookingReference, conclusion).stream()
                .mapToLong(e -> e.getAmountVnd() == null ? 0L : e.getAmountVnd()).sum();
    }

    private SettlementPaymentEvidenceOutcome replay(SettlementEvidenceConclusion existing,
            ApplyVerifiedSettlementPaymentEvidenceCommand command) {
        boolean same = existing.getBookingReference().equals(command.bookingReference())
                && existing.getEvidenceReference().equals(command.verifiedEvidenceReference())
                && existing.getProviderTransactionReference().equals(command.merchantReference())
                && existing.getAmountVnd().equals(command.amountVnd())
                && existing.getCurrencyCode().equals(command.currencyCode())
                && existing.getConclusion().equals(command.paymentKind() + "_VERIFIED");
        return same ? SettlementPaymentEvidenceOutcome.accepted()
                : SettlementPaymentEvidenceOutcome.rejected("IDEMPOTENCY_CONFLICT");
    }

    @Override
    @Transactional
    public CancellationHandoffOutcome openCancellationHandoff(OpenCancellationHandoffCommand command) {
        validate(command);
        String fingerprint = fingerprint(command);
        String operationReference = command.operationReference().trim();

        var existing = idempotencyRepository.findByOperationReference(operationReference);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        SettlementHandoffIdempotency claim = SettlementHandoffIdempotency.builder()
                .operationReference(operationReference)
                .requestFingerprint(fingerprint)
                .correlationReference(command.correlationReference().trim())
                .lifecycleStatus(HandoffIdempotencyStatus.IN_PROGRESS)
                .build();
        try {
            idempotencyRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException duplicate) {
            throw new WorkshopSettlementConflictException("Concurrent operation reference claim; retry with the same command");
        }

        String caseReference = "SETTLE-CASE-" + UUID.randomUUID();
        SettlementCancellationCase cancellationCase = SettlementCancellationCase.builder()
                .caseReference(caseReference)
                .sessionReference(command.sessionReference().trim())
                .affectedBookingReferencesJson(serialize(command.affectedBookingReferences()))
                .cancellationReasonReference(command.cancellationReasonReference().trim())
                .operationReference(operationReference)
                .correlationReference(command.correlationReference().trim())
                .build();
        caseRepository.save(cancellationCase);

        claim.setCancellationCaseReference(caseReference);
        claim.setLifecycleStatus(HandoffIdempotencyStatus.COMPLETED);
        claim.setCompletedAt(Instant.now());
        idempotencyRepository.save(claim);
        return CancellationHandoffOutcome.accepted(caseReference, command.correlationReference().trim());
    }

    private CancellationHandoffOutcome replay(SettlementHandoffIdempotency existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            return CancellationHandoffOutcome.rejected("IDEMPOTENCY_CONFLICT", existing.getCorrelationReference());
        }
        if (existing.getLifecycleStatus() == HandoffIdempotencyStatus.IN_PROGRESS) {
            return CancellationHandoffOutcome.rejected("IN_PROGRESS", existing.getCorrelationReference());
        }
        if (existing.getCancellationCaseReference() != null) {
            return CancellationHandoffOutcome.accepted(existing.getCancellationCaseReference(), existing.getCorrelationReference());
        }
        return CancellationHandoffOutcome.rejected(existing.getRejectionReason(), existing.getCorrelationReference());
    }

    private void validate(OpenCancellationHandoffCommand command) {
        if (command == null) throw new WorkshopSettlementValidationException("command is required");
        require(command.sessionReference(), "sessionReference");
        require(command.cancellationReasonReference(), "cancellationReasonReference");
        require(command.operationReference(), "operationReference");
        require(command.correlationReference(), "correlationReference");
        List<String> references = command.affectedBookingReferences() == null
                ? List.of()
                : command.affectedBookingReferences();
        if (references.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new WorkshopSettlementValidationException("affectedBookingReferences must contain only non-blank references");
        }
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
    }

    private String serialize(List<String> values) {
        try { return objectMapper.writeValueAsString(new ArrayList<>(new LinkedHashSet<>(values))); }
        catch (JsonProcessingException e) { throw new WorkshopSettlementValidationException("affected booking references are not serializable"); }
    }

    private String fingerprint(OpenCancellationHandoffCommand command) {
        String canonical = String.join("|", command.sessionReference().trim(),
                String.join(",", new LinkedHashSet<>(command.affectedBookingReferences())),
                command.cancellationReasonReference().trim(), command.operationReference().trim(),
                command.correlationReference().trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
