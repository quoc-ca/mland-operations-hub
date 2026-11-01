package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingCancellationEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCustomerCancellationSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.GuestContactProofDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCustomerCancellationSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Opens one immutable whole-booking cancellation case for an eligible Member or Guest. */
@Service
@Slf4j
public class CustomerBookingCancellationService {
    private static final String POLICY_REVISION = "CUSTOMER_CANCELLATION_V1";

    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementIdempotencyService idempotency;
    private final SettlementCaseRepository cases;
    private final SettlementCustomerCancellationSnapshotRepository snapshots;
    private final SettlementBusinessAuditRepository audits;
    private final TransactionTemplate transaction;

    public CustomerBookingCancellationService(WorkshopBookingSettlementAdapter booking,
            WorkshopOperationsSettlementAdapter operations,
            SettlementIdempotencyService idempotency,
            SettlementCaseRepository cases,
            SettlementCustomerCancellationSnapshotRepository snapshots,
            SettlementBusinessAuditRepository audits,
            PlatformTransactionManager transactionManager) {
        this.booking = booking;
        this.operations = operations;
        this.idempotency = idempotency;
        this.cases = cases;
        this.snapshots = snapshots;
        this.audits = audits;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public SettlementCaseProjectionDto open(String bookingCode, String idempotencyKey,
            String correlationReference, GuestContactProofDto guestProof, CurrentAccountActor actor) {
        String bookingReference = required(bookingCode, "bookingCode");
        String key = required(idempotencyKey, "Idempotency-Key");
        var financials = booking.confirmedProjection(bookingReference);
        if (financials == null) throw new WorkshopSettlementNotFoundException();
        authorizeCustomerAccess(bookingReference, guestProof, actor);

        Instant requestedAt = Instant.now();
        String correlation = correlationReference == null || correlationReference.isBlank()
                ? "CORR-" + UUID.randomUUID() : correlationReference.trim();
        String operationReference = "CUSTOMER-CANCEL-" + digest(bookingReference + "|" + key).substring(0, 48);
        String fingerprint = digest(bookingReference + "|" + accessFingerprint(guestProof, actor));
        final String actorReference = actor == null ? null : String.valueOf(actor.accountId());

        SettlementCaseProjectionDto opened;
        try {
            opened = transaction.execute(status -> {
                var result = idempotency.execute(operationReference, fingerprint, () -> {
                    if (snapshots.findByBookingReference(bookingReference).isPresent()) {
                        throw new WorkshopSettlementConflictException("CANCELLATION_CASE_ALREADY_EXISTS");
                    }
                    return createSnapshotAndCase(bookingReference, correlation, actorReference, requestedAt);
                });
                SettlementCase settlementCase = cases.findByCaseReference(result.outcomeReference())
                        .orElseThrow(() -> new WorkshopSettlementConflictException("CANCELLATION_CASE_OUTCOME_MISSING"));
                return SettlementCaseProjectionDto.from(settlementCase);
            });
        } catch (DataIntegrityViolationException duplicateBookingClaim) {
            throw new WorkshopSettlementConflictException("CANCELLATION_CASE_ALREADY_EXISTS");
        }

        if ("OPEN".equals(opened.status())) {
            String caseReference = opened.caseReference();
            var completion = operations.cancelBooking(bookingReference, caseReference,
                    operationReference + "-OPERATIONS", correlation);
            if (completion.status() == edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus.ACCEPTED) {
                var bookingCompletion = booking.cancelConfirmedBooking(bookingReference, caseReference,
                        operationReference + "-BOOKING", correlation);
                if (bookingCompletion == null || bookingCompletion.status() != edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus.ACCEPTED) {
                    return updateCaseAfterOperations(caseReference, "REJECTED", "BOOKING_CANCELLATION_REJECTED",
                            "CUSTOMER_CANCELLATION_BOOKING_STATE_REJECTED", actorReference, correlation);
                }
                return updateCaseAfterOperations(caseReference, "RESOLVED", "DIRECT_DEPOSIT_FORFEITED",
                        "CUSTOMER_CANCELLATION_RESOLVED_NO_REFUND", actorReference, correlation);
            }
            if (completion.status() == edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus.REJECTED) {
                return updateCaseAfterOperations(caseReference, "REJECTED", "OPERATIONS_CANCELLATION_REJECTED",
                        "CUSTOMER_CANCELLATION_REJECTED_BY_OPERATIONS", actorReference, correlation);
            }
        }
        return opened;
    }

    public SettlementCaseProjectionDto read(String bookingCode, String caseReference,
            GuestContactProofDto guestProof, CurrentAccountActor actor) {
        String bookingReference = required(bookingCode, "bookingCode");
        String ref = required(caseReference, "caseReference");
        if (actor == null) {
            authorizeCustomerAccess(bookingReference, guestProof, null);
        } else if (actor.role() == ActorRole.MEMBER && actor.status() == ActorStatus.ACTIVE) {
            authorizeCustomerAccess(bookingReference, guestProof, actor);
        } else {
            throw new WorkshopSettlementNotFoundException();
        }
        return readCase(bookingReference, ref);
    }

    private SettlementCaseProjectionDto readCase(String bookingReference, String caseReference) {
        return cases.findByCaseReference(caseReference)
                .filter(settlementCase -> bookingReference.equals(settlementCase.getBookingReference()))
                .filter(settlementCase -> "CUSTOMER_CANCELLATION".equals(settlementCase.getCaseType()))
                .map(SettlementCaseProjectionDto::from)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    private String createSnapshotAndCase(String bookingReference, String correlation, String actorReference,
            Instant requestedAt) {
        BookingCancellationEligibilitySnapshot eligibility = operations.cancellationEligibility(bookingReference);
        var paymentProjection = booking.verifiedDepositPayments(bookingReference);
        List<VerifiedBookingPayment> payments = paymentProjection == null ? List.of()
                : paymentProjection.payments().stream().filter(VerifiedBookingPayment::verified)
                .sorted(Comparator.comparing(VerifiedBookingPayment::originalProviderCreateDate,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(VerifiedBookingPayment::reference, Comparator.nullsLast(String::compareTo)))
                .toList();

        boolean paymentFactsValid = !payments.isEmpty() && payments.stream().allMatch(payment ->
                payment.reference() != null && !payment.reference().isBlank()
                        && payment.providerTransactionNumber() != null
                        && payment.providerTransactionNumber().matches("[0-9]{1,15}")
                        && payment.originalProviderCreateDate() != null
                        && payment.originalProviderCreateDate().matches("[0-9]{14}")
                        && payment.amountVnd() > 0 && "VND".equals(payment.currencyCode()));
        long paidAmount = 0;
        if (paymentFactsValid) {
            for (VerifiedBookingPayment payment : payments) paidAmount = Math.addExact(paidAmount, payment.amountVnd());
        }

        String earliestSessionReference = eligibility == null ? null : eligibility.earliestRequiredSessionReference();
        Instant earliestSessionStart = eligibility == null ? null : eligibility.earliestRequiredSessionStart();
        // Direct Mland deposits are non-refundable.  A customer cancellation must
        // never create a VNPay refund/QueryDR operation; OTA refunds are handled by
        // the OTA owner outside this direct-booking flow.
        String rejectionReason;
        if (eligibility == null) rejectionReason = "CANCELLATION_ELIGIBILITY_UNAVAILABLE";
        else if (!eligibility.eligible()) rejectionReason = safeReason(eligibility.reason(), "CANCELLATION_INELIGIBLE");
        else if (earliestSessionStart == null || !earliestSessionStart.isAfter(requestedAt)) rejectionReason = "SESSION_ALREADY_STARTED";
        else if (!paymentFactsValid) rejectionReason = "VERIFIED_DEPOSIT_EVIDENCE_UNAVAILABLE";
        else rejectionReason = null;
        boolean eligible = rejectionReason == null;
        String caseStatus = eligible ? "OPEN" : "REJECTED";
        String reason = eligible ? "DIRECT_DEPOSIT_FORFEITED" : rejectionReason;

        String caseReference = "SETTLE-CANCEL-" + UUID.randomUUID();
        SettlementCase settlementCase = cases.saveAndFlush(SettlementCase.builder()
                .caseReference(caseReference).bookingReference(bookingReference).caseType("CUSTOMER_CANCELLATION")
                .lifecycleStatus(caseStatus)
                .reasonReference(reason).correlationReference(correlation)
                .createdAt(requestedAt).updatedAt(requestedAt).build());

        snapshots.saveAndFlush(SettlementCustomerCancellationSnapshot.builder()
                .snapshotReference("CANCEL-SNAPSHOT-" + UUID.randomUUID())
                .caseReference(caseReference).bookingReference(bookingReference)
                .policyRevision(POLICY_REVISION).refundPercent((short) 0)
                .earliestSessionReference(earliestSessionReference).earliestSessionStart(earliestSessionStart)
                .paidDepositAmountVnd(paidAmount).refundAmountVnd(0L)
                .currencyCode("VND").eligibilityOutcome(eligible ? "NO_REFUND" : "REJECTED")
                .rejectionReason(eligible ? null : rejectionReason).requestedAt(requestedAt).build());

        audits.save(SettlementBusinessAudit.builder().auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_CASE").aggregateReference(caseReference)
                .actionCode(eligible ? "CUSTOMER_CANCELLATION_OPENED_NO_REFUND" : "CUSTOMER_CANCELLATION_REJECTED")
                .actorReference(actorReference).correlationReference(correlation)
                .reasonReference(reason)
                .createdAt(requestedAt).build());
        return settlementCase.getCaseReference();
    }

    private SettlementCaseProjectionDto updateCaseAfterOperations(String caseReference, String status,
            String reason, String auditAction, String actorReference, String correlationReference) {
        return transaction.execute(statusContext -> {
            SettlementCase settlementCase = cases.findByCaseReference(caseReference)
                    .orElseThrow(() -> new WorkshopSettlementConflictException("CANCELLATION_CASE_OUTCOME_MISSING"));
            settlementCase.setLifecycleStatus(status);
            settlementCase.setReasonReference(reason);
            cases.saveAndFlush(settlementCase);
            audits.save(SettlementBusinessAudit.builder().auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                    .aggregateType("SETTLEMENT_CASE").aggregateReference(caseReference)
                    .actionCode(auditAction).actorReference(actorReference)
                    .correlationReference(correlationReference).reasonReference(reason)
                    .createdAt(Instant.now()).build());
            return SettlementCaseProjectionDto.from(settlementCase);
        });
    }

    private void authorizeCustomerAccess(String bookingReference, GuestContactProofDto guestProof,
                                         CurrentAccountActor actor) {
        if (actor != null) {
            if (actor.role() != ActorRole.MEMBER || actor.status() != ActorStatus.ACTIVE || actor.accountId() == null)
                throw new WorkshopSettlementNotFoundException();
            var access = booking.verifyCustomerAccess(bookingReference, actor.accountId(), null, null);
            if (access == null || !access.authorized()) throw new WorkshopSettlementNotFoundException();
            return;
        }
        String email = guestProof == null ? null : trimToNull(guestProof.contactEmail());
        String phone = guestProof == null ? null : trimToNull(guestProof.contactPhone());
        if ((email == null) == (phone == null)) throw new WorkshopSettlementValidationException("EXACTLY_ONE_GUEST_CONTACT_PROOF_REQUIRED");
        var access = booking.verifyCustomerAccess(bookingReference, null, email, phone);
        if (access == null || !access.authorized()) throw new WorkshopSettlementNotFoundException();
    }

    private static String accessFingerprint(GuestContactProofDto proof, CurrentAccountActor actor) {
        if (actor != null) return "MEMBER:" + actor.accountId();
        return "GUEST:" + digest(trimToNull(proof == null ? null : proof.contactEmail()) + "|"
                + trimToNull(proof == null ? null : proof.contactPhone()));
    }

    private static String safeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.replaceAll("[^A-Z0-9_]+", "_");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
        return value.trim();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
