package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public CollectionResponse recordGatewayAttempt(String bookingReference, GatewayAttemptRequest request,
                                                   String correlation, CurrentAccountActor actor) {
        CurrentAccountActor staff = authorization.requireManagerOrStaff(actor, request.branchId());
        return response(collections.recordGatewayAttempt(bookingReference, request.amountVnd(),
                request.merchantReference(), staff.accountId(), correlation));
    }

    /** FR-032: only an actor who can see the shop bank account confirms a transfer; remote is fine. */
    public CollectionResponse confirmBankTransfer(String collectionReference, String correlation,
                                                  CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        return response(collections.confirmBankTransfer(collectionReference, manager.accountId(), correlation));
    }

    public CollectionResponse markPendingFailed(String collectionReference, String correlation,
                                                CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        return response(collections.markPendingFailed(collectionReference, manager.accountId(), correlation));
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

    public CashCloseResponse closeCashDay(CashCloseRequest request, String correlation, CurrentAccountActor actor) {
        CurrentAccountActor closer = authorization.requireManagerOrStaff(actor, request.branchId());
        return response(cashCloses.close(request.branchId(), request.businessDay(), request.countedCashVnd(),
                request.reason(), closer.accountId(), correlation));
    }

    public CashCloseResponse confirmCashDifference(Long branchId, LocalDate businessDay, String correlation,
                                                   CurrentAccountActor actor) {
        CurrentAccountActor manager = authorization.requireManager(actor);
        return response(cashCloses.confirmDifference(branchId, businessDay, manager.accountId(), correlation));
    }

    public long expectedCash(Long branchId, LocalDate businessDay, CurrentAccountActor actor) {
        authorization.requireManagerOrStaff(actor, branchId);
        return cashCloses.expectedCash(branchId, businessDay);
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
