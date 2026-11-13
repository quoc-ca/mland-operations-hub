package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.3 T097, FR-038..FR-040: refunding Mland money by bank transfer outside the system — the request
 * lifecycle, the single-use account link, the encrypted account number and the per-ticket cap.
 */
class BankRefundServiceTest {

    private static final String TICKET = "GRP-TEST0001-L0-T1";
    private static final String GROUP = "GRP-TEST0001";
    private static final Long MANAGER = 22L;
    private static final Long STAFF = 11L;
    private static final Long EXECUTOR = 33L;

    private SettlementRefundRequestRepository requests;
    private SettlementRefundAccountLinkRepository links;
    private SettlementFolioCollectionRepository collections;
    private SettlementFinalBillRepository bills;
    private SettlementEvidenceConclusionRepository evidence;
    private BankRefundService service;
    private final SettlementCustomerNotifier notifier = mock(SettlementCustomerNotifier.class);
    private final List<SettlementRefundRequest> requestRows = new ArrayList<>();
    private final List<SettlementRefundAccountLink> linkRows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeAll
    static void key() {
        // 32 zero bytes: a test key, never a deployment one.
        RefundAccountCipher.useKeyForTests("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeEach
    void setUp() {
        requests = mock(SettlementRefundRequestRepository.class);
        links = mock(SettlementRefundAccountLinkRepository.class);
        collections = mock(SettlementFolioCollectionRepository.class);
        bills = mock(SettlementFinalBillRepository.class);
        evidence = mock(SettlementEvidenceConclusionRepository.class);
        SettlementBusinessAuditRepository audits = mock(SettlementBusinessAuditRepository.class);
        requestRows.clear();
        linkRows.clear();

        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            SettlementRefundRequest row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ids.getAndIncrement());
                row.setCreatedAt(Instant.now());
                row.setStatus(row.getStatus() == null ? RefundRequestStatus.PENDING_ACCOUNT : row.getStatus());
                requestRows.add(row);
            }
            return row;
        });
        when(requests.findByReasonAndSourceReference(any(), anyString())).thenAnswer(invocation ->
                requestRows.stream().filter(row -> row.getReason() == invocation.getArgument(0)
                        && invocation.getArgument(1).equals(row.getSourceReference())).findFirst());
        when(requests.findByRequestReference(anyString())).thenAnswer(invocation ->
                requestRows.stream().filter(row -> invocation.getArgument(0).equals(row.getRequestReference()))
                        .findFirst());
        when(requests.findForUpdate(anyString())).thenAnswer(invocation ->
                requestRows.stream().filter(row -> invocation.getArgument(0).equals(row.getRequestReference()))
                        .findFirst());
        when(requests.findById(any())).thenAnswer(invocation ->
                requestRows.stream().filter(row -> invocation.getArgument(0).equals(row.getId())).findFirst());
        when(requests.sumRefundedOrPending(anyString())).thenAnswer(invocation -> requestRows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getBookingReference()))
                .filter(row -> row.getStatus() != RefundRequestStatus.CLOSED_UNREACHABLE)
                .mapToLong(SettlementRefundRequest::getAmountVnd).sum());
        when(links.saveAndFlush(any())).thenAnswer(invocation -> {
            SettlementRefundAccountLink row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ids.getAndIncrement());
                linkRows.add(row);
            }
            return row;
        });
        when(links.findByTokenHash(anyString())).thenAnswer(invocation ->
                linkRows.stream().filter(row -> invocation.getArgument(0).equals(row.getTokenHash())).findFirst());
        when(links.findByRequestIdAndLifecycleStatus(any(), any())).thenAnswer(invocation ->
                linkRows.stream().filter(row -> invocation.getArgument(0).equals(row.getRequestId())
                        && row.getLifecycleStatus() == invocation.getArgument(1)).toList());
        givenCollected(500_000);

        service = new BankRefundService(requests, links, collections, bills, evidence, audits,
                mock(org.springframework.context.ApplicationEventPublisher.class), notifier);
    }

    // ---- opening ----

    @Test
    void opensARequestWaitingForTheAccountAndSendsOneLink() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        assertThat(opened.request().getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
        assertThat(opened.request().getRequestReference()).startsWith("REFUND-");
        assertThat(opened.rawAccountLinkToken()).isNotBlank();
        assertThat(linkRows).singleElement().satisfies(link -> {
            assertThat(link.getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.ACTIVE);
            assertThat(link.getExpiresAt()).isAfter(Instant.now().plusSeconds(6 * 24 * 3600));
            assertThat(link.getTokenHash()).hasSize(64).isNotEqualTo(opened.rawAccountLinkToken());
        });
        // Email 7 goes out once, with the raw token only in the email snapshot.
        verify(notifier, times(1)).notifyRefundAccountRequest(eq(opened.request()), eq(opened.rawAccountLinkToken()),
                any(), eq(false), eq("corr"));
    }

    @Test
    void theSameMoneyIsNeverAskedBackTwice() {
        var first = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        var again = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        assertThat(again.request().getRequestReference()).isEqualTo(first.request().getRequestReference());
        assertThat(again.rawAccountLinkToken()).isNull();
        assertThat(requestRows).hasSize(1);
    }

    @Test
    void aReminderIssuesAFreshLinkAndSendsTheReminderEmail() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        service.remindAccountNeeded(opened.request(), "corr-r");

        assertThat(linkRows).hasSize(2);
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.REVOKED);
        assertThat(linkRows.get(1).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.ACTIVE);
        verify(notifier).notifyRefundAccountRequest(eq(opened.request()), anyString(), any(), eq(true), eq("corr-r"));
    }

    @Test
    void aRefundNeverExceedsWhatWasCollectedForThatTicket() {
        service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 400_000, "corr");

        assertThatThrownBy(() -> service.open(TICKET, GROUP, RefundReason.EXCESS_OR_DUPLICATE_MONEY, "coll-1",
                200_000, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REFUND_EXCEEDS_COLLECTED_FOR_TICKET");
    }

    @Test
    void thereIsNoMinimumButThereIsNoRefundOfNothing() {
        assertThat(service.open(TICKET, GROUP, RefundReason.EXCESS_OR_DUPLICATE_MONEY, "coll-2", 1_000,
                "corr").request().getAmountVnd()).isEqualTo(1_000);
        assertThatThrownBy(() -> service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-9", 0,
                "corr")).isInstanceOf(WorkshopSettlementValidationException.class);
    }

    // ---- the account ----

    @Test
    void theCustomerAnswersTheLinkOnceAndTheNumberIsStoredEncrypted() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        var request = service.submitAccountByLink(opened.rawAccountLinkToken(), "BIDV", "12345678901", "A",
                "corr");

        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_APPROVAL);
        assertThat(request.getRecipientAccountMasked()).isEqualTo("****8901");
        assertThat(request.getRecipientAccountEncrypted()).isNotNull().doesNotContain("12345678901");
        assertThat(request.getAccountEntryChannel()).isEqualTo(RefundAccountEntryChannel.LINK);
        assertThat(service.revealAccountNumber(request.getRequestReference())).isEqualTo("12345678901");
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.USED);

        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), "BIDV", "999", "A", "corr")).isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void aResendRevokesTheOldLink() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        String old = opened.rawAccountLinkToken();

        String fresh = service.resendAccountLink(opened.request().getRequestReference(), STAFF, "corr");

        assertThat(fresh).isNotEqualTo(old);
        assertThat(linkRows).hasSize(2);
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.REVOKED);
        assertThatThrownBy(() -> service.submitAccountByLink(old, "BIDV", "12345678901", "A", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void staffMayEnterTheAccountAtTheCounterAndThatKillsTheLink() {
        var opened = service.open(TICKET, GROUP, RefundReason.OTA_ADDON_OVERCHARGE, "addon-1", 50_000,
                "corr");

        var request = service.submitAccountAtCounter(opened.request().getRequestReference(), "BIDV",
                "12345678901", "A", STAFF, "corr");

        assertThat(request.getAccountEntryChannel()).isEqualTo(RefundAccountEntryChannel.COUNTER);
        assertThat(request.getAccountEnteredByAccountId()).isEqualTo(STAFF);
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.REVOKED);
    }

    @Test
    void anotherHolderNameIsRecordedAndLeftToTheManagerToCheck() {
        // Decision 2026-11-13: no payer name to compare; the approving Manager and the transfer check the holder.
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        var request = service.submitAccountByLink(opened.rawAccountLinkToken(), "BIDV", "12345678901",
                "Tran Thi B", "corr");

        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_APPROVAL);
        assertThat(request.getRecipientHolderName()).isEqualTo("Tran Thi B");
    }

    @Test
    void anIncompleteAccountIsRefused() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), "BIDV", "  ", "A", "corr")).isInstanceOf(WorkshopSettlementValidationException.class);
    }

    // ---- approval, transfer, reconciliation ----

    @Test
    void theMoneyMovesOnlyThroughApprovalTransferAndAManagersConfirmation() {
        var request = awaitingApproval();
        String reference = request.getRequestReference();

        assertThatThrownBy(() -> service.recordTransfer(reference, EXECUTOR, "FT123", null, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);

        service.approve(reference, MANAGER, "Mland huy ca", "corr");
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_TRANSFER);

        service.recordTransfer(reference, EXECUTOR, "FT123", "proof-1", "corr");
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_RECONCILIATION);
        assertThat(request.getBankTransferReference()).isEqualTo("FT123");
        assertThat(request.getTransferredByAccountId()).isEqualTo(EXECUTOR);

        var refunded = service.confirmRefunded(reference, MANAGER, "corr");
        assertThat(refunded.getStatus()).isEqualTo(RefundRequestStatus.REFUNDED);
        assertThat(refunded.getConfirmedByAccountId()).isEqualTo(MANAGER);
        // FR-040: only the masked form survives the refund.
        assertThat(refunded.getRecipientAccountEncrypted()).isNull();
        assertThat(refunded.getRecipientAccountMasked()).isEqualTo("****8901");
    }

    @Test
    void approvalAndTransferBothNeedTheirEvidence() {
        var request = awaitingApproval();
        String reference = request.getRequestReference();

        assertThatThrownBy(() -> service.approve(reference, MANAGER, "  ", "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        service.approve(reference, MANAGER, "Mland huy ca", "corr");
        assertThatThrownBy(() -> service.recordTransfer(reference, EXECUTOR, "  ", null, "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    void aConfirmationIsNotRepeated() {
        var request = awaitingApproval();
        String reference = request.getRequestReference();
        service.approve(reference, MANAGER, "Mland huy ca", "corr");
        service.recordTransfer(reference, EXECUTOR, "FT123", null, "corr");
        service.confirmRefunded(reference, MANAGER, "corr");

        var again = service.confirmRefunded(reference, 99L, "corr");

        assertThat(again.getConfirmedByAccountId()).isEqualTo(MANAGER);
    }

    // ---- unreachable ----

    @Test
    void aRequestIsClosedOnlyAfterThirtyDaysAndCanBeReopened() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        String reference = opened.request().getRequestReference();

        assertThatThrownBy(() -> service.closeUnreachable(reference, MANAGER, "Khong lien lac duoc", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REFUND_NOT_UNREACHABLE_YET");

        opened.request().setCreatedAt(Instant.now().minus(BankRefundService.UNREACHABLE_AFTER).minusSeconds(60));
        var closed = service.closeUnreachable(reference, MANAGER, "Khong lien lac duoc", "corr");
        assertThat(closed.getStatus()).isEqualTo(RefundRequestStatus.CLOSED_UNREACHABLE);
        assertThat(closed.getClosureReason()).isEqualTo("Khong lien lac duoc");
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.REVOKED);

        var reopened = service.reopen(reference, MANAGER, "corr");
        assertThat(reopened.request().getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
        assertThat(reopened.rawAccountLinkToken()).isNotBlank();
        assertThat(reopened.request().getReopenedAt()).isNotNull();
    }

    @Test
    void closingNeedsAReasonAndOnlyAppliesToARequestStillWaiting() {
        var request = awaitingApproval();

        assertThatThrownBy(() -> service.closeUnreachable(request.getRequestReference(), MANAGER, "x", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REFUND_NOT_AWAITING_ACCOUNT");
    }

    // ---- the cipher itself ----

    @Test
    void theStoredAccountNumberIsNeitherReadableNorRepeatable() {
        String first = RefundAccountCipher.encrypt("12345678901");
        String second = RefundAccountCipher.encrypt("12345678901");

        assertThat(first).isNotEqualTo(second); // a fresh nonce each time, so rows cannot be matched
        assertThat(RefundAccountCipher.decrypt(first)).isEqualTo("12345678901");
        assertThat(RefundAccountCipher.decrypt(second)).isEqualTo("12345678901");
        assertThat(RefundAccountCipher.mask("12345678901")).isEqualTo("****8901");
        assertThat(RefundAccountCipher.mask("123")).isEqualTo("****");
    }

    @Test
    void aTamperedRowFailsToDecryptInsteadOfReturningAWrongNumber() {
        String stored = RefundAccountCipher.encrypt("12345678901");
        String tampered = stored.substring(0, stored.length() - 3) + "AAA";

        assertThatThrownBy(() -> RefundAccountCipher.decrypt(tampered))
                .isInstanceOf(RefundAccountCipher.GeneralSecurityFailure.class);
    }

    // ---- helpers ----

    private SettlementRefundRequest awaitingApproval() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        return service.submitAccountByLink(opened.rawAccountLinkToken(), "BIDV", "12345678901", "A",
                "corr");
    }

    private void givenCollected(long amountVnd) {
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET)).thenReturn(List.of(
                SettlementFolioCollection.builder().billReference(TICKET).bookingReference(TICKET)
                        .method(FolioCollectionMethod.VNPAY).amountVnd(amountVnd)
                        .status(FolioCollectionStatus.CONFIRMED).build()));
    }
}
