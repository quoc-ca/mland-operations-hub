package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.BookingDesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.HumanReviewDecision;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewDecision;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.BookingDesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.HumanReviewDecisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberFacade;
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

    private final BookingDesignRequestRepository requestRepository;
    private final HumanReviewDecisionRepository decisionRepository;
    private final DesignAnalysisService designAnalysisService;
    private final MemberFacade memberFacade;

    public static final long STAFF_MAX_ESTIMATED_VALUE = 3_000_000L; // 3,000,000 VND

    @Transactional(readOnly = true)
    public List<DesignAnalysisService.DesignRequestViewDto> getReviewQueue(
            String callerUid,
            Collection<? extends GrantedAuthority> authorities
    ) {
        boolean isOwner = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        boolean isStaff = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));

        if (!isStaff && !isOwner) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Bạn không có quyền truy cập hàng đợi duyệt thiết kế.");
        }

        List<BookingDesignRequest> queue = requestRepository.findByReviewStatusOrderByCreatedAtAsc(ReviewStatus.NEED_REVIEW);

        return queue.stream()
                .filter(req -> {
                    long val = req.getEstimatedValue() != null ? req.getEstimatedValue() : 0L;
                    if (isOwner) {
                        return true; // Manager/Owner can review all
                    }
                    // Staff can only review <= 3,000,000 VND
                    return val <= STAFF_MAX_ESTIMATED_VALUE;
                })
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
            Collection<? extends GrantedAuthority> authorities
    ) {
        log.info("Recording review decision: reviewRequestId={}, decision={}, version={}, reviewerUid={}",
                reviewRequestId, decision, designVersion, reviewerUid);

        boolean isOwner = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        boolean isStaff = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));

        if (!isStaff && !isOwner) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Bạn không có quyền thẩm duyệt thiết kế.");
        }

        if (decision == null) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Thiếu quyết định thẩm duyệt (ACCEPTED, REJECTED, CLARIFICATION_REQUIRED).");
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Lý do thẩm duyệt không được để trống.");
        }

        BookingDesignRequest request = requestRepository.findById(reviewRequestId)
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy yêu cầu thẩm duyệt với ID đã cung cấp."));

        // Separation of duties: Reviewer cannot review own request
        MemberDto member = memberFacade.getMemberByUid(reviewerUid).orElse(null);
        if (member != null && member.getId().equals(request.getMemberId())) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Không thể tự phê duyệt yêu cầu thiết kế do chính bạn tạo.");
        }

        // Terminal state check
        if (request.getReviewStatus() == ReviewStatus.ACCEPTED || request.getReviewStatus() == ReviewStatus.REJECTED) {
            throw DesignReviewException.conflict("REVIEW_STATE_CONFLICT", "Yêu cầu đã có quyết định cuối cùng và không thể thay đổi.");
        }

        // Design version check
        if (designVersion != null && !designVersion.equals(request.getDesignVersion())) {
            throw DesignReviewException.conflict("DESIGN_VERSION_STALE", "Phiên bản thiết kế đã thay đổi hoặc đã có cập nhật mới hơn.");
        }

        // Authority / Threshold check
        long estimatedValue = request.getEstimatedValue() != null ? request.getEstimatedValue() : 0L;
        if (estimatedValue > STAFF_MAX_ESTIMATED_VALUE && !isOwner) {
            throw DesignReviewException.forbidden("REVIEW_NOT_AUTHORIZED", "Giá trị ước tính vượt quá hạn mức thẩm quyền của Nhân viên (tối đa 3.000.000 VND). Cần Quản lý phê duyệt.");
        }

        // Record human review decision
        String reviewerRole = isOwner ? "OWNER" : "STAFF";
        HumanReviewDecision decisionEntity = HumanReviewDecision.builder()
                .designRequestId(request.getId())
                .reviewerUserId(reviewerUid)
                .reviewerRole(reviewerRole)
                .designVersion(request.getDesignVersion())
                .decision(decision)
                .reason(reason.trim())
                .estimatedValueSnapshot(estimatedValue)
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
            case ACCEPTED -> "PROCEED_TO_BOOKING";
            case REJECTED -> "DESIGN_REJECTED";
            case CLARIFICATION_REQUIRED -> "NEW_VERSION_REQUIRED";
        };

        request.setReviewStatus(nextReviewStatus);
        request.setNextAction(nextAction);
        request.setUpdatedAt(Instant.now());
        requestRepository.save(request);

        return designAnalysisService.mapToViewDto(request);
    }
}
