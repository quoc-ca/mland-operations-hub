package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.controller;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.SecurityIdentityExtractor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
@Slf4j
public class MemberCustomOrderController {

    private final CustomOrderService customOrderService;
    private final DesignAnalysisService designAnalysisService;
    private final AccountFacade accountFacade;
    private final CustomOrderRepository customOrderRepository;

    @GetMapping("/custom-orders")
    public ResponseEntity<CustomOrderListProjectionDto> listMyCustomOrders(
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        AccountSummary member = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy thông tin tài khoản."));

        List<CustomOrderProjectionDto> list = customOrderService.listMyCustomOrders(member.accountId());
        return ResponseEntity.ok(CustomOrderListProjectionDto.builder().items(list).build());
    }

    @GetMapping("/custom-orders/{customOrderId}")
    public ResponseEntity<CustomOrderProjectionDto> getMyCustomOrder(
            @PathVariable("customOrderId") String customOrderId,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        AccountSummary member = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy thông tin tài khoản."));

        CustomOrderProjectionDto order = customOrderService.getMyCustomOrder(member.accountId(), customOrderId);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/custom-orders/{customOrderId}/payment")
    public ResponseEntity<PaymentAttemptProjectionDto> startFullPayment(
            @PathVariable("customOrderId") String customOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        AccountSummary member = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy thông tin tài khoản."));

        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        }

        PaymentAttemptProjectionDto paymentAttempt = customOrderService.startFullPayment(
                member.accountId(),
                customOrderId,
                idempotencyKey,
                clientIp
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(paymentAttempt);
    }

    @PostMapping("/custom-orders/{customOrderId}/request-new-quote")
    public ResponseEntity<CustomOrderProjectionDto> requestNewQuote(
            @PathVariable("customOrderId") String customOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        AccountSummary member = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy thông tin tài khoản."));

        CustomOrderProjectionDto updatedOrder = customOrderService.requestNewQuote(
                member.accountId(),
                customOrderId,
                idempotencyKey
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(updatedOrder);
    }

    @GetMapping("/custom-design-requests")
    public ResponseEntity<CustomRequestListProjectionDto> listMyCustomDesignRequests(
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        List<DesignAnalysisService.DesignRequestViewDto> list = designAnalysisService.getMyDesignRequests(callerUid);

        List<CustomDesignRequestProjectionDto> items = list.stream().map(dto -> {
            CustomDesignRequestProjectionDto.CustomDesignRequestProjectionDtoBuilder builder = CustomDesignRequestProjectionDto.builder()
                .requestReference(dto.getRequestReference())
                .status(dto.getReviewStatus())
                .version(dto.getRequestVersion())
                .explanation(dto.getDecisionExplanation())
                .designSnapshot(dto.getDesignSnapshot())
                .quoteReference(dto.getQuoteReference())
                .description(dto.getDescription())
                .images(dto.getImages())
                .trackingLink(dto.getTrackingLink())
                .createdAt(dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null)
                .updatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt().toString() : null);

            if (dto.getId() != null) {
                customOrderRepository.findByDesignRequestId(dto.getId())
                        .ifPresent(co -> builder.customOrderReference(co.getCustomOrderReference()));
            }

            return builder.build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(CustomRequestListProjectionDto.builder().items(items).build());
    }
}
