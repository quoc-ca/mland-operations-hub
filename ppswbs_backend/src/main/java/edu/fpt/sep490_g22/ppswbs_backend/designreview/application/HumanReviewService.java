package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.HumanReviewDecision;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewDecision;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.HumanReviewDecisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HumanReviewService {

    private final DesignRequestRepository requestRepository;
    private final HumanReviewDecisionRepository decisionRepository;
    private final DesignAnalysisService designAnalysisService;
    private final AccountFacade accountFacade;

    @Transactional(readOnly = true)
    public List<DesignAnalysisService.DesignRequestViewDto> getReviewQueue(
            String callerUid,
            Collection<? extends GrantedAuthority> authorities) {
        return getReviewQueue(callerUid, authorities, null);
    }

    @Transactional(readOnly = true)
    public List<DesignAnalysisService.DesignRequestViewDto> getReviewQueue(
            String callerUid,
            Collection<? extends GrantedAuthority> authorities,
            ReviewStatus statusFilter) {
        boolean isManager = DesignReviewPermissions.isManager(authorities);
        boolean isStaff = DesignReviewPermissions.isStaff(authorities);

        if (!isStaff && !isManager) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED",
                    "Bạn không có quyền truy cập hàng đợi duyệt thiết kế.");
        }

        // Staff and Manager retain and view all custom design requests, sorted newest
        // first
        List<DesignRequest> queue = statusFilter != null
                ? requestRepository.findByReviewStatusOrderByCreatedAtDesc(statusFilter)
                : requestRepository.findAllByOrderByCreatedAtDesc();

        return queue.stream()
                .map(designAnalysisService::mapToViewDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public DesignAnalysisService.DesignRequestViewDto recordDecision(
            String reviewRequestId,
            ReviewDecision decision,
            Integer designVersion,
            String reason,
            String reviewerUid,
            Collection<? extends GrantedAuthority> authorities) {
        log.info("Recording human review decision: reviewRequestId={}, decision={}, version={}, reviewerUid={}",
                reviewRequestId, decision, designVersion, reviewerUid);

        boolean isManager = DesignReviewPermissions.isManager(authorities);
        boolean isStaff = DesignReviewPermissions.isStaff(authorities);

        if (!isStaff && !isManager) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Bạn không có quyền thẩm duyệt thiết kế.");
        }

        if (decision == null) {
            throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID",
                    "Thiếu quyết định thẩm duyệt (ACCEPTED, REJECTED).");
        }

        if (decision != ReviewDecision.ACCEPTED && (reason == null || reason.trim().isEmpty())) {
            throw DesignReviewException.badRequest("DESIGN_REVIEW_REASON_REQUIRED",
                    "Bắt buộc phải nhập lời giải thích / lý do cho quyết định từ chối hoặc yêu cầu làm rõ.");
        }

        String finalReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : (decision == ReviewDecision.ACCEPTED ? "Thiết kế đạt tiêu chuẩn chế tác, đã duyệt gửi báo giá cho khách hàng." : "");

        if (finalReason.length() > 2000) {
            throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID",
                    "Lý do thẩm duyệt không được vượt quá 2000 ký tự.");
        }

        DesignRequest request = requestRepository.findById(reviewRequestId)
                .or(() -> requestRepository.findByRequestReference(reviewRequestId))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND",
                        "Không tìm thấy yêu cầu thẩm duyệt với ID đã cung cấp."));

        // Separation of duties: Reviewer cannot review own request
        accountFacade.resolveAccount(reviewerUid).ifPresent(account -> {
            if (account.accountId().equals(request.getOwnerAccountId())) {
                throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED",
                        "Không thể tự phê duyệt yêu cầu thiết kế do chính bạn tạo.");
            }
        });

        // Terminal state check
        if (request.getReviewStatus() == ReviewStatus.ACCEPTED || request.getReviewStatus() == ReviewStatus.REJECTED) {
            throw DesignReviewException.conflict("DESIGN_REVIEW_ALREADY_DECIDED",
                    "Yêu cầu đã có quyết định cuối cùng và không thể thay đổi.");
        }

        // Design version check
        if (designVersion != null && !designVersion.equals(request.getRequestVersion())) {
            throw DesignReviewException.conflict("DESIGN_REVIEW_STATE_CONFLICT",
                    "Phiên bản thiết kế đã thay đổi hoặc đã có cập nhật mới hơn.");
        }

        // Record human review decision
        String reviewerRole = isManager ? "MANAGER" : "STAFF";
        HumanReviewDecision decisionEntity = HumanReviewDecision.builder()
                .designRequestId(request.getId())
                .reviewerUserId(reviewerUid)
                .reviewerRole(reviewerRole)
                .designVersion(request.getRequestVersion())
                .decision(decision)
                .reason(finalReason)
                .estimatedValueSnapshot(0L)
                .decidedAt(Instant.now())
                .build();
        decisionRepository.save(decisionEntity);

        // Transition state
        ReviewStatus nextReviewStatus = switch (decision) {
            case ACCEPTED -> ReviewStatus.ACCEPTED;
            case REJECTED -> ReviewStatus.REJECTED;
            case CLARIFICATION_REQUIRED -> ReviewStatus.CLARIFICATION_REQUIRED;
        };

        String nextAction = switch (decision) {
            case ACCEPTED -> "DESIGN_ACCEPTED";
            case REJECTED -> "DESIGN_REJECTED";
            case CLARIFICATION_REQUIRED -> "NEW_VERSION_REQUIRED";
        };

        request.setReviewStatus(nextReviewStatus);
        request.setDecisionExplanation(finalReason);
        request.setNextAction(nextAction);
        request.setRequestVersion(request.getRequestVersion() + 1);
        request.setUpdatedAt(Instant.now());
        requestRepository.save(request);

        return designAnalysisService.mapToViewDto(request);
    }
}
