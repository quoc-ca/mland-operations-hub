package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountKeyReplacementService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * T137 (FR-040, decided 2026-11-15): {@code POST /api/v1/workshop-refund-account-key-replacements}. Admin only: an
 * active account with the canonical Admin role, no display role and no Firebase claim. Manager and Staff get 403.
 * A reason and an Idempotency-Key are required; a replay returns the stored result and touches nothing.
 */
@RestController
@RequiredArgsConstructor
public class RefundAccountKeyReplacementController {

    private final RefundAccountKeyReplacementService replacement;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementIdempotencyService idempotency;

    public record KeyReplacementRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    /** Contract fields: requestsReset and linksSent (one new link per request returned) and the failures, if any. */
    public record KeyReplacementResponse(int requestsReset, int linksSent, int failed) {
    }

    @PostMapping("/api/v1/workshop-refund-account-key-replacements")
    public KeyReplacementResponse replace(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody KeyReplacementRequest request,
            Authentication authentication) {
        CurrentAccountActor actor = authorization.requireManager(actor(authentication));
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        String operation = "REFUND-KEY-REPLACE-" + digest(idempotencyKey.trim());
        String fingerprint = digest(request.reason().trim());
        var outcome = idempotency.execute(operation, fingerprint, () -> {
            var result = replacement.replace(actor.accountId(), request.reason(), correlation);
            return "returned=" + result.returnedToPendingAccount() + ";failed=" + result.failed();
        });
        String[] parts = outcome.outcomeReference().split(";");
        int returned = number(parts[0]);
        return new KeyReplacementResponse(returned, returned, number(parts[1]));
    }

    private static int number(String pair) {
        return Integer.parseInt(pair.substring(pair.indexOf('=') + 1));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
