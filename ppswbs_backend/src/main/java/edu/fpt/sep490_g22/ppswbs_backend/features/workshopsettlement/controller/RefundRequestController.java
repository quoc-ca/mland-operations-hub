package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 003.3 FR-039/FR-040 (T097 HTTP routes): the bank-refund queue and its lifecycle, the counter entry of a
 * recipient account, a fresh account link and the one audited read of the full account number.
 *
 * <p>Every transition is Manager-only in V1: the codebase has roles, not named permissions, so a Manager is
 * the actor who holds {@code workshop.refund.execute} until it can be granted per person
 * (owner decision 2026-11-13).
 */
@RestController
public class RefundRequestController {

    private final BankRefundService bankRefunds;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementIdempotencyService idempotency;

    @Autowired
    public RefundRequestController(BankRefundService bankRefunds,
                                   WorkshopSettlementAuthorizationService authorization,
                                   @Autowired(required = false) SettlementIdempotencyService idempotency) {
        this.bankRefunds = bankRefunds;
        this.authorization = authorization;
        this.idempotency = idempotency;
    }

    public RefundRequestController(BankRefundService bankRefunds,
                                   WorkshopSettlementAuthorizationService authorization) {
        this(bankRefunds, authorization, null);
    }

    public enum RefundTransition {APPROVE, RECORD_TRANSFER, CONFIRM_REFUNDED, CLOSE_UNREACHABLE, REOPEN}

    public record TransitionRequest(
            @NotNull RefundTransition action,
            @Size(max = 128) String bankTransactionReference,
            @Size(max = 256) String proofReference,
            @Size(max = 1000) String reason
    ) {
    }

    public record CounterAccountRequest(
            @NotBlank @Size(max = 32) String bankCode,
            @NotBlank @Size(max = 32) String accountNumber,
            @NotBlank @Size(max = 128) String holderName
    ) {
    }

    /**
     * The refund as a reader may see it. {@code ticketCode} is null for a group-level refund (a late payment
     * that was never re-confirmed has no confirmed ticket). The account number is only ever masked here.
     */
    public record RefundRequestResponse(
            String refundId,
            String ticketCode,
            String groupCode,
            String source,
            long amount,
            String status,
            String accountNumberMasked,
            String holderName
    ) {
        static RefundRequestResponse of(SettlementRefundRequest request) {
            String group = request.getGroupReference();
            boolean groupLevel = group != null && group.equals(request.getBookingReference());
            return new RefundRequestResponse(
                    request.getRequestReference(),
                    groupLevel ? null : request.getBookingReference(),
                    group,
                    request.getReason().name(),
                    request.getAmountVnd(),
                    request.getStatus().name(),
                    request.getRecipientAccountMasked(),
                    request.getRecipientHolderName());
        }
    }

    @GetMapping("/api/v1/workshop-refund-requests")
    public List<RefundRequestResponse> list(
            @RequestParam(required = false) RefundRequestStatus status,
            Authentication authentication) {
        authorization.requireManager(actor(authentication));
        return bankRefunds.list(status).stream().map(RefundRequestResponse::of).toList();
    }

    @PostMapping("/api/v1/workshop-refund-requests/{refundId}/transitions")
    public RefundRequestResponse applyTransition(
            @PathVariable String refundId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody TransitionRequest request,
            Authentication authentication) {
        CurrentAccountActor actor = authorization.requireManager(actor(authentication));
        String correlation = correlation(correlationId);
        SettlementRefundRequest result = switch (request.action()) {
            case APPROVE -> bankRefunds.approve(refundId, actor.accountId(), request.reason(), correlation);
            case RECORD_TRANSFER -> {
                if (request.proofReference() == null || request.proofReference().isBlank()) {
                    throw new WorkshopSettlementValidationException("TRANSFER_PROOF_REQUIRED");
                }
                yield bankRefunds.recordTransfer(refundId, actor.accountId(), request.bankTransactionReference(),
                        request.proofReference(), correlation);
            }
            case CONFIRM_REFUNDED -> bankRefunds.confirmRefunded(refundId, actor.accountId(), correlation);
            case CLOSE_UNREACHABLE -> bankRefunds.closeUnreachable(refundId, actor.accountId(), request.reason(),
                    correlation);
            case REOPEN -> bankRefunds.reopen(refundId, actor.accountId(), correlation).request();
        };
        return RefundRequestResponse.of(result);
    }

    /** FR-038a, FR-022d: Staff enters account at the counter; Idempotency-Key required. */
    @PutMapping("/api/v1/workshop-refund-requests/{refundId}/account")
    public RefundRequestResponse recordAccountAtCounter(
            @PathVariable String refundId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CounterAccountRequest request,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        CurrentAccountActor actor = authorization.requireManagerOrAnyStaff(actor(authentication));
        String correlation = correlation(correlationId);
        if (idempotency == null) {
            return RefundRequestResponse.of(bankRefunds.submitAccountAtCounter(refundId, request.bankCode(),
                    request.accountNumber(), request.holderName(), actor.accountId(), correlation));
        }
        String opRef = "REFUND-ACC-" + digest(refundId + "|" + idempotencyKey.trim());
        String fingerprint = digest(refundId + "|" + request.bankCode().trim() + "|"
                + request.accountNumber().trim() + "|" + request.holderName().trim());
        SettlementIdempotencyResult result = idempotency.execute(opRef, fingerprint, () -> {
            SettlementRefundRequest submitted = bankRefunds.submitAccountAtCounter(refundId, request.bankCode(),
                    request.accountNumber(), request.holderName(), actor.accountId(), correlation);
            return submitted.getRequestReference();
        });
        return RefundRequestResponse.of(bankRefunds.require(result.outcomeReference()));
    }

    /** FR-040, FR-022d: a resend revokes the previous link; Idempotency-Key required. Replay returns 204. */
    @PostMapping("/api/v1/workshop-refund-requests/{refundId}/account-link-resends")
    public ResponseEntity<Void> resendAccountLink(
            @PathVariable String refundId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            Authentication authentication) {
        requireIdempotencyKey(idempotencyKey);
        CurrentAccountActor actor = authorization.requireManagerOrAnyStaff(actor(authentication));
        String correlation = correlation(correlationId);
        if (idempotency == null) {
            bankRefunds.resendAccountLink(refundId, actor.accountId(), correlation);
            return ResponseEntity.noContent().build();
        }
        String opRef = "REFUND-RESEND-" + digest(refundId + "|" + idempotencyKey.trim());
        String fingerprint = digest(refundId);
        idempotency.execute(opRef, fingerprint, () -> {
            bankRefunds.resendAccountLink(refundId, actor.accountId(), correlation);
            return refundId;
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * FR-040: the full account number for the Manager who approves and transfers. Audited on every read and
     * never cached, so it does not survive in a browser or proxy.
     */
    @GetMapping("/api/v1/workshop-refund-requests/{refundId}/account-number")
    public ResponseEntity<AccountNumberResponse> revealAccountNumber(
            @PathVariable String refundId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            Authentication authentication) {
        CurrentAccountActor actor = authorization.requireManager(actor(authentication));
        String accountNumber = bankRefunds.revealAccountNumber(refundId, actor.accountId(), correlation(correlationId));
        SettlementRefundRequest request = bankRefunds.require(refundId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AccountNumberResponse(refundId, request.getRecipientBankCode(), accountNumber,
                        request.getRecipientHolderName()));
    }

    public record AccountNumberResponse(String refundId, String bankCode, String accountNumber, String holderName) {
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String correlation(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId.trim();
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
