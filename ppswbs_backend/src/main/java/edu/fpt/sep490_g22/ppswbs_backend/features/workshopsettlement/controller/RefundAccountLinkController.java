package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 003.3 FR-040: the customer submits the refund account through the single-use 7-day link from email 7. The
 * token is the credential (no sign-in). The response never echoes the account number. Contract:
 * {@code POST /api/v1/refund-account-links/{token}} → 204, 404 unknown link, 410 used, revoked or expired.
 */
@RestController
@RequiredArgsConstructor
public class RefundAccountLinkController {

    private final BankRefundService bankRefunds;

    public record RefundAccountRequest(
            @NotBlank @Size(max = 32) String bankCode,
            @NotBlank @Size(max = 32) String accountNumber,
            @NotBlank @Size(max = 128) String holderName
    ) {
    }

    @PostMapping("/api/v1/refund-account-links/{token}")
    public ResponseEntity<?> submitAccount(
            @PathVariable String token,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody RefundAccountRequest request) {
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        try {
            bankRefunds.submitAccountByLink(token, request.bankCode(), request.accountNumber(), request.holderName(),
                    correlation);
        } catch (WorkshopSettlementConflictException conflict) {
            if ("REFUND_ACCOUNT_LINK_NOT_USABLE".equals(conflict.getMessage())
                    || "REFUND_NOT_AWAITING_ACCOUNT".equals(conflict.getMessage())) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(new SettlementProblem("REFUND_ACCOUNT_LINK_NOT_USABLE", correlation, List.of()));
            }
            throw conflict;
        }
        return ResponseEntity.noContent().build();
    }
}
