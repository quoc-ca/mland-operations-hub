package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewPermissions;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.HumanReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequestImage;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewDecision;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping({ "/api/v1/custom-design-requests", "/api/v1/design-requests" })
@RequiredArgsConstructor
@Slf4j
public class DesignRequestController {

        private final DesignAnalysisService designAnalysisService;
        private final HumanReviewService humanReviewService;
        private final CustomOrderService customOrderService;
        private final AccountFacade accountFacade;

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> submitDesignRequest(
                        @RequestParam(value = "description", required = false) String description,
                        @RequestParam(value = "catalogueReference", required = false) String catalogueReference,
                        @RequestParam(value = "designReference", required = false) String designReference,
                        @RequestParam(value = "designRingVersion", required = false) Integer designRingVersion,
                        @RequestParam(value = "consentVersion", required = false) String consentVersion,
                        @RequestParam(value = "consentPurpose", required = false) String consentPurpose,
                        @RequestParam(value = "designVersion", defaultValue = "1") Integer designVersion,
                        @RequestPart(value = "image", required = false) MultipartFile singleImage,
                        @RequestPart(value = "images", required = false) List<MultipartFile> multipleImages,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                        @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionId,
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);
                boolean isStaffOrManager = authentication.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()) || "STAFF".equals(a.getAuthority())
                                                || "ROLE_MANAGER".equals(a.getAuthority())
                                                || "MANAGER".equals(a.getAuthority()));
                if (isStaffOrManager) {
                        throw DesignReviewException.forbidden("MEMBER_INACTIVE_OR_INVALID",
                                        "Nhân viên và Quản lý không thể gửi yêu cầu thiết kế với tư cách khách hàng.");
                }

                if (idempotencyKey == null || idempotencyKey.isBlank()) {
                        throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID",
                                        "Header Idempotency-Key là bắt buộc.");
                }

                List<MultipartFile> allFiles = new ArrayList<>();
                if (multipleImages != null && !multipleImages.isEmpty()) {
                        for (MultipartFile f : multipleImages) {
                                if (f != null && !f.isEmpty()) {
                                        boolean duplicate = allFiles.stream().anyMatch(
                                                        existing -> Objects.equals(existing.getOriginalFilename(),
                                                                        f.getOriginalFilename()) &&
                                                                        existing.getSize() == f.getSize());
                                        if (!duplicate) {
                                                allFiles.add(f);
                                        }
                                }
                        }
                } else if (singleImage != null && !singleImage.isEmpty()) {
                        allFiles.add(singleImage);
                }

                DesignAnalysisService.DesignRequestViewDto response = designAnalysisService.submitDesignRequest(
                                callerUid,
                                null,
                                description,
                                catalogueReference,
                                designReference,
                                designRingVersion,
                                consentVersion,
                                consentPurpose,
                                designVersion,
                                idempotencyKey,
                                allFiles);

                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> submitDesignRequestJson(
                        @Valid @RequestBody CreateCustomDesignRequestDto body,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);
                boolean isStaffOrManager = authentication.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()) || "STAFF".equals(a.getAuthority())
                                                || "ROLE_MANAGER".equals(a.getAuthority())
                                                || "MANAGER".equals(a.getAuthority()));
                if (isStaffOrManager) {
                        throw DesignReviewException.forbidden("MEMBER_INACTIVE_OR_INVALID",
                                        "Nhân viên và Quản lý không thể gửi yêu cầu thiết kế với tư cách khách hàng.");
                }

                if (idempotencyKey == null || idempotencyKey.isBlank()) {
                        throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID",
                                        "Header Idempotency-Key là bắt buộc.");
                }

                DesignAnalysisService.DesignRequestViewDto response = designAnalysisService.submitDesignRequest(
                                callerUid,
                                null,
                                body.getDescription(),
                                null,
                                body.getDesignReference(),
                                body.getDesignVersion(),
                                "v1.0",
                                "HUMAN_DESIGN_REVIEW",
                                body.getDesignVersion() != null ? body.getDesignVersion() : 1,
                                idempotencyKey,
                                Collections.emptyList());

                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        @GetMapping
        public ResponseEntity<List<DesignAnalysisService.DesignRequestViewDto>> getMyDesignRequests(
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);
                List<DesignAnalysisService.DesignRequestViewDto> list = designAnalysisService
                                .getMyDesignRequests(callerUid);
                return ResponseEntity.ok(list);
        }

        @GetMapping("/{designRequestId}")
        public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> getDesignRequest(
                        @PathVariable("designRequestId") String designRequestId,
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);
                Collection<? extends GrantedAuthority> authorities = authentication != null
                                ? authentication.getAuthorities()
                                : Collections.emptyList();

                DesignAnalysisService.DesignRequestViewDto view = designAnalysisService.getDesignRequestById(
                                designRequestId,
                                callerUid,
                                authorities,
                                null);

                return ResponseEntity.ok(view);
        }

        @PostMapping("/{designRequestId}")
        public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> reviseDesignRequest(
                        @PathVariable("designRequestId") String designRequestId,
                        @RequestHeader(value = "If-Match-Version", required = false) Integer ifMatchVersionHeader,
                        @RequestBody(required = false) CustomDesignRevisionDto body,
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);

                String desc = body != null ? body.getDescription() : null;
                String designRef = body != null ? body.getDesignReference() : null;
                Integer designVer = body != null ? body.getDesignVersion() : null;
                String clarifResp = body != null ? body.getClarificationResponse() : null;

                DesignAnalysisService.DesignRequestViewDto revised = designAnalysisService.reviseDesignRequest(
                                designRequestId,
                                desc,
                                designRef,
                                designVer,
                                clarifResp,
                                callerUid,
                                null,
                                ifMatchVersionHeader,
                                Collections.emptyList());

                return ResponseEntity.ok(revised);
        }

        @PostMapping("/{designRequestId}/decisions")
        public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> recordDecision(
                        @PathVariable("designRequestId") String designRequestId,
                        @RequestHeader(value = "If-Match-Version", required = false) Integer ifMatchVersionHeader,
                        @Valid @RequestBody ReviewDecisionRequest body,
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);

                ReviewDecision decision;
                try {
                        decision = ReviewDecision.valueOf(body.getDecision().trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                        throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID",
                                        "Quyết định thẩm duyệt không hợp lệ. Phải là ACCEPTED, REJECTED hoặc CLARIFICATION_REQUIRED.");
                }

                Integer version = ifMatchVersionHeader != null ? ifMatchVersionHeader : body.getRequestVersion();

                DesignAnalysisService.DesignRequestViewDto result = humanReviewService.recordDecision(
                                designRequestId,
                                decision,
                                version,
                                body.getReason(),
                                callerUid,
                                authentication.getAuthorities());

                return ResponseEntity.ok(result);
        }

        @PostMapping("/{designRequestId}/quote")
        public ResponseEntity<QuoteProjectionDto> prepareQuote(
                        @PathVariable("designRequestId") String designRequestId,
                        @Valid @RequestBody QuoteCommandDto body,
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);
                boolean isStaff = authentication.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority())
                                                || "STAFF".equals(a.getAuthority()));
                boolean isManager = authentication.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority())
                                                || "MANAGER".equals(a.getAuthority()));

                if (!isStaff && !isManager) {
                        throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED",
                                        "Bạn không có quyền chuẩn bị báo giá cho yêu cầu thiết kế.");
                }

                AccountSummary account = accountFacade.resolveAccount(callerUid)
                                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED",
                                                "Không tìm thấy thông tin tài khoản."));

                long totalAmount;
                try {
                        totalAmount = Long.parseLong(body.getTotalAmount().trim());
                } catch (NumberFormatException e) {
                        throw DesignReviewException.badRequest("QUOTE_AMOUNT_INVALID",
                                        "Tổng số tiền báo giá không hợp lệ.");
                }

                Instant expiry = null;
                if (body.getQuoteExpiresAt() != null && !body.getQuoteExpiresAt().isBlank()) {
                        try {
                                expiry = Instant.parse(body.getQuoteExpiresAt().trim());
                        } catch (Exception e) {
                                // Ignore parse exception or fallback
                        }
                }

                QuoteProjectionDto quoteDto = customOrderService.prepareOrUpdateQuote(
                                designRequestId,
                                account.accountId(),
                                isManager,
                                totalAmount,
                                body.getCurrency(),
                                expiry,
                                body.getManagerReason());

                return ResponseEntity.ok(quoteDto);
        }

        @GetMapping("/{designRequestId}/quote")
        public ResponseEntity<QuoteProjectionDto> getQuote(
                        @PathVariable("designRequestId") String designRequestId,
                        Authentication authentication) {
                QuoteProjectionDto quote = customOrderService.findLatestQuoteByDesignRequestId(designRequestId)
                                .orElse(null);
                if (quote == null) {
                        return ResponseEntity.noContent().build();
                }
                return ResponseEntity.ok(quote);
        }

        @PostMapping("/{designRequestId}/quote/approve")
        public ResponseEntity<QuoteProjectionDto> approveQuote(
                        @PathVariable("designRequestId") String designRequestId,
                        @RequestBody(required = false) Map<String, String> body,
                        Authentication authentication) {
                String callerUid = SecurityIdentityExtractor.requireUid(authentication);
                boolean isManager = DesignReviewPermissions.isManager(authentication.getAuthorities());
                if (!isManager) {
                        throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED",
                                        "Chỉ Quản lý (Manager) mới có thẩm quyền phê duyệt báo giá.");
                }
                AccountSummary manager = accountFacade.resolveAccount(callerUid)
                                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED",
                                                "Không tìm thấy thông tin tài khoản."));
                String reason = body != null ? body.get("managerReason")
                                : "Phê duyệt báo giá đơn hàng gia công tùy chỉnh";
                QuoteProjectionDto quoteDto = customOrderService.approveQuoteByDesignRequestId(
                                designRequestId,
                                manager.accountId(),
                                reason);
                return ResponseEntity.ok(quoteDto);
        }

        @GetMapping("/{designRequestId}/tracking")
        public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> getGuestTracking(
                        @PathVariable("designRequestId") String designRequestId) {
                throw DesignReviewException.unauthorized("DESIGN_TRACKING_NOT_SUPPORTED",
                                "Chức năng theo dõi yêu cầu thiết kế ẩn danh đã ngừng hoạt động. Vui lòng đăng nhập tài khoản hội viên.");
        }

        @GetMapping("/images/{imageReference}")
        public ResponseEntity<byte[]> getImage(
                        @PathVariable("imageReference") String imageReference) {
                DesignRequestImage imageEntity = designAnalysisService.getImageEntity(imageReference);
                byte[] bytes = designAnalysisService.getImageBytes(imageReference);

                if (bytes == null || bytes.length == 0) {
                        return ResponseEntity.notFound().build();
                }

                return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(imageEntity.getContentType()))
                                .contentLength(bytes.length)
                                .body(bytes);
        }
}
