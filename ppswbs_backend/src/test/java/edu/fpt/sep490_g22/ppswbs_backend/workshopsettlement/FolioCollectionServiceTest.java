package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.3 T094/T095: collecting a folio balance in several parts and mixed methods, verifying each part, and
 * finalizing only when nothing is owed and nothing is unproven.
 */
class FolioCollectionServiceTest {

    private static final String TICKET = "GRP-TEST0001-L0-T1";
    private static final String BILL = "SETTLE-BILL-1";
    private static final Long BRANCH = 5L;
    private static final Long STAFF = 11L;
    private static final Long MANAGER = 22L;

    private SettlementFinalBillRepository bills;
    private SettlementFolioCollectionRepository collections;
    private SettlementCashDayCloseRepository cashCloses;
    private SettlementChargeRepository charges;
    private SettlementEvidenceConclusionRepository evidence;
    private FolioCollectionService service;
    private FolioFinalizationService finalization;
    private BankRefundService bankRefunds;
    private PaymentOwnerRegistrationPort paymentOwners;
    private PaymentGatewayCheckoutPort checkout;
    private SettlementBusinessAuditRepository audits;
    private WorkshopBookingSettlementAdapter booking;
    private SettlementFinalBill folio;
    private final List<SettlementFolioCollection> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bills = mock(SettlementFinalBillRepository.class);
        collections = mock(SettlementFolioCollectionRepository.class);
        cashCloses = mock(SettlementCashDayCloseRepository.class);
        charges = mock(SettlementChargeRepository.class);
        evidence = mock(SettlementEvidenceConclusionRepository.class);
        audits = mock(SettlementBusinessAuditRepository.class);
        booking = mock(WorkshopBookingSettlementAdapter.class);
        when(booking.recordTicketFinalBalanceResolved(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FinalBalanceResolutionOutcome.accepted());
        bankRefunds = mock(BankRefundService.class);
        paymentOwners = mock(PaymentOwnerRegistrationPort.class);

        folio = SettlementFinalBill.builder().billReference(BILL).bookingReference(TICKET)
                .postPromotionAmountVnd(500_000L).acceptedDepositAmountVnd(200_000L).remainingAmountVnd(300_000L)
                .currencyCode("VND").lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name()).build();
        when(bills.findByBookingReferenceForUpdate(TICKET)).thenReturn(Optional.of(folio));
        when(bills.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(BILL, "CONFIRMED")).thenReturn(List.of());
        when(evidence.findByBookingReferenceAndConclusion(TICKET, "FINAL_PAYMENT_VERIFIED")).thenReturn(List.of());
        when(collections.findByBillReferenceOrderByIdAsc(BILL)).thenReturn(rows);
        when(collections.findByBillReferenceAndStatusOrderByIdAsc(anyString(), any())).thenAnswer(invocation ->
                rows.stream().filter(row -> row.getStatus() == invocation.getArgument(1)).toList());
        when(collections.findByMethodAndExternalReference(any(), anyString())).thenAnswer(invocation ->
                rows.stream().filter(row -> row.getMethod() == invocation.getArgument(0)
                        && invocation.getArgument(1).equals(row.getExternalReference())).findFirst());
        when(collections.saveAndFlush(any())).thenAnswer(invocation -> {
            SettlementFolioCollection row = invocation.getArgument(0);
            if (!rows.contains(row)) {
                rows.add(row);
            }
            return row;
        });
        when(collections.findByCollectionReference(anyString())).thenAnswer(invocation ->
                rows.stream().filter(row -> invocation.getArgument(0).equals(row.getCollectionReference()))
                        .findFirst());

