package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The bank refund of Mland money (003.3 T097, FR-038..FR-040).
 *
 * <p>Mland money goes back only by bank transfer outside the system. This service calculates, records,
 * tracks and reconciles it, and never calls a gateway. The states are
 * {@code PENDING_ACCOUNT â PENDING_APPROVAL â PENDING_TRANSFER â PENDING_RECONCILIATION â REFUNDED}, with
 * {@code CLOSED_UNREACHABLE} for a customer who never answers, which a Manager may reopen.
 *
 * <p>Who does what: a Manager approves the reason, amount and recipient; an actor holding
 * {@code workshop.refund.execute} makes the transfer and records the bank reference and proof; a Manager
 * confirms {@code REFUNDED} after matching the bank statement. One Manager may both approve and confirm, and
 * every step records who and when.
 *
 * <p>The account itself arrives either through a single-use link sent to the payer, valid 7 days, where a
 * resend revokes the old one, or by Staff entry at the counter. The number is encrypted at rest and only its
 * masked form survives the refund.
 *
 * <p>Refunds are per ticket: the money refunded or pending for a ticket never exceeds what was collected for
 * that ticket, and a refund of one ticket leaves the others untouched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankRefundService {

    /** FR-040: an account-collection link lives 7 days. */
    public static final Duration LINK_LIFETIME = Duration.ofDays(7);
    /** FR-040: after this long without a reply a Manager may close the request as unreachable. */
    public static final Duration UNREACHABLE_AFTER = Duration.ofDays(30);

    private final SettlementRefundRequestRepository requests;
    private final SettlementRefundAccountLinkRepository links;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementFinalBillRepository bills;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementBusinessAuditRepository audits;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final SettlementCustomerNotifier notifier;

    /** A newly opened request, with the raw link value when one was created for it. */
    public record OpenedRefund(SettlementRefundRequest request, String rawAccountLinkToken) {
    }

    // ---- opening ----

    /**
     * Opens a refund for one ticket, or returns the existing one for the same money (FR-039).
     */
    @Transactional
    public OpenedRefund open(String bookingReference, String groupReference, RefundReason reason,
                             String sourceReference, long amountVnd,
                             String correlationReference) {
        if (bookingReference == null || bookingReference.isBlank() || sourceReference == null
                || sourceReference.isBlank()) {
            throw new WorkshopSettlementValidationException("REFUND_REFERENCES_REQUIRED");
        }
        if (amountVnd <= 0) {
            // There is no 5,000 VND minimum for a bank refund, but there is no refund of nothing either.
            throw new WorkshopSettlementValidationException("REFUND_AMOUNT_INVALID");
        }
        var existing = requests.findByReasonAndSourceReference(reason, sourceReference);
        if (existing.isPresent()) {
            return new OpenedRefund(existing.get(), null);
        }
        requireWithinCollected(bookingReference, amountVnd);
        return createRequest(bookingReference, groupReference, reason, sourceReference, amountVnd,
                correlationReference);
    }

    /**
     * 003.1 FR-040 (decision 2026-11-13): a 100% bank refund of a late group payment that was never re-confirmed.
     * No ticket was confirmed, so there is no folio: the request is opened for the group itself and capped by the
     * verified late payment that Booking recorded, not by ticket collections. Idempotent per review reference.
     * Returns null, without throwing, when the amount would exceed that payment.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public OpenedRefund openLatePaymentRefund(String groupReference, String reviewReference, long amountVnd,
                                              long verifiedLatePaymentVnd, String correlationReference) {
        if (groupReference == null || groupReference.isBlank() || reviewReference == null || reviewReference.isBlank()) {
            throw new WorkshopSettlementValidationException("REFUND_REFERENCES_REQUIRED");
        }
        if (amountVnd <= 0) {
            throw new WorkshopSettlementValidationException("REFUND_AMOUNT_INVALID");
        }
        var existing = requests.findByReasonAndSourceReference(RefundReason.LATE_PAYMENT_NOT_RECONFIRMED, reviewReference);
        if (existing.isPresent()) {
            return new OpenedRefund(existing.get(), null);
        }
        if (amountVnd > verifiedLatePaymentVnd) {
            return null; // over the verified late payment: the caller refuses without touching the transaction
        }
        return createRequest(groupReference, groupReference, RefundReason.LATE_PAYMENT_NOT_RECONFIRMED,
                reviewReference, amountVnd, correlationReference);
    }

    private OpenedRefund createRequest(String bookingReference, String groupReference, RefundReason reason,
                                       String sourceReference, long amountVnd,
                                       String correlationReference) {
        SettlementRefundRequest request = requests.saveAndFlush(SettlementRefundRequest.builder()
                .requestReference("REFUND-" + UUID.randomUUID())
                .bookingReference(bookingReference.trim())
                .groupReference(groupReference)
                .reason(reason)
                .sourceReference(sourceReference.trim())
                .amountVnd(amountVnd)
                .currencyCode("VND")
                .status(RefundRequestStatus.PENDING_ACCOUNT)
                .build());
        String token = issueLink(request);
        notifier.notifyRefundAccountRequest(request, token, Instant.now().plus(LINK_LIFETIME), false, correlationReference);
        audit(request, "REFUND_REQUEST_OPENED", null, reason.name(), correlationReference);
        log.info("Opened bank refund {} for {} of {} VND", request.getRequestReference(), bookingReference,
                amountVnd);
        return new OpenedRefund(request, token);
    }

    /** FR-040: a resend revokes the previous link, so only the newest one ever works. */
    @Transactional
    public String resendAccountLink(String requestReference, Long actorAccountId, String correlationReference) {
        SettlementRefundRequest request = lock(requestReference);
        if (request.getStatus() != RefundRequestStatus.PENDING_ACCOUNT) {
            throw new WorkshopSettlementConflictException("REFUND_ACCOUNT_ALREADY_ON_FILE");
        }
        String token = issueLink(request);
        notifier.notifyRefundAccountRequest(request, token, Instant.now().plus(LINK_LIFETIME), false, correlationReference);
        audit(request, "REFUND_ACCOUNT_LINK_RESENT", actorAccountId, null, correlationReference);
        return token;
    }

    /**
     * Day-3 / day-7 reminder (template 7, "(nhắc lại)"): a fresh link replaces the previous one, so the reminder
     * always carries a usable link. Runs in the scheduler transaction.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void remindAccountNeeded(SettlementRefundRequest request, String correlationReference) {
        if (request.getStatus() != RefundRequestStatus.PENDING_ACCOUNT) {
            return;
        }
        String token = issueLink(request);
        notifier.notifyRefundAccountRequest(request, token, Instant.now().plus(LINK_LIFETIME), true, correlationReference);
        audit(request, "REFUND_ACCOUNT_REMINDED", null, null, correlationReference);
    }

    // ---- the recipient account ----

    /** The customer answers the link. One use only: the link is spent whether or not it is used again. */
    @Transactional
    public SettlementRefundRequest submitAccountByLink(String rawToken, String bankCode, String accountNumber,
                                                       String holderName, String correlationReference) {
        SettlementRefundAccountLink link = links.findByTokenHash(hash(rawToken))
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        Instant now = Instant.now();
        if (!link.isUsable(now)) {
            throw new WorkshopSettlementConflictException("REFUND_ACCOUNT_LINK_NOT_USABLE");
        }
        SettlementRefundRequest request = requests.findById(link.getRequestId())
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        applyAccount(request, bankCode, accountNumber, holderName,
                RefundAccountEntryChannel.LINK, null, correlationReference);
        link.setLifecycleStatus(RefundAccountLinkStatus.USED);
        link.setUsedAt(now);
        links.saveAndFlush(link);
        return request;
    }

    /** Staff enters the account at the counter, which FR-038a expects for an OTA add-on payer who is there. */
    @Transactional
    public SettlementRefundRequest submitAccountAtCounter(String requestReference, String bankCode,
                                                          String accountNumber, String holderName,
                                                          Long actorAccountId, String correlationReference) {
        SettlementRefundRequest request = lock(requestReference);
        applyAccount(request, bankCode, accountNumber, holderName,
                RefundAccountEntryChannel.COUNTER, actorAccountId, correlationReference);
        revokeActiveLinks(request, Instant.now());
        return request;
    }

    /**
     * The full account number, for the two roles FR-040 allows to see it. Every other reader gets the mask
     * that is stored beside it.
     */
    @Transactional(readOnly = true)
    public String revealAccountNumber(String requestReference) {
        SettlementRefundRequest request = requests.findByRequestReference(requestReference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (request.getRecipientAccountEncrypted() == null) {
            throw new WorkshopSettlementConflictException("REFUND_ACCOUNT_NOT_ON_FILE");
        }
        return RefundAccountCipher.decrypt(request.getRecipientAccountEncrypted());
    }

    // ---- approval, transfer, reconciliation ----

    @Transactional
    public SettlementRefundRequest approve(String requestReference, Long managerAccountId, String reason,
                                           String correlationReference) {
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("REFUND_APPROVAL_REASON_REQUIRED");
        }
        SettlementRefundRequest request = lock(requestReference);
        if (request.getStatus() != RefundRequestStatus.PENDING_APPROVAL) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_AWAITING_APPROVAL");
        }
        request.setApprovedByAccountId(managerAccountId);
        request.setApprovedAt(Instant.now());
        request.setApprovalReason(reason.trim());
        request.setStatus(RefundRequestStatus.PENDING_TRANSFER);
        SettlementRefundRequest saved = requests.saveAndFlush(request);
        audit(saved, "REFUND_APPROVED", managerAccountId, reason.trim(), correlationReference);
        return saved;
    }

    /**
     * The transfer happened outside the system. The actor records the bank reference and the proof; the
     * money is not considered returned until a Manager matches it against the statement.
     */
    @Transactional
    public SettlementRefundRequest recordTransfer(String requestReference, Long actorAccountId,
                                                  String bankTransferReference, String proofReference,
                                                  String correlationReference) {
        if (bankTransferReference == null || bankTransferReference.isBlank()) {
            throw new WorkshopSettlementValidationException("BANK_TRANSFER_REFERENCE_REQUIRED");
        }
        SettlementRefundRequest request = lock(requestReference);
        if (request.getStatus() != RefundRequestStatus.PENDING_TRANSFER) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_AWAITING_TRANSFER");
        }
        request.setTransferredByAccountId(actorAccountId);
        request.setTransferredAt(Instant.now());
        request.setBankTransferReference(bankTransferReference.trim());
        request.setTransferProofReference(proofReference);
        request.setStatus(RefundRequestStatus.PENDING_RECONCILIATION);
        SettlementRefundRequest saved = requests.saveAndFlush(request);
        audit(saved, "REFUND_TRANSFERRED", actorAccountId, bankTransferReference.trim(), correlationReference);
        return saved;
    }

    /**
     * A Manager matched the bank statement. Only now is the money back, and only now do the business
     * consequences follow (FR-039). The full account number is dropped; the mask stays.
     */
    @Transactional
    public SettlementRefundRequest confirmRefunded(String requestReference, Long managerAccountId,
                                                   String correlationReference) {
        SettlementRefundRequest request = lock(requestReference);
        if (request.getStatus() == RefundRequestStatus.REFUNDED) {
            return request; // Confirmations are deduplicated (FR-039).
        }
        if (request.getStatus() != RefundRequestStatus.PENDING_RECONCILIATION) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_AWAITING_RECONCILIATION");
        }
        request.setConfirmedByAccountId(managerAccountId);
        request.setConfirmedAt(Instant.now());
        request.setStatus(RefundRequestStatus.REFUNDED);
        request.setRecipientAccountEncrypted(null);
        SettlementRefundRequest saved = requests.saveAndFlush(request);
        audit(saved, "REFUND_CONFIRMED", managerAccountId, null, correlationReference);
        // Only now may the consequences follow (FR-039): the allocation closes and the folio and booking move.
        events.publishEvent(new edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent(
                saved.getRequestReference(), saved.getBookingReference(), saved.getSourceReference(),
                saved.getAmountVnd(), saved.getConfirmedAt()));
        log.info("Refund {} confirmed against the bank statement", saved.getRequestReference());
        return saved;
    }

    // ---- unreachable customers ----

    /** FR-040: after 30 days without a reply a Manager may close it, with a reason. */
    @Transactional
    public SettlementRefundRequest closeUnreachable(String requestReference, Long managerAccountId, String reason,
                                                    String correlationReference) {
        if (reason == null || reason.isBlank()) {
            throw new WorkshopSettlementValidationException("REFUND_CLOSURE_REASON_REQUIRED");
        }
        SettlementRefundRequest request = lock(requestReference);
        if (request.getStatus() != RefundRequestStatus.PENDING_ACCOUNT) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_AWAITING_ACCOUNT");
        }
        Instant now = Instant.now();
        if (request.getCreatedAt().plus(UNREACHABLE_AFTER).isAfter(now)) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_UNREACHABLE_YET");
        }
        request.setStatus(RefundRequestStatus.CLOSED_UNREACHABLE);
        request.setClosedByAccountId(managerAccountId);
        request.setClosedAt(now);
        request.setClosureReason(reason.trim());
        revokeActiveLinks(request, now);
        SettlementRefundRequest saved = requests.saveAndFlush(request);
        audit(saved, "REFUND_CLOSED_UNREACHABLE", managerAccountId, reason.trim(), correlationReference);
        return saved;
    }

    /** The customer came back. They never lost the right to the refund, so it opens again with a fresh link. */
    @Transactional
    public OpenedRefund reopen(String requestReference, Long managerAccountId, String correlationReference) {
        SettlementRefundRequest request = lock(requestReference);
        if (request.getStatus() != RefundRequestStatus.CLOSED_UNREACHABLE) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_CLOSED");
        }
        request.setStatus(RefundRequestStatus.PENDING_ACCOUNT);
        request.setReopenedAt(Instant.now());
        request.setReminderDay3At(null);
        request.setReminderDay7At(null);
        SettlementRefundRequest saved = requests.saveAndFlush(request);
        String token = issueLink(saved);
        notifier.notifyRefundAccountRequest(saved, token, Instant.now().plus(LINK_LIFETIME), false, correlationReference);
        audit(saved, "REFUND_REOPENED", managerAccountId, null, correlationReference);
        return new OpenedRefund(saved, token);
    }

    @Transactional(readOnly = true)
    public List<SettlementRefundRequest> listForBooking(String bookingReference) {
        return requests.findByBookingReferenceOrderByIdAsc(bookingReference);
    }

    /** FR-039 queue: every request, or only one status, newest first. */
    @Transactional(readOnly = true)
    public List<SettlementRefundRequest> list(RefundRequestStatus status) {
        return status == null ? requests.findByOrderByCreatedAtDesc()
                : requests.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public SettlementRefundRequest require(String requestReference) {
        return requests.findByRequestReference(requestReference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    /**
     * FR-040: the full account number, for the Manager who approves and the actor who transfers. Every read is
     * audited, because this is the one place the number leaves the database in clear.
     */
    @Transactional
    public String revealAccountNumber(String requestReference, Long actorAccountId, String correlationReference) {
        SettlementRefundRequest request = requests.findByRequestReference(requestReference)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (request.getRecipientAccountEncrypted() == null) {
            throw new WorkshopSettlementConflictException("REFUND_ACCOUNT_NOT_ON_FILE");
        }
        audit(request, "REFUND_ACCOUNT_REVEALED", actorAccountId, null, correlationReference);
        return RefundAccountCipher.decrypt(request.getRecipientAccountEncrypted());
    }

    // ---- internals ----

    private void applyAccount(SettlementRefundRequest request, String bankCode, String accountNumber,
                              String holderName,
                              RefundAccountEntryChannel channel, Long actorAccountId,
                              String correlationReference) {
        if (request.getStatus() != RefundRequestStatus.PENDING_ACCOUNT) {
            throw new WorkshopSettlementConflictException("REFUND_NOT_AWAITING_ACCOUNT");
        }
        if (bankCode == null || bankCode.isBlank() || accountNumber == null || accountNumber.isBlank()
                || holderName == null || holderName.isBlank()) {
            throw new WorkshopSettlementValidationException("REFUND_ACCOUNT_INCOMPLETE");
        }
        request.setRecipientBankCode(bankCode.trim());
        request.setRecipientAccountEncrypted(RefundAccountCipher.encrypt(accountNumber.trim()));
        request.setRecipientAccountMasked(RefundAccountCipher.mask(accountNumber.trim()));
        request.setRecipientHolderName(holderName.trim());
        request.setAccountEntryChannel(channel);
        request.setAccountEnteredByAccountId(actorAccountId);
        request.setAccountEnteredAt(Instant.now());
        request.setStatus(RefundRequestStatus.PENDING_APPROVAL);
        requests.saveAndFlush(request);
        // The number itself never reaches an audit row; only its mask does.
        audit(request, "REFUND_ACCOUNT_RECORDED", actorAccountId, channel.name(), correlationReference);
    }

    /** FR-038: the money refunded or pending for one ticket never exceeds what was collected for it. */
    private void requireWithinCollected(String bookingReference, long amountVnd) {
        long counterCollections = collections.findByBookingReferenceOrderByIdAsc(bookingReference).stream()
                .filter(collection -> collection.getStatus() == FolioCollectionStatus.CONFIRMED)
                .mapToLong(collection -> collection.getAmountVnd())
                .sum();
        long bookingTimeCollection = bills.findByBookingReference(bookingReference)
                .map(SettlementFinalBill::getAcceptedDepositAmountVnd)
                .orElse(0L);
        long verifiedGatewayCollections = java.util.stream.Stream.of("FINAL_PAYMENT_VERIFIED", "SHOP_ADDON_VERIFIED")
                .flatMap(conclusion -> evidence.findByBookingReferenceAndConclusion(bookingReference, conclusion).stream())
                .mapToLong(item -> item.getAmountVnd() == null ? 0L : item.getAmountVnd())
                .sum();
        long collected = Math.addExact(bookingTimeCollection,
                Math.addExact(verifiedGatewayCollections, counterCollections));
        long alreadyOwed = requests.sumRefundedOrPending(bookingReference);
        if (Math.addExact(alreadyOwed, amountVnd) > collected) {
            throw new WorkshopSettlementConflictException("REFUND_EXCEEDS_COLLECTED_FOR_TICKET");
        }
    }

    private String issueLink(SettlementRefundRequest request) {
        Instant now = Instant.now();
        revokeActiveLinks(request, now);
        String rawToken = UUID.randomUUID().toString();
        links.saveAndFlush(SettlementRefundAccountLink.builder()
                .requestId(request.getId())
                .tokenHash(hash(rawToken))
                .lifecycleStatus(RefundAccountLinkStatus.ACTIVE)
                .expiresAt(now.plus(LINK_LIFETIME))
                .build());
        return rawToken;
    }

    private void revokeActiveLinks(SettlementRefundRequest request, Instant now) {
        for (SettlementRefundAccountLink link : links.findByRequestIdAndLifecycleStatus(request.getId(),
                RefundAccountLinkStatus.ACTIVE)) {
            link.setLifecycleStatus(RefundAccountLinkStatus.REVOKED);
            link.setRevokedAt(now);
            links.saveAndFlush(link);
        }
    }

    private SettlementRefundRequest lock(String requestReference) {
        if (requestReference == null || requestReference.isBlank()) {
            throw new WorkshopSettlementValidationException("REFUND_REFERENCE_REQUIRED");
        }
        return requests.findForUpdate(requestReference.trim())
                .orElseThrow(WorkshopSettlementNotFoundException::new);
    }

    private void audit(SettlementRefundRequest request, String action, Long actorAccountId, String reason,
                       String correlationReference) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_REFUND_REQUEST")
                .aggregateReference(request.getRequestReference())
                .actionCode(action)
                .actorReference(actorAccountId == null ? "SYSTEM" : "account:" + actorAccountId)
                .correlationReference(correlationReference == null || correlationReference.isBlank()
                        ? "CORR-" + UUID.randomUUID() : correlationReference)
                .evidenceReference(request.getBankTransferReference())
                .reasonReference(reason)
                .createdAt(Instant.now())
                .build());
    }

    static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new WorkshopSettlementValidationException("REFUND_ACCOUNT_LINK_REQUIRED");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
