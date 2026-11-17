package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
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

    private final SettlementRefundRequestRepository refundRequests;
    private final SettlementFolioCollectionRepository collections;

    // One constructor only. Two unannotated constructors left Spring with no candidate, so it looked for a no-arg
    // one and failed the whole application context (NoSuchMethodException: <init>()); every dependency is required.
    public FolioCorrectionService(
            SettlementFolioCorrectionRepository corrections,
            SettlementCaseRepository cases,
            SettlementRefundAllocationRepository allocations,
            SettlementFinalBillRepository bills,
            SettlementEvidenceConclusionRepository evidence,
            SettlementBusinessAuditRepository audits,
            SettlementIdempotencyService idempotency,
            WorkshopSettlementAuthorizationService authorization,
            WorkshopBookingSettlementAdapter booking,
            WorkshopRefundService refunds,
            PlatformTransactionManager transactionManager,
            SettlementRefundRequestRepository refundRequests,
            SettlementFolioCollectionRepository collections) {
        this.corrections = corrections;
        this.cases = cases;
        this.allocations = allocations;
        this.bills = bills;
        this.evidence = evidence;
        this.audits = audits;
        this.idempotency = idempotency;
        this.authorization = authorization;
        this.booking = booking;
        this.refunds = refunds;
        this.transactionManager = transactionManager;
        this.refundRequests = refundRequests;
        this.collections = collections;
    }

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
        var ticketCtx = booking.getTicketFolioContext(bookingRef).orElse(null);
        if (ticketCtx == null) throw new WorkshopSettlementNotFoundException();
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

            // T115, FR-038/FR-050: a ticket folio is capped by the ticket's real collected money and refunded by
            // bank transfer, so no provider transaction number is needed or invented.
            List<RefundablePayment> payments = ticketCollectedMoney(bill);
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
                        // T098/T115: every correction is refunded through the bank refund lifecycle.
                        .operationReference("BANK-REFUND-" + UUID.randomUUID())
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
        return new Opened(toResponse(correction, settlementCase), settlementCase.getCaseReference(), dispatch);
    }

    @Transactional(readOnly = true)
    public FolioCorrectionDtos.Response read(String bookingReference, String correctionReference,
                                             CurrentAccountActor actor) {
        String bookingRef = required(bookingReference, "bookingReference");
        String corrRef = required(correctionReference, "correctionReference");

        var ticketCtx = booking.getTicketFolioContext(bookingRef).orElse(null);
        if (ticketCtx == null) throw new WorkshopSettlementNotFoundException();

        // T114, FR-048 / Constitution II: no contact proof in a URL. The owning Member, a Manager or the assigned
        // Staff read here; a Guest reads through POST …/folio-lookups with the proof in the body.
        Long locationId = ticketCtx.locationReference();
        if (actor == null) {
            throw new WorkshopSettlementNotFoundException();
        } else if (actor.role() == ActorRole.MEMBER) {
            if (!Objects.equals(actor.accountId(), ticketCtx.buyerMemberAccountId())) {
                throw new WorkshopSettlementNotFoundException();
            }
        } else if (actor.role() == ActorRole.MANAGER) {
            authorization.requireManager(actor);
        } else if (actor.role() == ActorRole.STAFF) {
            authorization.requireManagerOrAssignedStaff(actor, locationId, ticketCtx.lineReference());
        } else {
            throw new WorkshopSettlementNotFoundException();
        }

        SettlementFolioCorrection correction = corrections
                .findByBookingReferenceAndCorrectionReference(bookingRef, corrRef)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementCase settlementCase = cases.findByCaseReference(correction.getCaseReference())
                .orElseThrow(WorkshopSettlementNotFoundException::new);

        return toResponse(correction, settlementCase);
    }

    private FolioCorrectionDtos.Response toResponse(SettlementFolioCorrection correction, SettlementCase settlementCase) {
        String refundId = null;
        String refundStatus = null;
        var allocList = allocations.findAllByCaseReferenceOrderByCreatedAtAsc(settlementCase.getCaseReference());
        if (!allocList.isEmpty() && allocList.getFirst().getBankRefundReference() != null) {
            refundId = allocList.getFirst().getBankRefundReference();
            var reqOpt = refundRequests.findByRequestReference(refundId);
            if (reqOpt.isPresent()) {
                refundStatus = reqOpt.get().getStatus().name();
            }
        }
        return FolioCorrectionDtos.Response.from(correction, settlementCase, refundId, refundStatus);
    }

    /**
     * T115: the money really collected for one ticket, oldest first — its share of the group's booking-time payment
     * (the folio's first collection, from 003.1's immutable snapshot) and then every CONFIRMED folio collection,
     * whatever its method. A VNPay part counts once, through its collection (T107). These go back by bank
     * transfer, so no provider transaction number or create date is attached.
     */
    private List<RefundablePayment> ticketCollectedMoney(SettlementFinalBill bill) {
        List<RefundablePayment> payments = new ArrayList<>();
        if (bill.getAcceptedDepositAmountVnd() != null && bill.getAcceptedDepositAmountVnd() > 0) {
            payments.add(new RefundablePayment("BOOKING_TIME:" + bill.getBookingReference(), null, null,
                    bill.getAcceptedDepositAmountVnd(), "VND"));
        }
        for (var col : collections.findByBillReferenceOrderByIdAsc(bill.getBillReference())) {
            if (col.getStatus() == FolioCollectionStatus.CONFIRMED && col.getAmountVnd() > 0) {
                payments.add(new RefundablePayment("COLLECTION:" + col.getCollectionReference(), null, null,
                        col.getAmountVnd(), "VND"));
            }
        }
        if (payments.isEmpty()) {
            throw new WorkshopSettlementConflictException("COLLECTED_MONEY_UNAVAILABLE");
        }
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

