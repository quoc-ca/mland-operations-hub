package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * HTTP-facing rules of the folio collections and the cash close (003.3 T094-T096).
 *
 * <p>Who may do what: taking money at the counter and closing the cash book are Manager or Staff of that
 * branch; confirming a direct transfer, approving a handover before verification, recording or closing a debt
 * and confirming a cash difference are Manager-only. The transfer confirmation is deliberately kept away from
 * the person who took the money (FR-032), which is the split the gateway gets for free from its signature.
 */
@Service
@RequiredArgsConstructor
public class FolioCollectionApiService {

    private final WorkshopSettlementAuthorizationService authorization;
    private final FolioCollectionService collections;
    private final FolioFinalizationService finalization;
    private final CashDayCloseService cashCloses;
    private final SettlementIdempotencyService idempotency;

    public CollectionResponse collectCash(String bookingReference, CashCollectionRequest request,
                                          String correlation, CurrentAccountActor actor) {
        CurrentAccountActor staff = authorization.requireManagerOrStaff(actor, request.branchId());
        return response(collections.collectCash(bookingReference, request.amountVnd(), request.cashReceivedVnd(),
                request.receiptReference(), request.payerLabel(), request.branchId(), staff.accountId(),
                correlation));
    }

    public CollectionResponse recordBankTransfer(String bookingReference, BankTransferRequest request,
                                                 String correlation, CurrentAccountActor actor) {
        CurrentAccountActor staff = authorization.requireManagerOrStaff(actor, request.branchId());
        return response(collections.recordBankTransfer(bookingReference, request.amountVnd(),
                request.transactionReference(), request.payerLabel(), staff.accountId(), correlation));
    }

    /** FR-032: only an actor who can see the shop bank account confirms a transfer; remote is fine. */
    public CollectionResponse confirmBankTransfer(String collectionReference, String correlation,
                                                  CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        return response(collections.confirmBankTransfer(collectionReference, manager.accountId(), correlation));
    }

    public void approveHandover(String bookingReference, ManagerReasonRequest request, String correlation,
                                CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        finalization.approveHandoverException(bookingReference, manager.accountId(), request.reason(), correlation);
    }

    public void recordDebt(String bookingReference, DebtRequest request, String correlation,
                           CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        finalization.recordDebt(bookingReference, manager.accountId(), request.amountVnd(), request.reason(),
                correlation);
    }

    public void closeDebt(String bookingReference, ManagerReasonRequest request, String correlation,
                          CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        finalization.closeDebt(bookingReference, manager.accountId(), request.reason(), correlation);
    }

    /**
     * T120 (FR-036, FR-022d): closes a branch's cash day once. The {@code Idempotency-Key} is required and stored;
     * a replay with the same key and content returns the stored close, a changed reuse is a conflict.
     */
    @Transactional
    public CashCloseResponse closeCashDay(CashCloseRequest request, String idempotencyKey, String correlation,
                                          CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor closer = authorization.requireManagerOrStaff(actor, request.branchId());
        LocalDate day = CashDayCloseService.dayOrToday(request.businessDay());
        String reason = request.reason() == null ? "" : request.reason().trim();
        String op = "CASH_CLOSE:" + request.branchId() + ":" + idempotencyKey.trim();
        String fp = "CASH_CLOSE:" + request.branchId() + ":" + day + ":" + request.countedCashVnd() + ":" + reason;
        var outcome = idempotency.execute(op, fp, () -> cashCloses.close(request.branchId(), day,
                request.countedCashVnd(), request.reason(), closer.accountId(), correlation).getCloseReference());
        return response(cashCloses.findByReference(outcome.outcomeReference()));
    }

    public CashCloseResponse confirmCashDifference(Long branchId, LocalDate businessDay, String correlation,
                                                   CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        return response(cashCloses.confirmDifference(branchId, businessDay, manager.accountId(), correlation));
    }

    /** FR-052: Manager only, reason and {@code Idempotency-Key} required; a replay returns the stored close. */
    @Transactional
    public CashCloseResponse confirmDifferenceById(String cashCloseId, String reason, String idempotencyKey,
                                                   String correlation, CurrentAccountActor actor) {
        requireKey(idempotencyKey);
        CurrentAccountActor manager = authorization.requireManager(actor);
        if (cashCloseId == null || cashCloseId.isBlank()) {
            throw new WorkshopSettlementValidationException("CASH_CLOSE_ID_REQUIRED");
        }
        String op = "CASH_DIFF:" + cashCloseId.trim() + ":" + idempotencyKey.trim();
        String fp = "CASH_DIFF:" + cashCloseId.trim() + ":" + (reason == null ? "" : reason.trim());
        var outcome = idempotency.execute(op, fp, () -> cashCloses.confirmDifferenceById(cashCloseId, reason,
                manager.accountId(), correlation).getCloseReference());
        return response(cashCloses.findByReference(outcome.outcomeReference()));
    }

    /** T120: the expected cash is read under the cash-close resource (Manager, or Staff of that branch). */
    public ExpectedCashResponse expectedCash(Long branchId, LocalDate businessDay, CurrentAccountActor actor) {
        authorization.requireManagerOrStaff(actor, branchId);
        LocalDate day = CashDayCloseService.dayOrToday(businessDay);
        return new ExpectedCashResponse(branchId, day, cashCloses.expectedCash(branchId, day));
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("Idempotency-Key must contain 16 to 128 characters");
        }
    }

    private static CollectionResponse response(FolioCollectionService.CollectionOutcome outcome) {
        return new CollectionResponse(outcome.collectionReference(), outcome.method().name(), outcome.amountVnd(),
                outcome.excessVnd(), outcome.status().name(), outcome.balanceDue(), outcome.pendingVnd(),
                outcome.folioStatus());
    }

    private static CashCloseResponse response(SettlementCashDayClose close) {
        return new CashCloseResponse(close.getCloseReference(), close.getBranchId(), close.getBusinessDay(),
                close.getExpectedCashVnd(), close.getCountedCashVnd(), close.getDifferenceVnd(),
                close.getDifferenceReason(), close.getStatus().name());
    }
}
