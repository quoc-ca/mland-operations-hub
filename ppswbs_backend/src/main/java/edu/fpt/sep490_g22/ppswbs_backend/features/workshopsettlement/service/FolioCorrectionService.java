package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCorrectionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolioCorrectionService {

    private final SettlementFolioCorrectionRepository corrections;
    private final SettlementCaseRepository cases;
    private final SettlementRefundAllocationRepository allocations;
    private final SettlementFinalBillRepository bills;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementBusinessAuditRepository audits;
    private final SettlementIdempotencyService idempotency;
    private final WorkshopSettlementAuthorizationService authorization;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopRefundService refunds;
    private final PlatformTransactionManager transactionManager;

    /**
     * Opens the correction in one transaction, then dispatches the VNPay refund after that transaction has
     * committed and ended. The dispatch must not run from an afterCommit callback: the finished transaction is
     * still bound to the thread there, so the refund bookkeeping (row-locking queries) fails with
     * "no transaction in progress". If the dispatch fails, the PREPARED allocations are picked up by the
     * refund recovery scheduler, so the already committed correction is never reported as failed.
     */
    public FolioCorrectionDtos.Response create(String bookingReference, String idempotencyKey,
                                               String correlationReference,
                                               FolioCorrectionDtos.CreateRequest request,
                                               CurrentAccountActor actor) {
        Opened opened = new TransactionTemplate(transactionManager).execute(status ->
                openInTransaction(bookingReference, idempotencyKey, correlationReference, request, actor));
        if (opened.dispatchRefund()) {
            try {
                refunds.dispatchCase(opened.caseReference());
            } catch (RuntimeException failure) {
                log.warn("Folio correction refund dispatch deferred to recovery; caseReference={} ({})",
                        opened.caseReference(), failure.getClass().getSimpleName());
            }
        }
        return opened.response();
    }

    private Opened openInTransaction(String bookingReference, String idempotencyKey,
                                               String correlationReference,
                                               FolioCorrectionDtos.CreateRequest request,
                                               CurrentAccountActor actor) {
        String bookingRef = required(bookingReference, "bookingReference");
        String key = required(idempotencyKey, "Idempotency-Key");
        if (request == null) throw new WorkshopSettlementValidationException("request is required");
        Long amountVnd = request.amountVnd();
        if (amountVnd == null || amountVnd <= 0) {
            throw new WorkshopSettlementValidationException("amountVnd must be positive");
        }
        String reason = required(request.reason(), "reason");

        CurrentAccountActor manager = authorization.requireManager(actor);

        if (bookingRef.startsWith("OTA-")) {
            throw new WorkshopSettlementConflictException("OTA_BOOKING_NOT_SUPPORTED");
        }

        // Lock the folio row FIRST: concurrent corrections (even with different Idempotency-Keys) must serialize,
        // otherwise both could allocate the same unrefunded collections and refund them twice. It must be the first
        // database access: under MySQL REPEATABLE READ the first plain read fixes the transaction snapshot, so a
        // correction that read anything before waiting for the lock would not see the winner's allocations.
        SettlementFinalBill bill = bills.findByBookingReferenceForUpdate(bookingRef)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        var projection = booking.confirmedProjection(bookingRef);
        if (projection == null) throw new WorkshopSettlementNotFoundException();
        if (!"FINALIZED".equals(bill.getLifecycleStatus())) {
            if ("OPEN".equals(bill.getLifecycleStatus())) {
                throw new WorkshopSettlementConflictException("FOLIO_NOT_FINALIZED");
            }
            throw new WorkshopSettlementConflictException("FOLIO_CLOSED");
        }

        String correlation = correlationReference == null || correlationReference.isBlank()
                ? "CORR-" + UUID.randomUUID() : correlationReference.trim();
        String operationReference = "FOLIO-CORRECTION-" + digest(bookingRef + "|" + key.trim()).substring(0, 48);
        String fingerprint = digest(String.join("|", bookingRef, String.valueOf(manager.accountId()),
                String.valueOf(amountVnd), reason.trim()));

        SettlementIdempotencyResult idempotencyResult = idempotency.execute(operationReference, fingerprint, () -> {
            List<SettlementCase> pending = cases.findByBookingReferenceAndCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
                    bookingRef, List.of("FOLIO_CORRECTION"), SettlementCaseLifecycleStatus.PENDING_PROVIDER.name());
            if (!pending.isEmpty()) {
                throw new WorkshopSettlementConflictException("FOLIO_CORRECTION_ALREADY_IN_PROGRESS");
            }

            List<RefundablePayment> payments = verifiedPayments(bookingRef);
            Map<String, Long> unrefundedByPayment = new LinkedHashMap<>();
            long totalUnrefunded = 0L;
            for (RefundablePayment p : payments) {
                long allocated = allocations.findAllByOriginalPaymentReference(p.paymentReference()).stream()
                        // Everything except a provider-REJECTED request counts as possibly refunded: an UNKNOWN
                        // request may have moved money, so its amount stays reserved until QueryDR settles it.
                        .filter(a -> !"REJECTED".equals(a.getLifecycleStatus()))
                        .mapToLong(SettlementRefundAllocation::getAllocatedAmountVnd)
                        .sum();
                long unrefunded = Math.max(0L, p.amountVnd() - allocated);
                unrefundedByPayment.put(p.paymentReference(), unrefunded);
                totalUnrefunded += unrefunded;
            }

            if (amountVnd > totalUnrefunded) {
                throw new WorkshopSettlementValidationException("OVERCHARGE_EXCEEDS_UNREFUNDED_COLLECTIONS");
            }

            Instant now = Instant.now();
            String caseReference = "SETTLE-CORR-CASE-" + UUID.randomUUID();
            String correctionReference = "CORR-" + UUID.randomUUID();

            long remaining = amountVnd;
            List<SettlementRefundAllocation> planned = new ArrayList<>();
            for (RefundablePayment p : payments) {
                if (remaining == 0) break;
                long available = unrefundedByPayment.getOrDefault(p.paymentReference(), 0L);
                if (available <= 0) continue;
                long alloc = Math.min(available, remaining);

                planned.add(SettlementRefundAllocation.builder()
                        .allocationReference("REFUND-ALLOCATION-" + UUID.randomUUID())
                        .caseReference(caseReference)
                        .originalPaymentReference(p.paymentReference())
                        .originalProviderTransactionNumber(p.providerTransactionNumber())
                        .originalProviderCreateDate(p.providerCreateDate())
                        .originalAmountVnd(p.amountVnd())
                        .allocatedAmountVnd(alloc)
                        .currencyCode("VND")
                        .operationReference("VNPAY-REFUND-" + UUID.randomUUID())
                        .lifecycleStatus("PREPARED")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                remaining -= alloc;
            }

            if (remaining != 0) {
                throw new WorkshopSettlementConflictException("REFUND_ALLOCATION_TOTAL_MISMATCH");
            }

            cases.saveAndFlush(SettlementCase.builder()
                    .caseReference(caseReference)
                    .bookingReference(bookingRef)
                    .caseType("FOLIO_CORRECTION")
                    .lifecycleStatus(SettlementCaseLifecycleStatus.PENDING_PROVIDER.name())
                    .reasonReference(reason.trim())
                    .correlationReference(correlation)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());

            corrections.saveAndFlush(SettlementFolioCorrection.builder()
                    .correctionReference(correctionReference)
                    .caseReference(caseReference)
                    .bookingReference(bookingRef)
                    .billReference(bill.getBillReference())
                    .amountVnd(amountVnd)
                    .currencyCode("VND")
                    .reason(reason.trim())
                    .managerActorReference(String.valueOf(manager.accountId()))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());

            // The case row must exist before its allocations (foreign key settlement_refund_allocations.case_reference).
            planned.forEach(allocations::save);

            audits.save(SettlementBusinessAudit.builder()
                    .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                    .aggregateType("SETTLEMENT_FOLIO_CORRECTION")
                    .aggregateReference(correctionReference)
                    .actionCode("FOLIO_CORRECTION_OPENED")
                    .actorReference(String.valueOf(manager.accountId()))
                    .correlationReference(correlation)
                    .reasonReference(reason.trim())
                    .evidenceReference(caseReference)
                    .createdAt(now)
                    .build());

            return correctionReference;
        });

        SettlementFolioCorrection correction = corrections.findByCorrectionReference(idempotencyResult.outcomeReference())
                .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));
        SettlementCase settlementCase = cases.findByCaseReference(correction.getCaseReference())
                .orElseThrow(() -> new WorkshopSettlementConflictException("IDEMPOTENCY_OUTCOME_INCOMPLETE"));

        boolean dispatch = SettlementCaseLifecycleStatus.PENDING_PROVIDER.name().equals(settlementCase.getLifecycleStatus());
        return new Opened(FolioCorrectionDtos.Response.from(correction, settlementCase), settlementCase.getCaseReference(), dispatch);
    }

    @Transactional(readOnly = true)
    public FolioCorrectionDtos.Response read(String bookingReference, String correctionReference,
                                             String contactEmail, String contactPhone,
                                             CurrentAccountActor actor) {
        String bookingRef = required(bookingReference, "bookingReference");
        String corrRef = required(correctionReference, "correctionReference");

        var projection = booking.confirmedProjection(bookingRef);
        if (projection == null) throw new WorkshopSettlementNotFoundException();

        if (actor == null) {
            if ((contactEmail == null || contactEmail.isBlank()) == (contactPhone == null || contactPhone.isBlank())) {
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

        SettlementFolioCorrection correction = corrections
                .findByBookingReferenceAndCorrectionReference(bookingRef, corrRef)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementCase settlementCase = cases.findByCaseReference(correction.getCaseReference())
                .orElseThrow(WorkshopSettlementNotFoundException::new);

        return FolioCorrectionDtos.Response.from(correction, settlementCase);
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

    private record Opened(FolioCorrectionDtos.Response response, String caseReference, boolean dispatchRefund) {}

    private record RefundablePayment(String paymentReference, String providerTransactionNumber,
                                     String providerCreateDate, Long amountVnd, String currencyCode) {}
}

