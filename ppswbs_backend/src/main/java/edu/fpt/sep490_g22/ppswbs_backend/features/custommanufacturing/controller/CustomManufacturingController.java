package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.controller;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewPermissions;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.SecurityIdentityExtractor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/custom-orders")
@RequiredArgsConstructor
@Slf4j
public class CustomManufacturingController {

    private final CustomOrderService customOrderService;
    private final AccountFacade accountFacade;

    @GetMapping
    public ResponseEntity<List<CustomOrderProjectionDto>> listCustomOrdersForOperations(
            @RequestParam(name = "status", required = false) String status,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        boolean isStaff = DesignReviewPermissions.isStaff(authentication.getAuthorities());
        boolean isManager = DesignReviewPermissions.isManager(authentication.getAuthorities());
        if (!isStaff && !isManager) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Bạn không có quyền truy cập danh sách đơn hàng gia công.");
        }

        List<CustomOrderProjectionDto> orders = customOrderService.listAllCustomOrdersForOperations(status);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{customOrderId}")
    public ResponseEntity<CustomOrderProjectionDto> getCustomOrderForOperations(
            @PathVariable("customOrderId") String customOrderId,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        boolean isStaff = DesignReviewPermissions.isStaff(authentication.getAuthorities());
        boolean isManager = DesignReviewPermissions.isManager(authentication.getAuthorities());
        if (!isStaff && !isManager) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Bạn không có quyền xem thông tin đơn hàng gia công này.");
        }

        CustomOrderProjectionDto order = customOrderService.getCustomOrderForOperations(customOrderId);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{customOrderId}/quotes/{quoteId}/approve")
    public ResponseEntity<QuoteProjectionDto> approveQuoteWithQuoteId(
            @PathVariable("customOrderId") String customOrderId,
            @PathVariable("quoteId") String quoteId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication
    ) {
        return doApproveQuote(customOrderId, quoteId, body, authentication);
    }

    @PostMapping("/{customOrderId}/quotes/approve")
    public ResponseEntity<QuoteProjectionDto> approveQuoteLatest(
            @PathVariable("customOrderId") String customOrderId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication
    ) {
        return doApproveQuote(customOrderId, null, body, authentication);
    }

    private ResponseEntity<QuoteProjectionDto> doApproveQuote(
            String customOrderId,
            String quoteId,
            Map<String, String> body,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        boolean isManager = DesignReviewPermissions.isManager(authentication.getAuthorities());
        if (!isManager) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED",
                    "Chỉ Quản lý (Manager) mới có thẩm quyền phê duyệt báo giá sản xuất gia công tùy chỉnh.");
        }

        AccountSummary manager = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy thông tin tài khoản."));

        String reason = body != null ? body.get("managerReason") : "Phê duyệt báo giá đơn hàng gia công tùy chỉnh";
        QuoteProjectionDto approvedQuote = customOrderService.approveQuote(
                customOrderId,
                quoteId,
                manager.accountId(),
                reason
        );

        return ResponseEntity.ok(approvedQuote);
    }

    @PostMapping("/{customOrderId}/status")
    public ResponseEntity<CustomOrderProjectionDto> updateStatus(
            @PathVariable("customOrderId") String customOrderId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        boolean isStaff = DesignReviewPermissions.isStaff(authentication.getAuthorities());
        boolean isManager = DesignReviewPermissions.isManager(authentication.getAuthorities());
        if (!isStaff && !isManager) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Bạn không có quyền cập nhật trạng thái sản xuất.");
        }

        String prodStatus = body != null ? body.get("productionStatus") : null;
        String fulfStatus = body != null ? body.get("fulfilmentStatus") : null;
        String status = body != null ? body.get("status") : null;

        CustomOrderProjectionDto updated = customOrderService.updateProductionOrFulfilmentStatus(
                customOrderId,
                prodStatus,
                fulfStatus,
                status
        );

        return ResponseEntity.ok(updated);
    }
}
