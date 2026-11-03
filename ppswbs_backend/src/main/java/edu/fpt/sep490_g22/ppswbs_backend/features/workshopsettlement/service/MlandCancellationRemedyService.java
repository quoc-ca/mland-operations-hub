package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.MlandRemedyCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MlandCancellationRemedyService {
    private static final String CASE_TYPE = "MLAND_CANCELLATION_REMEDY";
    private static final String PENDING_PROVIDER = "PENDING_PROVIDER";
    private final SettlementCaseRepository cases;
    private final SettlementCancellationCaseRepository cancellationCases;
    private final SettlementCustomerCancellationSnapshotRepository customerCancellationSnapshots;
    private final SettlementBookingMlandRefundPolicySnapshotRepository bookingPolicySnapshots;
    private final SettlementMlandRemedySnapshotRepository remedySnapshots;
    private final SettlementRefundAllocationRepository allocations;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementBusinessAuditRepository audits;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementIdempotencyService idempotency;
    private final WorkshopRefundService refunds;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SettlementCaseProjectionDto read(String bookingReference, String caseReference,
            String contactEmail, String contactPhone, CurrentAccountActor actor) {
        String bookingRef = required(bookingReference, "bookingReference");
        String caseRef = required(caseReference, "caseReference");
        var projection = booking.confirmedProjection(bookingRef);
        if (projection == null) throw new WorkshopSettlementNotFoundException();

        if (actor == null) {
            if ((contactEmail == null || contactEmail.isBlank())
                    == (contactPhone == null || contactPhone.isBlank())) {
                throw new WorkshopSettlementNotFoundException();
            }
            var access = booking.verifyCustomerAccess(bookingRef, null, contactEmail, contactPhone);
            if (access == null || !access.authorized()) throw new WorkshopSettlementNotFoundException();
        } else if (actor.role() == ActorRole.MEMBER) {
            var access = booking.verifyCustomerAccess(bookingRef, actor.accountId(), null, null);
            if (access == null || !access.authorized()) throw new WorkshopSettlementNotFoundException();
        } else if (actor.role() == ActorRole.MANAGER) {
            authorization.requireManager(actor);
        } else if (actor.role() == ActorRole.STAFF) {
            authorization.requireManagerOrStaff(actor, projection.bookingLocationReference());
        } else {
            throw new WorkshopSettlementNotFoundException();
        }

        SettlementCase remedyCase = cases.findByCaseReference(caseRef)
                .filter(candidate -> bookingRef.equals(candidate.getBookingReference()))
                .filter(candidate -> CASE_TYPE.equals(candidate.getCaseType()))
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        return SettlementCaseProjectionDto.from(remedyCase);
    }

    @Transactional
    public SettlementCaseProjectionDto open(String bookingReference, String idempotencyKey,
            String correlationReference, MlandRemedyCommandDto command, CurrentAccountActor actor) {
        String bookingRef = required(bookingReference, "bookingReference");
        String key = required(idempotencyKey, "Idempotency-Key");
        if (command == null) throw new WorkshopSettlementValidationException("command is required");
        String reason = required(command.reason(), "reason");
        String replacementOutcome = required(command.replacementOutcome(), "replacementOutcome");
        String handoffReference = required(command.cancellationHandoffReference(), "cancellationHandoffReference");
        if (!List.of("UNAVAILABLE", "DECLINED").contains(replacementOutcome))
            throw new WorkshopSettlementValidationException("replacementOutcome must be UNAVAILABLE or DECLINED");
        CurrentAccountActor manager = authorization.requireManager(actor);

        var financialProjection = booking.confirmedProjection(bookingRef);
        if (financialProjection == null) throw new WorkshopSettlementNotFoundException();
        String correlation = correlationReference == null || correlationReference.isBlank()
                ? "CORR-" + UUID.randomUUID() : correlationReference.trim();
        String operationReference = "MLAND-REMEDY-" + digest(bookingRef + "|" + key).substring(0, 48);
        String fingerprint = digest(String.join("|", bookingRef, String.valueOf(manager.accountId()),
                replacementOutcome, handoffReference, reason.trim()));

        SettlementIdempotencyResult result = idempotency.execute(operationReference, fingerprint,
                () -> create(bookingRef, correlation, manager, reason.trim(), replacementOutcome, handoffReference)
                        .getCaseReference());
        SettlementCase remedyCase = cases.findByCaseReference(result.outcomeReference())
                .filter(value -> bookingRef.equals(value.getBookingReference()) && CASE_TYPE.equals(value.getCaseType()))
                .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
        if (PENDING_PROVIDER.equals(remedyCase.getLifecycleStatus())) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { refunds.dispatchCase(remedyCase.getCaseReference()); }
            });
        }
        return SettlementCaseProjectionDto.from(remedyCase);
    }

    private SettlementCase create(String bookingReference, String correlation, CurrentAccountActor manager,
            String reason, String replacementOutcome, String handoffReference) {
        SettlementCancellationCase handoff = cancellationCases.findByCaseReference(handoffReference)
                .orElseThrow(() -> new WorkshopSettlementConflictException("CANCELLATION_HANDOFF_NOT_FOUND"));
        if (!affectedBookingsContain(handoff, bookingReference))
            throw new WorkshopSettlementConflictException("CANCELLATION_HANDOFF_BOOKING_MISMATCH");
        SettlementBookingMlandRefundPolicySnapshot policy = bookingPolicySnapshots
                .findByBookingReferenceForUpdate(bookingReference)
                .orElseThrow(() -> new WorkshopSettlementConflictException("BOOKING_REFUND_POLICY_SNAPSHOT_NOT_READY"));
        if (customerCancellationSnapshots.findByBookingReference(bookingReference).isPresent())
            throw new WorkshopSettlementConflictException("BOOKING_ALREADY_HAS_CANCELLATION_CASE");
        if (remedySnapshots.findByBookingReference(bookingReference).isPresent())
            throw new WorkshopSettlementConflictException("MLAND_REMEDY_ALREADY_EXISTS");
        List<RefundablePayment> payments = verifiedPayments(bookingReference);
        long collectedAmount = payments.stream().mapToLong(RefundablePayment::amountVnd).reduce(0L, Math::addExact);
        long refundAmount = BigInteger.valueOf(collectedAmount)
                .multiply(BigInteger.valueOf(policy.getRefundPercent()))
                .divide(BigInteger.valueOf(100)).longValueExact();

        Instant now = Instant.now();
        String caseReference = "SETTLE-MLAND-" + UUID.randomUUID();
        boolean refundRequired = refundAmount > 0;
        SettlementCase remedyCase = cases.saveAndFlush(SettlementCase.builder()
                .caseReference(caseReference).bookingReference(bookingReference).caseType(CASE_TYPE)
                .lifecycleStatus(refundRequired ? PENDING_PROVIDER : "RESOLVED")
                .reasonReference(null).correlationReference(correlation)
                .createdAt(now).updatedAt(now).build());
        remedySnapshots.saveAndFlush(SettlementMlandRemedySnapshot.builder()
                .snapshotReference("MLAND-REMEDY-SNAPSHOT-" + UUID.randomUUID())
                .caseReference(caseReference).bookingReference(bookingReference)
                .policyReference(policy.getPolicyReference()).cancellationHandoffReference(handoffReference)
                .replacementOutcome(replacementOutcome).refundPercent(policy.getRefundPercent())
                .confirmedAt(policy.getConfirmedAt()).collectedAmountVnd(collectedAmount)
                .refundableAmountVnd(refundAmount).currencyCode("VND").capturedAt(now).build());
        if (refundRequired) allocate(caseReference, payments, refundAmount, now);
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID()).aggregateType("SETTLEMENT_CASE")
                .aggregateReference(caseReference).actionCode(refundRequired
                        ? "MLAND_CANCELLATION_REMEDY_OPENED" : "MLAND_CANCELLATION_NO_REFUND_DUE")
                .actorReference(String.valueOf(manager.accountId())).correlationReference(correlation)
                .reasonReference(reason)
                .evidenceReference(handoffReference).createdAt(now).build());
        return remedyCase;
    }

    private List<RefundablePayment> verifiedPayments(String bookingReference) {
        var deposits = booking.verifiedDepositPayments(bookingReference);
        if (deposits == null || deposits.payments() == null) {
            throw new WorkshopSettlementConflictException("VERIFIED_PAYMENT_EVIDENCE_UNAVAILABLE");
        }
        List<RefundablePayment> payments = new ArrayList<>();
        for (VerifiedBookingPayment payment : deposits.payments()) {
            if (!payment.verified()) continue;
            payments.add(new RefundablePayment(payment.reference(), payment.providerTransactionNumber(),
                    payment.originalProviderCreateDate(), payment.amountVnd(), payment.currencyCode()));
        }
        for (SettlementEvidenceConclusion payment : evidence.findByBookingReferenceAndConclusion(
                bookingReference, "FINAL_PAYMENT_VERIFIED")) {
            payments.add(new RefundablePayment(payment.getProviderTransactionReference(),
                    payment.getProviderTransactionNumber(), payment.getOriginalProviderCreateDate(),
                    payment.getAmountVnd(), payment.getCurrencyCode()));
        }
        payments.sort(Comparator.comparing(RefundablePayment::providerCreateDate)
                .thenComparing(RefundablePayment::paymentReference));
        boolean valid = !payments.isEmpty() && payments.stream().allMatch(payment ->
                payment.paymentReference() != null && !payment.paymentReference().isBlank()
                        && payment.providerTransactionNumber() != null
                        && payment.providerTransactionNumber().matches("[0-9]{1,15}")
                        && payment.providerCreateDate() != null && payment.providerCreateDate().matches("[0-9]{14}")
                        && payment.amountVnd() != null && payment.amountVnd() > 0
                        && "VND".equals(payment.currencyCode()));
        if (!valid) throw new WorkshopSettlementConflictException("VERIFIED_PAYMENT_EVIDENCE_INVALID");
        return List.copyOf(payments);
    }

    /** The approved allocation policy is aggregate rounding followed by oldest-payment-first allocation. */
    private void allocate(String caseReference, List<RefundablePayment> payments, long refundAmount, Instant now) {
        long remaining = refundAmount;
        for (RefundablePayment payment : payments) {
            if (remaining == 0) break;
            long amount = Math.min(payment.amountVnd(), remaining);
            allocations.save(SettlementRefundAllocation.builder()
                    .allocationReference("REFUND-ALLOCATION-" + UUID.randomUUID())
                    .caseReference(caseReference).originalPaymentReference(payment.paymentReference())
                    .originalProviderTransactionNumber(payment.providerTransactionNumber())
                    .originalProviderCreateDate(payment.providerCreateDate())
                    .originalAmountVnd(payment.amountVnd()).allocatedAmountVnd(amount).currencyCode("VND")
                    .operationReference("VNPAY-REFUND-" + UUID.randomUUID())
                    .lifecycleStatus("PREPARED").createdAt(now).updatedAt(now).build());
            remaining -= amount;
        }
        if (remaining != 0) throw new WorkshopSettlementConflictException("REFUND_ALLOCATION_TOTAL_MISMATCH");
    }

    private boolean affectedBookingsContain(SettlementCancellationCase handoff, String bookingReference) {
        try {
            List<String> affected = objectMapper.readValue(handoff.getAffectedBookingReferencesJson(),
                    new TypeReference<>() { });
            return affected != null && affected.contains(bookingReference);
        } catch (Exception invalidEvidence) {
            throw new WorkshopSettlementConflictException("CANCELLATION_HANDOFF_EVIDENCE_INVALID");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
        return value.trim();
    }

    private static String digest(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record RefundablePayment(String paymentReference, String providerTransactionNumber,
                                     String providerCreateDate, Long amountVnd, String currencyCode) { }
}
