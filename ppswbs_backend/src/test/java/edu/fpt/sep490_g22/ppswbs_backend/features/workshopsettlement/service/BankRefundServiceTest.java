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
    private SettlementBusinessAuditRepository audits;
    private SettlementRefundAccountLinkRepository links;
    private SettlementRefundLinkSubmissionRepository linkSubmissions;
    private SettlementFolioCollectionRepository collections;
    private SettlementFinalBillRepository bills;
    private SettlementEvidenceConclusionRepository evidence;
    private BankRefundService service;
    private final SettlementCustomerNotifier notifier = mock(SettlementCustomerNotifier.class);
    private final List<SettlementRefundRequest> requestRows = new ArrayList<>();
    private final List<SettlementRefundAccountLink> linkRows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private final List<SettlementRefundLinkSubmission> submissionRows = new ArrayList<>();
    private final AtomicLong keys = new AtomicLong(1);

    private String nextKey() {
        return "idem-key-%08d".formatted(keys.getAndIncrement());
    }

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
        audits = mock(SettlementBusinessAuditRepository.class);
        requestRows.clear();
        linkRows.clear();
        submissionRows.clear();
        linkSubmissions = mock(SettlementRefundLinkSubmissionRepository.class);
        when(linkSubmissions.saveAndFlush(any())).thenAnswer(invocation -> {
            SettlementRefundLinkSubmission row = invocation.getArgument(0);
            submissionRows.add(row);
            return row;
        });
        when(linkSubmissions.findByTokenHashAndIdempotencyKey(anyString(), anyString())).thenAnswer(invocation ->
                submissionRows.stream().filter(row -> row.getTokenHash().equals(invocation.getArgument(0))
                        && row.getIdempotencyKey().equals(invocation.getArgument(1))).findFirst());

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
        when(requests.findByBookingReferenceOrderByIdAsc(anyString())).thenAnswer(invocation -> requestRows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getBookingReference())).toList());
        when(requests.findById(any())).thenAnswer(invocation ->
                requestRows.stream().filter(row -> invocation.getArgument(0).equals(row.getId())).findFirst());
        when(requests.sumRefundedOrPending(anyString())).thenAnswer(invocation -> requestRows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getBookingReference()))
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
                mock(org.springframework.context.ApplicationEventPublisher.class), notifier, linkSubmissions);
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

        var request = service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901", "A",
                true, "corr");

        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_APPROVAL);
        assertThat(request.getRecipientAccountMasked()).isEqualTo("****8901");
        assertThat(request.getRecipientAccountEncrypted()).isNotNull().doesNotContain("12345678901");
        assertThat(request.getAccountEntryChannel()).isEqualTo(RefundAccountEntryChannel.LINK);
        assertThat(service.revealAccountNumber(request.getRequestReference())).isEqualTo("12345678901");
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.USED);

        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "999", "A", true, "corr")).isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void aResendRevokesTheOldLink() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        String old = opened.rawAccountLinkToken();

        String fresh = service.resendAccountLink(opened.request().getRequestReference(), STAFF, "corr");

        assertThat(fresh).isNotEqualTo(old);
        assertThat(linkRows).hasSize(2);
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.REVOKED);
        assertThatThrownBy(() -> service.submitAccountByLink(old, nextKey(), "BIDV", "12345678901", "A", true, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void staffMayEnterTheAccountAtTheCounterAndThatKillsTheLink() {
        var opened = service.open(TICKET, GROUP, RefundReason.OVERCHARGE_AFTER_FINALIZED, "correction-1", 50_000,
                "corr");

        var request = service.submitAccountAtCounter(opened.request().getRequestReference(), "BIDV",
                "12345678901", "A", true, STAFF, "corr");

        assertThat(request.getAccountEntryChannel()).isEqualTo(RefundAccountEntryChannel.COUNTER);
        assertThat(request.getAccountEnteredByAccountId()).isEqualTo(STAFF);
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.REVOKED);
    }

    @Test
    void anotherHolderNameIsRecordedAndLeftToTheManagerToCheck() {
        // Decision 2026-11-13: no payer name to compare; the approving Manager and the transfer check the holder.
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        var request = service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901",
                "Tran Thi B", true, "corr");

        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_APPROVAL);
        assertThat(request.getRecipientHolderName()).isEqualTo("Tran Thi B");
    }

    @Test
    void anIncompleteAccountIsRefused() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "  ", "A", true, "corr")).isInstanceOf(WorkshopSettlementValidationException.class);
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

    // ---- T146: RETURN_TO_PENDING_ACCOUNT ----

    @Test
    void everyFailingSourceReturnsToPendingAccountWithoutTheNumberOrTheApproval() {
        // A bank return (PENDING_RECONCILIATION), a transfer-stage refusal and an approval-stage refusal.
        for (RefundRequestStatus stage : List.of(RefundRequestStatus.PENDING_APPROVAL,
                RefundRequestStatus.PENDING_TRANSFER, RefundRequestStatus.PENDING_RECONCILIATION)) {
            var opened = service.open(TICKET, GROUP, RefundReason.EXCESS_OR_DUPLICATE_MONEY, "src-" + stage, 10_000, "corr");
            var request = service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901", "A", true, "corr");
            String reference = request.getRequestReference();
            if (stage != RefundRequestStatus.PENDING_APPROVAL) service.approve(reference, MANAGER, "ok", "corr");
            if (stage == RefundRequestStatus.PENDING_RECONCILIATION) service.recordTransfer(reference, EXECUTOR, "FT1", null, "corr");
            long amount = request.getAmountVnd();

            var returned = service.returnToPendingAccount(reference, MANAGER, "Ngan hang tra lai", "corr");

            assertThat(returned.request().getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
            assertThat(returned.request().getRecipientAccountEncrypted()).isNull();
            assertThat(returned.request().getRecipientAccountMasked()).isEqualTo("****8901");
            assertThat(returned.request().getApprovedByAccountId()).isNull();
            assertThat(returned.request().getApprovedAt()).isNull();
            assertThat(returned.request().getBankTransferReference()).isNull();
            assertThat(returned.request().getAmountVnd()).isEqualTo(amount);
            assertThat(returned.rawAccountLinkToken()).isNotBlank();
            assertThatThrownBy(() -> service.recordTransfer(reference, EXECUTOR, "FT2", null, "corr"))
                    .isInstanceOf(WorkshopSettlementConflictException.class);
        }
        // 3 requests: one link email at opening and one more after each return.
        verify(notifier, times(6)).notifyRefundAccountRequest(any(), anyString(), any(), eq(false), eq("corr"));
    }

    @Test
    void aManagerMustApproveAgainAfterTheReturnAndTheNewAccount() {
        var request = awaitingApproval();
        String reference = request.getRequestReference();
        service.approve(reference, MANAGER, "ok", "corr");

        var returned = service.returnToPendingAccount(reference, MANAGER, "Sai so tai khoan", "corr");
        assertThatThrownBy(() -> service.approve(reference, MANAGER, "again", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REFUND_NOT_AWAITING_APPROVAL");

        service.submitAccountByLink(returned.rawAccountLinkToken(), nextKey(), "VCB", "99887766554", "B", true, "corr");
        service.approve(reference, MANAGER, "again", "corr");
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_TRANSFER);
        assertThat(request.getApprovedAt()).isNotNull();
    }

    @Test
    void theReturnNeedsAReasonAndAReplayChangesNothing() {
        var request = awaitingApproval();
        String reference = request.getRequestReference();
        assertThatThrownBy(() -> service.returnToPendingAccount(reference, MANAGER, "  ", "corr"))
                .isInstanceOf(WorkshopSettlementValidationException.class);

        service.returnToPendingAccount(reference, MANAGER, "Sai so", "corr");
        int links = linkRows.size();

        assertThatThrownBy(() -> service.returnToPendingAccount(reference, MANAGER, "Sai so", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REFUND_NOT_RETURNABLE");
        assertThat(linkRows).hasSize(links);
    }

    @Test
    void aLateReturnAfterRefundedAnswers409AndChangesNoData() {
        var request = awaitingApproval();
        String reference = request.getRequestReference();
        service.approve(reference, MANAGER, "ok", "corr");
        service.recordTransfer(reference, EXECUTOR, "FT123", null, "corr");
        service.confirmRefunded(reference, MANAGER, "corr");
        int links = linkRows.size();

        assertThatThrownBy(() -> service.returnToPendingAccount(reference, MANAGER, "Tra lai muon", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("REFUND_NOT_RETURNABLE");

        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REFUNDED);
        assertThat(request.getConfirmedByAccountId()).isEqualTo(MANAGER);
        assertThat(request.getRecipientAccountMasked()).isEqualTo("****8901");
        assertThat(linkRows).hasSize(links);
    }

    // ---- T170: the ticket refund cap across reasons ----

    @Test
    void aMlandCancellationRefundsWhatIsLeftAfterAnotherPendingRequest() {
        service.open(TICKET, GROUP, RefundReason.EXCESS_OR_DUPLICATE_MONEY, "coll-1", 80_000, "corr");

        var opened = service.openRemainder(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", "corr");

        assertThat(opened.request().getAmountVnd()).isEqualTo(420_000);
        assertThat(opened.request().getReason()).isEqualTo(RefundReason.MLAND_CANCELLATION);
        // Both the explicit request and the remainder are read under the ticket folio lock.
        verify(bills, times(2)).findByBookingReferenceForUpdate(TICKET);
        // The money is now fully spoken for: nothing more can be opened for this ticket.
        assertThatThrownBy(() -> service.open(TICKET, GROUP, RefundReason.OVERCHARGE_AFTER_FINALIZED, "corr-1", 1, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("REFUND_EXCEEDS_COLLECTED_FOR_TICKET");
    }

    @Test
    void aRemainderOfZeroCreatesNothingAndReturnsTheExistingRequest() {
        var first = service.openRemainder(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", "corr");
        assertThat(first.request().getAmountVnd()).isEqualTo(500_000);

        var again = service.openRemainder(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-2", "corr");

        assertThat(again.request()).isSameAs(first.request());
        assertThat(again.rawAccountLinkToken()).isNull();
        assertThat(requestRows).hasSize(1);
    }

    @Test
    void aClosedUnreachableRequestStillCountsAsOpenAndNothingCollectedOpensNothing() {
        var first = service.openRemainder(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", "corr");
        first.request().setStatus(RefundRequestStatus.CLOSED_UNREACHABLE);

        var again = service.openRemainder(TICKET, GROUP, RefundReason.EXCESS_OR_DUPLICATE_MONEY, "coll-9", "corr");

        assertThat(again.request()).isSameAs(first.request());
        assertThat(requestRows).hasSize(1);
        givenCollected(0);
        assertThat(service.openRemainder("GRP-OTHER-L0-T9", GROUP, RefundReason.MLAND_CANCELLATION, "case-3", "corr")).isNull();
    }

    // ---- T137 (+T148): key replacement ----

    @Test
    void aKeyReplacementReturnsEveryRequestHoldingACipherAndALaterRunChangesNothing() {
        var approved = awaitingApproval();
        service.approve(approved.getRequestReference(), MANAGER, "ok", "corr");
        var waiting = awaitingApproval2();
        when(requests.findReferencesHoldingAccountNumber()).thenAnswer(invocation -> requestRows.stream()
                .filter(row -> row.getRecipientAccountEncrypted() != null).map(SettlementRefundRequest::getRequestReference).toList());
        int linksBefore = linkRows.size();

        var result = new RefundAccountKeyReplacementService(service).replace(99L, "Khoa bi lo", "corr-key");

        assertThat(result.returnedToPendingAccount()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        for (var request : List.of(approved, waiting)) {
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
            assertThat(request.getRecipientAccountEncrypted()).isNull();
            assertThat(request.getRecipientAccountMasked()).isEqualTo("****8901");
            assertThat(request.getApprovedByAccountId()).isNull();
            assertThat(request.getApprovedAt()).isNull();
        }
        // The old links were revoked and exactly one new link per request was issued and sent.
        assertThat(linkRows).hasSize(linksBefore + 2);
        assertThat(linkRows.stream().filter(link -> link.getLifecycleStatus() == RefundAccountLinkStatus.ACTIVE)).hasSize(2);
        verify(audits, times(2)).save(argThat(a -> a.getActionCode().equals("REFUND_ACCOUNT_KEY_REPLACED")
                && a.getReasonReference().equals("Khoa bi lo")));
        verify(audits, times(2)).save(argThat(a -> a.getActionCode().equals("REFUND_ACCOUNT_LINK_RESENT")));

        // A rerun touches only the requests still holding a ciphertext: none.
        int linksAfter = linkRows.size();
        var rerun = new RefundAccountKeyReplacementService(service).replace(99L, "Khoa bi lo", "corr-key");
        assertThat(rerun.returnedToPendingAccount()).isZero();
        assertThat(linkRows).hasSize(linksAfter);

        // A Manager approves again only after the payer enters the new account.
        assertThatThrownBy(() -> service.approve(approved.getRequestReference(), MANAGER, "again", "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    // ---- T165, T169: the account link route ----

    @Test
    void aLostResponseRetryWithTheSameKeyAndContentReplaysWithoutASecondWriteOrEmail() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        String key = nextKey();

        var first = service.submitAccountByLink(opened.rawAccountLinkToken(), key, "BIDV", "12345678901", "A", true, "corr");
        String encrypted = first.getRecipientAccountEncrypted();
        var replay = service.submitAccountByLink(opened.rawAccountLinkToken(), key, "BIDV", "12345678901", "A", true, "corr");

        assertThat(replay).isSameAs(first);
        assertThat(replay.getRecipientAccountEncrypted()).isEqualTo(encrypted);
        assertThat(submissionRows).hasSize(1);
        // The record holds a keyed fingerprint only: neither the number, the name nor the token.
        assertThat(submissionRows.get(0).getRequestFingerprint()).hasSize(64)
                .doesNotContain("12345678901").doesNotContain(opened.rawAccountLinkToken());
        assertThat(submissionRows.get(0).getTokenHash()).isNotEqualTo(opened.rawAccountLinkToken());
        assertThat(submissionRows.get(0).getRetainUntil())
                .isEqualTo(linkRows.get(0).getExpiresAt().plus(BankRefundService.LINK_SUBMISSION_RETENTION));
        verify(notifier, times(1)).notifyRefundAccountRequest(any(), anyString(), any(), anyBoolean(), anyString());
    }

    @Test
    void changedContentUnderTheSameKeyIsAConflictAndANewKeyOnASpentLinkIsGone() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        String key = nextKey();
        service.submitAccountByLink(opened.rawAccountLinkToken(), key, "BIDV", "12345678901", "A", true, "corr");

        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), key, "BIDV", "999", "A", true, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901", "A", true, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("REFUND_ACCOUNT_LINK_NOT_USABLE");
        assertThat(submissionRows).hasSize(1);
    }

    @Test
    void anUnknownTokenIsNotFoundAndARevokedOrExpiredLinkIsNotUsableAndNothingIsWritten() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        String oldToken = opened.rawAccountLinkToken();

        assertThatThrownBy(() -> service.submitAccountByLink("no-such-token", nextKey(), "BIDV", "1", "A", true, "corr"))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);

        String freshToken = service.resendAccountLink(opened.request().getRequestReference(), STAFF, "corr"); // revokes the old link
        assertThatThrownBy(() -> service.submitAccountByLink(oldToken, nextKey(), "BIDV", "1", "A", true, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("REFUND_ACCOUNT_LINK_NOT_USABLE");

        linkRows.get(1).setExpiresAt(Instant.now().minusSeconds(1)); // the active link expires
        assertThatThrownBy(() -> service.submitAccountByLink(freshToken, nextKey(), "BIDV", "1", "A", true, "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("REFUND_ACCOUNT_LINK_NOT_USABLE");
        assertThat(opened.request().getRecipientAccountEncrypted()).isNull();
        assertThat(opened.request().getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
        assertThat(submissionRows).isEmpty();
    }

    @Test
    void aMissingKeyIs422AndTheRefusedConfirmationLeavesTheLinkAndTheRecordUntouched() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");

        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), null, "BIDV", "12345678901", "A", true, "corr"))
                .isInstanceOf(SettlementUnprocessableException.class).hasMessage("IDEMPOTENCY_KEY_REQUIRED");
        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901", "A", false, "corr"))
                .isInstanceOf(SettlementUnprocessableException.class).hasMessage("ACCOUNT_CONFIRMATION_REQUIRED");

        assertThat(submissionRows).isEmpty();
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.ACTIVE);
    }

    // ---- T150: the entry confirmation ----

    @Test
    void anAccountWithoutTheConfirmationIsRefusedOnBothEntryChannelsAndNothingIsWritten() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        String reference = opened.request().getRequestReference();

        assertThatThrownBy(() -> service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901", "A",
                false, "corr"))
                .isInstanceOf(SettlementUnprocessableException.class)
                .hasMessage("ACCOUNT_CONFIRMATION_REQUIRED");
        assertThatThrownBy(() -> service.submitAccountAtCounter(reference, "BIDV", "12345678901", "A", false, STAFF,
                "corr"))
                .isInstanceOf(SettlementUnprocessableException.class)
                .hasMessage("ACCOUNT_CONFIRMATION_REQUIRED");

        assertThat(opened.request().getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
        assertThat(opened.request().getRecipientAccountEncrypted()).isNull();
        // The link was not spent by the refused attempt.
        assertThat(linkRows.get(0).getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.ACTIVE);
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

    private SettlementRefundRequest awaitingApproval2() {
        var opened = service.open(TICKET, GROUP, RefundReason.EXCESS_OR_DUPLICATE_MONEY, "coll-key", 5_000, "corr");
        return service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901", "A", true, "corr");
    }

    private SettlementRefundRequest awaitingApproval() {
        var opened = service.open(TICKET, GROUP, RefundReason.MLAND_CANCELLATION, "case-1", 200_000, "corr");
        return service.submitAccountByLink(opened.rawAccountLinkToken(), nextKey(), "BIDV", "12345678901", "A",
                true, "corr");
    }

    private void givenCollected(long amountVnd) {
        when(collections.findByBookingReferenceOrderByIdAsc(TICKET)).thenReturn(List.of(
                SettlementFolioCollection.builder().billReference(TICKET).bookingReference(TICKET)
                        .method(FolioCollectionMethod.VNPAY).amountVnd(amountVnd)
                        .status(FolioCollectionStatus.CONFIRMED).build()));
    }
}
