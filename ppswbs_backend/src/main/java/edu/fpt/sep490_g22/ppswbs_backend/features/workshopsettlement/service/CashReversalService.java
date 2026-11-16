package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashReversalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.CashReversalDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashReversal;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Manager-only cash reversal of a CASH collection whose working day was already closed (003.3 T144, FR-036,
 * FR-053, CHK014).
 *
 * <p>The reversal is a negative cash entry dated the current Vietnam working day (the day after the close once the
 * counted amount was submitted). Neither the closed day nor the original collection is edited:
 * {@code FolioBalanceService} subtracts the reversal from what the folio has collected and the day's expected cash
 * adds it. A day that is not closed yet is corrected by the FR-053 void instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashReversalService {

    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementFinalBillRepository bills;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementCashReversalRepository reversals;
    private final SettlementRefundRequestRepository refunds;
    private final CashDayCloseService cashCloses;
    private final SettlementIdempotencyService idempotency;
    private final SettlementBusinessAuditRepository audits;

    @Transactional
    public CashReversalDto reverse(String ticketCode, String collectionId, String idempotencyKey,
                                   String correlationId, String reason, CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        String code = required(ticketCode, "ticketCode");
        SettlementFinalBill folio = bills.findByBookingReferenceForUpdate(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFolioCollection collection = findCollection(required(collectionId, "collectionId"));
        if (!folio.getBillReference().equals(collection.getBillReference())) {
            throw new WorkshopSettlementNotFoundException();
        }
        String key = idempotencyKey.trim();
        String correlation = correlationId == null || correlationId.isBlank()
                ? "CORR-" + UUID.randomUUID() : correlationId.trim();
        String op = "CASH_REVERSAL:" + code + ":" + collection.getCollectionReference() + ":" + key;
        String fp = "CASH_REVERSAL:" + code + ":" + collection.getCollectionReference() + ":" + reason.trim();
        var outcome = idempotency.execute(op, fp,
                () -> record(folio, collection, key, reason.trim(), manager.accountId(), correlation));
        return toDto(reversals.findByReversalReference(outcome.outcomeReference())
                .orElseThrow(WorkshopSettlementNotFoundException::new));
    }

    private String record(SettlementFinalBill folio, SettlementFolioCollection collection, String key, String reason,
                          Long managerAccountId, String correlation) {
        if (!"OPEN".equals(folio.getLifecycleStatus())) {
            // FINALIZED or CLOSED: the folio correction and the bank refund of FR-050/FR-038 apply instead.
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }
        if (collection.getMethod() != FolioCollectionMethod.CASH) {
            throw new WorkshopSettlementConflictException("COLLECTION_NOT_CASH");
        }
        if (collection.getStatus() == FolioCollectionStatus.VOIDED) {
            throw new WorkshopSettlementConflictException("COLLECTION_ALREADY_VOIDED");
        }
        if (collection.getStatus() != FolioCollectionStatus.CONFIRMED) {
            throw new WorkshopSettlementConflictException("COLLECTION_NOT_CONFIRMED");
        }
        if (reversals.existsByCollectionId(collection.getId())) {
            throw new WorkshopSettlementConflictException("COLLECTION_ALREADY_REVERSED");
        }
        if (refunds.existsBySourceReference(collection.getCollectionReference())) {
            throw new WorkshopSettlementConflictException("COLLECTION_UNDER_REFUND");
        }
        LocalDate originalDay = collection.getCashBusinessDay();
        if (originalDay == null || !cashCloses.isClosed(collection.getBranchId(), originalDay)) {
            // An open day is corrected by the FR-053 void, which never leaves a trace in a close.
            throw new WorkshopSettlementConflictException("CASH_DAY_NOT_CLOSED");
        }
        Instant now = Instant.now();
        SettlementCashReversal saved = reversals.saveAndFlush(SettlementCashReversal.builder()
                .reversalReference("CASHREV-" + UUID.randomUUID())
                .ticketCode(folio.getBookingReference())
                .billReference(folio.getBillReference())
                .collectionId(collection.getId())
                .branchId(collection.getBranchId())
                .amountVnd(Math.negateExact(collection.getAmountVnd()))
                .cashBusinessDay(cashCloses.currentCashDay(collection.getBranchId()))
                .originalCashBusinessDay(originalDay)
                .reason(reason)
                .recordedByAccountId(managerAccountId)
                .recordedAt(now)
                .idempotencyKey(key)
                .createdAt(now)
                .build());
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_CASH_REVERSAL")
                .aggregateReference(saved.getReversalReference())
                .actionCode("CASH_COLLECTION_REVERSED")
                .actorReference("account:" + managerAccountId)
                .correlationReference(correlation)
                .reasonReference(reason)
                .createdAt(now)
                .build());
        log.info("Cash reversal {} of collection {} on ticket {}: {} VND counts on {}", saved.getReversalReference(),
                collection.getCollectionReference(), folio.getBookingReference(), saved.getAmountVnd(),
                saved.getCashBusinessDay());
        return saved.getReversalReference();
    }

    private CashReversalDto toDto(SettlementCashReversal r) {
        String collectionRef = collections.findById(r.getCollectionId())
                .map(SettlementFolioCollection::getCollectionReference)
                .orElse(String.valueOf(r.getCollectionId()));
        return new CashReversalDto(r.getReversalReference(), r.getTicketCode(), collectionRef, r.getBranchId(),
                String.valueOf(r.getAmountVnd()), r.getCashBusinessDay(), r.getOriginalCashBusinessDay(),
                r.getReason(), String.valueOf(r.getRecordedByAccountId()), r.getRecordedAt());
    }

    private SettlementFolioCollection findCollection(String collectionId) {
        try {
            var byId = collections.findById(Long.parseLong(collectionId));
            if (byId.isPresent()) return byId.get();
        } catch (NumberFormatException ignored) {
            // a collection reference, not a numeric id
        }
        return collections.findByCollectionReference(collectionId)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
        return value.trim();
    }
}
