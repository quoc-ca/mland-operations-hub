package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundReceiptImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

/**
 * 003.3 T154 (FR-039, CHK007, plan O-1): the optional receipt image of a recorded bank transfer. Manager only in V1
 * (Staff and every other role are refused before any storage call). The image is stored as an authenticated
 * Cloudinary object and read only through this backend; no response carries a provider URL.
 */
@RestController
public class RefundReceiptImageController {

    private final RefundReceiptImageService receiptImages;
    private final WorkshopSettlementAuthorizationService authorization;

    @Autowired
    public RefundReceiptImageController(RefundReceiptImageService receiptImages,
                                        WorkshopSettlementAuthorizationService authorization) {
        this.receiptImages = receiptImages;
        this.authorization = authorization;
    }

    public record ReceiptImageResponse(String receiptImageId, String contentType, long byteCount, String checksum,
                                       Instant uploadedAt) {
    }

    @PostMapping(value = "/api/v1/workshop-refund-requests/{refundId}/receipt-images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptImageResponse> upload(
            @PathVariable String refundId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        CurrentAccountActor actor = authorization.requireManager(actor(authentication));
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException unreadable) {
            throw new SettlementReceiptException(SettlementReceiptException.Failure.TYPE_UNSUPPORTED);
        }
        RefundReceiptImageService.ReceiptImageView stored = receiptImages.upload(refundId, actor.accountId(),
                idempotencyKey, file.getContentType(), bytes);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ReceiptImageResponse(stored.receiptImageId(), stored.contentType(), stored.byteCount(),
                        stored.checksum(), stored.uploadedAt()));
    }

    @GetMapping("/api/v1/workshop-refund-requests/{refundId}/receipt-image")
    public ResponseEntity<byte[]> read(@PathVariable String refundId, Authentication authentication) {
        authorization.requireManager(actor(authentication));
        RefundReceiptImageService.ReceiptContent content = receiptImages.read(refundId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(content.bytes());
    }

    private static CurrentAccountActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved) {
            return resolved;
        }
        throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
    }
}