        FolioBalanceService balances = new FolioBalanceService(charges, evidence, collections);
        finalization = new FolioFinalizationService(bills, audits, balances, booking);
        checkout = mock(PaymentGatewayCheckoutPort.class);
        when(checkout.createFinalPaymentUrl(anyString(), anyString(), anyLong(), any(), any(), anyString()))
                .thenAnswer(invocation -> "https://pay.example/" + invocation.getArgument(0));
        service = new FolioCollectionService(bills, collections, cashCloses, audits, balances, finalization,
                bankRefunds, paymentOwners, checkout);
    }

    private SettlementFolioCollection row(String collectionReference) {
        return rows.stream().filter(row -> collectionReference.equals(row.getCollectionReference())).findFirst()
                .orElseThrow();
    }

    // ---- FR-008 per ticket: 003.1 hears the ticket is settled when its folio finalizes (BE-DESIGN-0006) ----

    @Test
    void finalizingATicketFolioTellsBookingTheTicketIsSettledOnce() {
        service.collectCash(TICKET, 300_000, 300_000, "RECEIPT-1", null, BRANCH, STAFF, "corr-final");

        assertThat(folio.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.FINALIZED.name());
        verify(booking, times(1)).recordTicketFinalBalanceResolved(TICKET, BILL, "TICKET-SETTLED:" + BILL, "corr-final");
    }

    @Test
    void aFolioThatIsNotSettledSendsNothing() {
        service.collectCash(TICKET, 100_000, 100_000, "RECEIPT-1", null, BRANCH, STAFF, "corr");

        assertThat(folio.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());
        verify(booking, never()).recordTicketFinalBalanceResolved(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aRefusalByBookingKeepsTheFinalizedFolioAndIsAudited() {
        when(booking.recordTicketFinalBalanceResolved(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FinalBalanceResolutionOutcome.rejected(BookingRejectionReason.INVALID_STATE));

        service.collectCash(TICKET, 300_000, 300_000, "RECEIPT-1", null, BRANCH, STAFF, "corr");

        assertThat(folio.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.FINALIZED.name());
        ArgumentCaptor<SettlementBusinessAudit> saved = ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(audit -> {
            assertThat(audit.getActionCode()).isEqualTo("TICKET_BALANCE_CONCLUSION_REJECTED");
            assertThat(audit.getReasonReference()).isEqualTo("INVALID_STATE");
        });
    }

    @Test
    void aFolioBookingDoesNotKnowAsATicketIsSkippedWithoutARefusalAudit() {
        when(booking.recordTicketFinalBalanceResolved(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FinalBalanceResolutionOutcome.rejected(BookingRejectionReason.BOOKING_NOT_FOUND));

        service.collectCash(TICKET, 300_000, 300_000, "RECEIPT-1", null, BRANCH, STAFF, "corr");

        ArgumentCaptor<SettlementBusinessAudit> saved = ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .noneMatch(audit -> "TICKET_BALANCE_CONCLUSION_REJECTED".equals(audit.getActionCode()));
    }

    // ---- cash ----

    @Test
    void cashIsConfirmedOnTheSpotAndOnlyTheAppliedAmountReachesTheFolio() {
        var outcome = service.collectCash(TICKET, 300_000, 500_000, "RECEIPT-1", "Khach A", BRANCH, STAFF, "corr");

        assertThat(outcome.status()).isEqualTo(FolioCollectionStatus.CONFIRMED);
        assertThat(outcome.amountVnd()).isEqualTo(300_000);
        assertThat(outcome.balanceDue()).isZero();
        assertThat(rows.get(0).getCashReceivedVnd()).isEqualTo(500_000);
        assertThat(rows.get(0).getCashChangeVnd()).isEqualTo(200_000);
        // FR-035: the balance is clear and nothing is pending, so the folio finalizes itself.
        assertThat(outcome.folioStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.FINALIZED.name());
    }

    @Test
    void cashBelowTheAppliedAmountIsRefused() {
        assertThatThrownBy(() -> service.collectCash(TICKET, 300_000, 200_000, "R", null, BRANCH, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    void cashNeedsABranchBecauseItMustLandInThatBranchesCashBook() {
        assertThatThrownBy(() -> service.collectCash(TICKET, 300_000, 300_000, "R", null, null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    void aBalanceUnderTheVnpayFloorIsStillCollectableInCash() {
        folio.setRemainingAmountVnd(3_000L);

        var outcome = service.collectCash(TICKET, 3_000, 5_000, "RECEIPT-2", null, BRANCH, STAFF, "corr");

        assertThat(outcome.balanceDue()).isZero();
        assertThat(rows.get(0).getCashChangeVnd()).isEqualTo(2_000);
    }

    @Test
    void cashTakenAfterTheBranchClosedBelongsToTheNextDay() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        when(cashCloses.findByBranchIdAndBusinessDay(BRANCH, today))
                .thenReturn(Optional.of(SettlementCashDayClose.builder().branchId(BRANCH).businessDay(today).build()));

        service.collectCash(TICKET, 300_000, 300_000, "RECEIPT-3", null, BRANCH, STAFF, "corr");

        assertThat(rows.get(0).getCashBusinessDay()).isEqualTo(today.plusDays(1));
    }

    @Test
    void theSameCashReceiptCannotBeRecordedTwice() {
        folio.setRemainingAmountVnd(400_000L);
        service.collectCash(TICKET, 100_000, 100_000, "RECEIPT-9", null, BRANCH, STAFF, "corr");

        assertThatThrownBy(() -> service.collectCash(TICKET, 100_000, 100_000, "RECEIPT-9", null, BRANCH, STAFF, "c"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void cashAboveTheBalanceIsKeptInFullAndFlaggedAsOwedBack() {
        var outcome = service.collectCash(TICKET, 350_000, 350_000, "RECEIPT-4", null, BRANCH, STAFF, "corr");

        assertThat(outcome.amountVnd()).isEqualTo(350_000);
        assertThat(outcome.excessVnd()).isEqualTo(50_000);
        assertThat(outcome.balanceDue()).isZero();
        verify(bankRefunds).open(eq(TICKET), any(), eq(RefundReason.EXCESS_OR_DUPLICATE_MONEY),
                eq(outcome.collectionReference()), eq(50_000L), eq("corr"));
    }

    // ---- several parts and mixed methods ----

    @Test
    void aBalanceMayBeCollectedInSeveralPartsAndMixedMethods() {
        service.collectCash(TICKET, 100_000, 100_000, "RECEIPT-5", null, BRANCH, STAFF, "corr");
        var transfer = service.recordBankTransfer(TICKET, 200_000, "BANK-TX-1", "Khach A", STAFF, "corr");

        assertThat(transfer.status()).isEqualTo(FolioCollectionStatus.PENDING_VERIFICATION);
        // FR-032: a pending part never reduces the balance.
        assertThat(transfer.balanceDue()).isEqualTo(200_000);
        assertThat(transfer.pendingVnd()).isEqualTo(200_000);
        assertThat(transfer.folioStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());

        var confirmed = service.confirmBankTransfer(transfer.collectionReference(), MANAGER, "corr");

        assertThat(confirmed.balanceDue()).isZero();
        assertThat(confirmed.pendingVnd()).isZero();
        assertThat(confirmed.folioStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.FINALIZED.name());
    }

    @Test
    void thesSameBalanceIsNeverAssignedTwice() {
        service.recordBankTransfer(TICKET, 300_000, "BANK-TX-2", null, STAFF, "corr");

        assertThatThrownBy(() -> service.recordBankTransfer(TICKET, 1_000, "BANK-TX-3", null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void theSameBankTransactionCannotBeRecordedTwice() {
        folio.setRemainingAmountVnd(400_000L);
        service.recordBankTransfer(TICKET, 100_000, "BANK-TX-4", null, STAFF, "corr");

        assertThatThrownBy(() -> service.recordBankTransfer(TICKET, 100_000, "BANK-TX-4", null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void aTransferWithoutItsReferenceIsRefused() {
        assertThatThrownBy(() -> service.recordBankTransfer(TICKET, 100_000, "  ", null, STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    // ---- gateway parts ----

    @Test
    void aGatewayPartUnderFiveThousandIsRefusedInsteadOfRounded() {
        assertThatThrownBy(() -> service.recordGatewayAttempt(TICKET, 4_999, "10.0.0.1", STAFF, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        verifyNoInteractions(checkout, paymentOwners);
    }

    // ---- T108: the server creates the merchant reference and the VNPay checkout ----

    @Test
    void theServerCreatesTheMerchantReferenceAndATenMinuteCheckoutForAVnpayPart() {
        Instant before = Instant.now();

        var attempt = service.recordGatewayAttempt(TICKET, 300_000, "10.0.0.1", STAFF, "corr");

        SettlementFolioCollection part = row(attempt.collectionReference());
        String merchant = part.getExternalReference();
        assertThat(merchant).matches("FOL[0-9A-F]{32}");
        assertThat(part.getPaymentUrlSnapshot()).isEqualTo("https://pay.example/" + merchant);
        assertThat(part.getPaymentExpiresAt()).isBetween(before.plus(Duration.ofMinutes(10)),
                Instant.now().plus(Duration.ofMinutes(10)));
        assertThat(part.getProviderCreateDate()).matches("[0-9]{14}");
        assertThat(part.getStatus()).isEqualTo(FolioCollectionStatus.PENDING_VERIFICATION);
        ArgumentCaptor<Instant> created = ArgumentCaptor.forClass(Instant.class);
        verify(checkout).createFinalPaymentUrl(eq(merchant), eq(TICKET), eq(300_000L), created.capture(),
                eq(part.getPaymentExpiresAt()), eq("10.0.0.1"));
        assertThat(Duration.between(created.getValue(), part.getPaymentExpiresAt())).isEqualTo(Duration.ofMinutes(10));
        verify(paymentOwners).registerOwner(merchant, TICKET, "FINAL_PAYMENT", 300_000L, "VND",
                part.getProviderCreateDate());
    }

    @Test
    void twoVnpayPartsNeverShareAMerchantReference() {
        var first = service.recordGatewayAttempt(TICKET, 100_000, null, STAFF, "corr");
        service.markPendingFailed(first.collectionReference(), MANAGER, "Khach doi phuong thuc", "corr");
        var second = service.recordGatewayAttempt(TICKET, 100_000, null, STAFF, "corr");

        assertThat(row(first.collectionReference()).getExternalReference())
                .isNotEqualTo(row(second.collectionReference()).getExternalReference());
        verify(checkout, times(2)).createFinalPaymentUrl(anyString(), eq(TICKET), eq(100_000L), any(), any(),
                eq("127.0.0.1"));
    }

    // ---- T109: a stuck pending part is failed by a Manager with a reason, or by VNPay's own answer ----

    @Test
    void anotherMethodWaitsUntilAPendingGatewayPartIsReconciled() {
        var attempt = service.recordGatewayAttempt(TICKET, 300_000, null, STAFF, "corr");

        assertThatThrownBy(() -> service.collectCash(TICKET, 300_000, 300_000, "RECEIPT-6", null, BRANCH, STAFF, "c"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("PENDING_GATEWAY_PART_MUST_BE_RECONCILED");

        // Reconciled as not paid, and only then is the balance free again.
        var failed = service.markPendingFailed(attempt.collectionReference(), MANAGER, "Khach khong thanh toan", "corr");
        assertThat(failed.status()).isEqualTo(FolioCollectionStatus.FAILED);
        assertThat(row(attempt.collectionReference()).getPaymentUrlSnapshot()).isNull();
        ArgumentCaptor<SettlementBusinessAudit> saved = ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(audit -> {
            assertThat(audit.getActionCode()).isEqualTo("FOLIO_COLLECTION_FAILED");
            assertThat(audit.getActorReference()).isEqualTo("account:" + MANAGER);
            assertThat(audit.getReasonReference()).isEqualTo("Khach khong thanh toan");
        });
        var cash = service.collectCash(TICKET, 300_000, 300_000, "RECEIPT-7", null, BRANCH, STAFF, "corr");
        assertThat(cash.balanceDue()).isZero();
    }

    @Test
    void aPendingTransferMayAlsoBeFailedAndAFailedPartCannotBeFailedAgain() {
        var transfer = service.recordBankTransfer(TICKET, 300_000, "BANK-TX-9", null, STAFF, "corr");

        service.markPendingFailed(transfer.collectionReference(), MANAGER, "Khong thay tien ve", "corr");

        assertThat(row(transfer.collectionReference()).getStatus()).isEqualTo(FolioCollectionStatus.FAILED);
        assertThatThrownBy(() -> service.markPendingFailed(transfer.collectionReference(), MANAGER, "Lan hai", "c"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("COLLECTION_NOT_PENDING");
    }

    @Test
    void failingAPartNeedsAReason() {
        var attempt = service.recordGatewayAttempt(TICKET, 300_000, null, STAFF, "corr");

        assertThatThrownBy(() -> service.markPendingFailed(attempt.collectionReference(), MANAGER, "  ", "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThat(row(attempt.collectionReference()).getStatus()).isEqualTo(FolioCollectionStatus.PENDING_VERIFICATION);
    }

    @Test
    void vnpaysOwnFailureFailsOnlyAPendingPartAsTheSystem() {
        var attempt = service.recordGatewayAttempt(TICKET, 300_000, null, STAFF, "corr");
        String merchant = row(attempt.collectionReference()).getExternalReference();

        assertThat(service.failGatewayCollectionFromProvider(merchant, "VNPAY_IPN_TRANSACTION_STATUS_02", "IPN-1"))
                .isTrue();
        assertThat(service.failGatewayCollectionFromProvider(merchant, "VNPAY_IPN_TRANSACTION_STATUS_02", "IPN-1"))
                .isFalse();
        assertThat(service.failGatewayCollectionFromProvider("UNKNOWN-REF", "x", "c")).isFalse();

        assertThat(row(attempt.collectionReference()).getStatus()).isEqualTo(FolioCollectionStatus.FAILED);
        ArgumentCaptor<SettlementBusinessAudit> saved = ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).filteredOn(a -> "FOLIO_GATEWAY_COLLECTION_FAILED".equals(a.getActionCode()))
                .singleElement().satisfies(audit -> {
                    assertThat(audit.getActorReference()).isEqualTo("SYSTEM");
                    assertThat(audit.getReasonReference()).isEqualTo("VNPAY_IPN_TRANSACTION_STATUS_02");
                });
    }

    @Test
    void signedGatewayEvidenceConfirmsItsOwnPartAndIsSafeToReplay() {
        var attempt = service.recordGatewayAttempt(TICKET, 300_000, null, STAFF, "corr");
        String merchant = row(attempt.collectionReference()).getExternalReference();

        var first = service.confirmGatewayCollection(merchant, 300_000, "corr");
        var second = service.confirmGatewayCollection(merchant, 300_000, "corr");

        assertThat(attempt.collectionReference()).isEqualTo(first.collectionReference());
        assertThat(first.balanceDue()).isZero();
        assertThat(second.status()).isEqualTo(FolioCollectionStatus.CONFIRMED);
        assertThat(row(attempt.collectionReference()).getPaymentUrlSnapshot()).isNull();
        verify(bills, atLeastOnce()).saveAndFlush(any());
    }

    @Test
    void gatewayEvidenceForAnotherAmountIsRefused() {
        var attempt = service.recordGatewayAttempt(TICKET, 300_000, null, STAFF, "corr");
        String merchant = row(attempt.collectionReference()).getExternalReference();

        assertThatThrownBy(() -> service.confirmGatewayCollection(merchant, 299_000, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    // ---- T110: verified money that settles no part is owed back in full ----

    @Test
    void unappliedGatewayMoneyOpensAnExcessBankRefundForTheWholeAmountWithAnAudit() {
        folio.setGroupReference("GRP-TEST0001");

        service.routeUnappliedGatewayMoney(folio, "FOL-REF", "VNPAY-999", 300_000, "FOLIO_NOT_OPEN", "corr");

        verify(bankRefunds).open(TICKET, "GRP-TEST0001", RefundReason.EXCESS_OR_DUPLICATE_MONEY, "VNPAY-999",
                300_000, "corr");
        ArgumentCaptor<SettlementBusinessAudit> saved = ArgumentCaptor.forClass(SettlementBusinessAudit.class);
        verify(audits).save(saved.capture());
        assertThat(saved.getValue().getActionCode()).isEqualTo("FOLIO_GATEWAY_MONEY_EXCESS_OWED");
        assertThat(saved.getValue().getReasonReference()).isEqualTo("FOLIO_NOT_OPEN");
        assertThat(saved.getValue().getEvidenceReference()).isEqualTo("FOL-REF");
    }

    // ---- folio state ----

    @Test
    void aFinalizedFolioTakesNoFurtherCollection() {
        service.collectCash(TICKET, 300_000, 300_000, "RECEIPT-8", null, BRANCH, STAFF, "corr");

        assertThatThrownBy(() -> service.collectCash(TICKET, 1_000, 1_000, "RECEIPT-10", null, BRANCH, STAFF, "c"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void aFolioWithNothingOwedTakesNoCollection() {
        folio.setRemainingAmountVnd(0L);

        assertThatThrownBy(() -> service.collectCash(TICKET, 1_000, 1_000, "RECEIPT-11", null, BRANCH, STAFF, "c"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("FOLIO_HAS_NO_BALANCE");
    }

    @Test
    void addOnChargesRaiseTheBalanceThatMayBeCollected() {
        when(charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(BILL, "CONFIRMED")).thenReturn(List.of(
                SettlementCharge.builder().billReference(BILL).amountVnd(50_000L).lifecycleStatus("CONFIRMED").build()));

        var outcome = service.collectCash(TICKET, 350_000, 350_000, "RECEIPT-12", null, BRANCH, STAFF, "corr");

        assertThat(outcome.excessVnd()).isZero();
        assertThat(outcome.balanceDue()).isZero();
    }

    // ---- finalization, handover and debt (T095) ----

    @Test
    void anUnprovenPartKeepsTheFolioOpenUntilItIsConfirmed() {
        service.recordBankTransfer(TICKET, 300_000, "BANK-TX-5", null, STAFF, "corr");

        SettlementFinalBill afterRecord = finalization.finalizeIfSettled(folio, "corr");

        assertThat(afterRecord.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());
    }

    @Test
    void aHandoverBeforeVerificationIsAManagerExceptionThatDoesNotFinalizeTheFolio() {
        service.recordBankTransfer(TICKET, 300_000, "BANK-TX-6", null, STAFF, "corr");

        SettlementFinalBill saved = finalization.approveHandoverException(TICKET, MANAGER, "Khach phai di gap",
                "corr");

        assertThat(saved.getHandoverApprovedByAccountId()).isEqualTo(MANAGER);
        assertThat(saved.getHandoverReason()).isEqualTo("Khach phai di gap");
        assertThat(saved.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());
        assertThat(finalization.mayHandOver(saved)).isTrue();
    }

    @Test
    void aHandoverExceptionNeedsAReason() {
        assertThatThrownBy(() -> finalization.approveHandoverException(TICKET, MANAGER, "   ", "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    void aDebtIsRecordedAndClosedWithAReasonAndNeverAboveTheBalance() {
        SettlementFinalBill withDebt = finalization.recordDebt(TICKET, MANAGER, 300_000, "Khach xin no", "corr");
        assertThat(withDebt.getDebtAmountVnd()).isEqualTo(300_000);
        assertThat(withDebt.getDebtRecordedByAccountId()).isEqualTo(MANAGER);

        assertThatThrownBy(() -> finalization.recordDebt(TICKET, MANAGER, 400_000, "Qua so du", "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> finalization.recordDebt(TICKET, MANAGER, 1_000, "  ", "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);

        SettlementFinalBill closed = finalization.closeDebt(TICKET, MANAGER, "Khach da tra", "corr");
        assertThat(closed.getDebtClosedAt()).isNotNull();
    }

    @Test
    void closingADebtThatWasNeverRecordedIsRefused() {
        assertThatThrownBy(() -> finalization.closeDebt(TICKET, MANAGER, "Khong co no", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }
}
