package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Finalization, handover and debt of one folio (003.3 T095, FR-035).
 *
 * <p>A folio is {@code FINALIZED} only when its balance is zero and no collection is pending, which is what
 * makes "Chờ xác minh thanh toán" resolve itself: Staff records the customer's transfer reference, the
 * customer leaves without the product, and the folio finalizes by itself the moment that part is confirmed.
 *
 * <p>Handing the product over before verification is an exception only a Manager may approve, with a reason,
 * and recording a real debt or closing it is Manager-only too. Both are audited and neither finalizes the
 * folio: the money is still owed, so nothing pretends otherwise.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolioFinalizationService {

    private final SettlementFinalBillRepository bills;
    private final SettlementBusinessAuditRepository audits;
    private final FolioBalanceService balances;

    /** Finalizes the folio when nothing is owed and nothing is unproven; otherwise leaves it OPEN. */
    @Transactional
    public SettlementFinalBill finalizeIfSettled(SettlementFinalBill folio, String correlationReference) {
        if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(folio.getLifecycleStatus())) {
            return folio;
        }
        FolioBalanceService.FolioBalance balance = balances.balanceOf(folio);
        if (balance.balanceDue() != 0 || balance.hasPendingPart()) {
            return folio;
        }
        folio.setLifecycleStatus(SettlementFinalBillLifecycleStatus.FINALIZED.name());
        SettlementFinalBill saved = bills.saveAndFlush(folio);
        audit(saved, "FOLIO_FINALIZED", null, null, correlationReference);
        log.info("Folio {} finalized: balance cleared and no collection pending", saved.getBookingReference());
        return saved;
    }

    /** Whether the product may be handed over: finalized, or a Manager approved the exception (FR-035). */
    @Transactional(readOnly = true)
    public boolean mayHandOver(SettlementFinalBill folio) {
        return SettlementFinalBillLifecycleStatus.FINALIZED.name().equals(folio.getLifecycleStatus())
                || folio.getHandoverApprovedAt() != null;
    }

    /**
     * The Manager-only exception: the product leaves before the money is proven. It records who, why and
     * when, and does not finalize the folio or touch the balance.
     */
    @Transactional
    public SettlementFinalBill approveHandoverException(String bookingReference, Long managerAccountId, String reason,
                                                        String correlationReference) {
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("HANDOVER_REASON_REQUIRED");
        }
        SettlementFinalBill folio = lock(bookingReference);
        if (SettlementFinalBillLifecycleStatus.FINALIZED.name().equals(folio.getLifecycleStatus())) {
            // Nothing to excuse: a finalized folio already allows handover.
            return folio;
        }
        if (folio.getHandoverApprovedAt() != null) {
            return folio;
        }
        folio.setHandoverApprovedByAccountId(managerAccountId);
        folio.setHandoverReason(reason.trim());
        folio.setHandoverApprovedAt(Instant.now());
        SettlementFinalBill saved = bills.saveAndFlush(folio);
        audit(saved, "FOLIO_HANDOVER_BEFORE_VERIFICATION", managerAccountId, reason.trim(), correlationReference);
        return saved;
    }

    /**
     * Records a real debt (`Còn nợ`), Manager-only and with a reason. There is deliberately no reminder, no
     * deadline and no booking block: a Manager follows it up and closes it by hand.
     */
    @Transactional
    public SettlementFinalBill recordDebt(String bookingReference, Long managerAccountId, long amountVnd,
                                          String reason, String correlationReference) {
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("DEBT_REASON_REQUIRED");
        }
        SettlementFinalBill folio = lock(bookingReference);
        FolioBalanceService.FolioBalance balance = balances.balanceOf(folio);
        if (amountVnd <= 0 || amountVnd > balance.balanceDue()) {
            throw new WorkshopSettlementValidationException("DEBT_AMOUNT_INVALID");
        }
        folio.setDebtAmountVnd(amountVnd);
        folio.setDebtReason(reason.trim());
        folio.setDebtRecordedByAccountId(managerAccountId);
        folio.setDebtRecordedAt(Instant.now());
        folio.setDebtClosedByAccountId(null);
        folio.setDebtClosedAt(null);
        SettlementFinalBill saved = bills.saveAndFlush(folio);
        audit(saved, "FOLIO_DEBT_RECORDED", managerAccountId, reason.trim(), correlationReference);
        return saved;
    }

    /** Closes a recorded debt, Manager-only and with a reason. The balance itself is settled by a collection. */
    @Transactional
    public SettlementFinalBill closeDebt(String bookingReference, Long managerAccountId, String reason,
                                         String correlationReference) {
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("DEBT_REASON_REQUIRED");
        }
        SettlementFinalBill folio = lock(bookingReference);
        if (folio.getDebtRecordedAt() == null) {
            throw new WorkshopSettlementConflictException("NO_DEBT_RECORDED");
        }
        if (folio.getDebtClosedAt() != null) {
            return folio;
        }
        folio.setDebtClosedByAccountId(managerAccountId);
        folio.setDebtClosedAt(Instant.now());
        SettlementFinalBill saved = bills.saveAndFlush(folio);
        audit(saved, "FOLIO_DEBT_CLOSED", managerAccountId, reason.trim(), correlationReference);
        return saved;
    }

    private SettlementFinalBill lock(String bookingReference) {
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new WorkshopSettlementValidationException("BOOKING_REFERENCE_REQUIRED");
        }
        return bills.findByBookingReferenceForUpdate(bookingReference.trim())
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    private void audit(SettlementFinalBill folio, String action, Long actorAccountId, String reason,
                       String correlationReference) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_FINAL_BILL")
                .aggregateReference(folio.getBookingReference())
                .actionCode(action)
                .actorReference(actorAccountId == null ? "SYSTEM" : "account:" + actorAccountId)
                .correlationReference(correlationReference == null || correlationReference.isBlank()
                        ? "CORR-" + UUID.randomUUID() : correlationReference)
                .reasonReference(reason)
                .createdAt(Instant.now())
                .build());
    }
}
