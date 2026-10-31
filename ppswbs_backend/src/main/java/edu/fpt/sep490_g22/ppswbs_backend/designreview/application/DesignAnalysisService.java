package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignAnalysisService {

    private final BookingDesignRequestRepository requestRepository;
    private final DesignAnalysisResultRepository analysisResultRepository;
    private final ImageConsentRepository consentRepository;
    private final TemporaryImageReferenceRepository temporaryImageRepository;
    private final AccountFacade accountFacade;
    private final WorkshopBookingFacade workshopBookingFacade;
    private final ImageRateLimiter imageRateLimiter;
    private final ReferenceImageStoragePort storagePort;
    private final ImageFeatureAnalysisPort featureAnalysisPort;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_DIMENSION = 4096;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    public static class DesignRequestViewDto {
        private String id;
        private Integer designVersion;
        private String analysisStatus;
        private String reviewStatus;
        private String nextAction;
        private String safeFailureCode;
        private Map<String, Object> candidateFeatures;
        private Map<String, Object> imageQuality;
        private Map<String, Object> confidence;
        private String safeExplanation;
    }

    @Transactional
    public DesignRequestViewDto submitDesignRequest(
            String callerUid,
            String bookingCode,
            String consentVersion,
            Integer designVersion,
            String idempotencyKey,
            MultipartFile file
    ) {
        log.info("Submitting booking-design image request: callerUid={}, bookingCode={}, designVersion={}", callerUid, bookingCode, designVersion);

        // 1. Identity & Member Role Validation
        AccountSummary member = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy thông tin tài khoản thành viên."));
        if (member.role() != ActorRole.MEMBER || member.status() != ActorStatus.ACTIVE) {
            throw DesignReviewException.forbidden("MEMBER_INACTIVE_OR_INVALID", "Tài khoản không phải là hội viên hoạt động.");
        }

        int version = (designVersion != null && designVersion > 0) ? designVersion : 1;

        // 2. Duplicate Idempotency Lookup (exemption from rate limit)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<BookingDesignRequest> existing = requestRepository
                    .findByMemberIdAndBookingCodeAndDesignVersionAndIdempotencyKey(
                            member.accountId(), bookingCode, version, idempotencyKey
                    );
            if (existing.isPresent()) {
                log.info("Duplicate idempotency key detected: returning existing design request {}", existing.get().getId());
                return mapToViewDto(existing.get());
            }
        } else {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Thiếu Idempotency-Key trong header.");
        }

        // 3. Atomic Rate Limiting (3 attempts per rolling 5 minutes)
        ImageRateLimiter.RateLimitResult rateCheck = imageRateLimiter.tryAcquire(member.accountId());
        if (!rateCheck.allowed()) {
            throw DesignReviewException.rateLimited(
                    "IMAGE_RATE_LIMITED",
                    "Bạn đã gửi quá 3 lượt phân tích ảnh trong vòng 5 phút. Vui lòng chờ để thử lại.",
                    rateCheck.retryAfterSeconds()
            );
        }

        // 4. Booking Context Validation
        BookingEligibilitySnapshot bookingEligibility = workshopBookingFacade
                .getBookingEligibilityForDesign(bookingCode, member.accountId())
                .orElseThrow(() -> DesignReviewException.unprocessable(
                        "BOOKING_CONTEXT_INVALID",
                        "Mã đặt chỗ không tồn tại hoặc không thuộc quyền sở hữu của bạn."
                ));

        if (!bookingEligibility.eligibleForDesign()) {
            throw DesignReviewException.unprocessable(
                    "BOOKING_CONTEXT_INVALID",
                    "Trạng thái booking hiện tại không cho phép gửi yêu cầu phân tích thiết kế."
            );
        }

        // 5. Consent Validation & Affirmative Logging
        if (consentVersion == null || consentVersion.isBlank()) {
            throw DesignReviewException.unprocessable("CONSENT_REQUIRED", "Bạn cần đồng ý với điều khoản phân tích hình ảnh của dịch vụ.");
        }
        ImageConsent consent = ImageConsent.builder()
                .memberId(member.accountId())
                .bookingCode(bookingCode)
                .purpose("WORKSHOP_BOOKING_DESIGN_ANALYSIS")
                .consentVersion(consentVersion.trim())
                .affirmativeAction("CHECKBOX_CONFIRMED")
                .acceptedAt(Instant.now())
                .build();
        consentRepository.save(consent);

        // 6. Image File Validation
        if (file == null || file.isEmpty()) {
            throw DesignReviewException.badRequest("IMAGE_INVALID", "Tệp hình ảnh không được để trống.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw DesignReviewException.unprocessable("IMAGE_TOO_LARGE", "Kích thước hình ảnh vượt quá giới hạn 10 MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw DesignReviewException.unprocessable("IMAGE_INVALID", "Định dạng hình ảnh không hợp lệ. Chỉ chấp nhận JPEG, PNG hoặc WEBP.");
        }

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage != null) {
                if (bufferedImage.getWidth() > MAX_DIMENSION || bufferedImage.getHeight() > MAX_DIMENSION) {
                    throw DesignReviewException.unprocessable("IMAGE_INVALID", "Độ phân giải ảnh vượt quá kích thước cho phép (tối đa 4096×4096).");
                }
            }
        } catch (IOException e) {
            throw DesignReviewException.unprocessable("IMAGE_INVALID", "Tệp hình ảnh bị lỗi hoặc không thể đọc được.");
        }

        // 7. Temporary Storage & Request Record
        String requestId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "design.jpg";
        String storageKey = storagePort.storeTemporary(requestId, originalFilename, contentType, imageBytes);

        // Save Request Record first to satisfy foreign key FK_TIR_REQUEST
        BookingDesignRequest designRequest = BookingDesignRequest.builder()
                .id(requestId)
                .memberId(member.accountId())
                .bookingCode(bookingCode)
                .designVersion(version)
                .idempotencyKey(idempotencyKey)
                .analysisStatus(AnalysisStatus.PROCESSING)
                .reviewStatus(ReviewStatus.NEED_REVIEW)
                .nextAction("AWAITING_ANALYSIS")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        requestRepository.save(designRequest);

        TemporaryImageReference imageRef = TemporaryImageReference.builder()
                .designRequestId(requestId)
                .storageObjectKey(storageKey)
                .fileName(originalFilename)
                .contentType(contentType)
                .fileSizeBytes(file.getSize())
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        temporaryImageRepository.save(imageRef);

        // 9. Invoke AI Feature Analysis & Immediate Image Cleanup
        ImageFeatureAnalysisPort.FeatureAnalysisResult analysisResult;
        try {
            analysisResult = featureAnalysisPort.analyzeRingImage(
                    imageBytes, contentType, bookingEligibility.packageName(), requestId
            );
        } finally {
            // Immediate deletion of temporary image on both success and failure
            storagePort.deleteTemporary(storageKey);
            imageRef.markDeleted("DELETED_AFTER_PROCESSING");
            temporaryImageRepository.save(imageRef);
        }

        // 10. Persist Analysis Outcome
        if (analysisResult.success()) {
            designRequest.setAnalysisStatus(AnalysisStatus.SUCCEEDED);
            designRequest.setEstimatedValue(analysisResult.estimatedValue());
            designRequest.setNextAction("AWAITING_HUMAN_REVIEW");
            requestRepository.save(designRequest);

            DesignAnalysisResult resultEntity = DesignAnalysisResult.builder()
                    .designRequestId(requestId)
                    .schemaVersion(analysisResult.schemaVersion())
                    .imageQualityJson(analysisResult.imageQualityJson())
                    .candidateFeaturesJson(analysisResult.candidateFeaturesJson())
                    .confidenceJson(analysisResult.confidenceJson())
                    .safeExplanation(analysisResult.safeExplanation())
                    .providerCorrelationId(analysisResult.providerCorrelationId())
                    .processedAt(Instant.now())
                    .build();
            analysisResultRepository.save(resultEntity);
        } else {
            designRequest.setAnalysisStatus(AnalysisStatus.FAILED);
            designRequest.setSafeFailureCode(analysisResult.failureCode() != null ? analysisResult.failureCode() : "AI_RESPONSE_INVALID");
            designRequest.setNextAction("SAFE_RETRY_AVAILABLE");
            requestRepository.save(designRequest);
        }

        return mapToViewDto(designRequest);
    }

    @Transactional(readOnly = true)
    public DesignRequestViewDto getDesignRequestById(String id, String callerUid, Collection<? extends GrantedAuthority> authorities) {
        BookingDesignRequest request = requestRepository.findById(id)
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy yêu cầu thiết kế với mã đã cung cấp."));

        if (!DesignReviewPermissions.canViewDesignRequest(authorities)) {
            // Must be owning Member
            AccountSummary member = accountFacade.resolveAccount(callerUid).orElse(null);
            if (member == null || !member.accountId().equals(request.getMemberId())) {
                throw DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy yêu cầu thiết kế.");
            }
        }

        return mapToViewDto(request);
    }

    public DesignRequestViewDto mapToViewDto(BookingDesignRequest request) {
        Optional<DesignAnalysisResult> resultOpt = analysisResultRepository.findByDesignRequestId(request.getId());

        Map<String, Object> candidateFeatures = null;
        Map<String, Object> imageQuality = null;
        Map<String, Object> confidence = null;
        String explanation = null;

        if (resultOpt.isPresent()) {
            DesignAnalysisResult res = resultOpt.get();
            explanation = res.getSafeExplanation();
            try {
                if (res.getCandidateFeaturesJson() != null) {
                    candidateFeatures = objectMapper.readValue(res.getCandidateFeaturesJson(), new TypeReference<>() {});
                }
                if (res.getImageQualityJson() != null) {
                    imageQuality = objectMapper.readValue(res.getImageQualityJson(), new TypeReference<>() {});
                }
                if (res.getConfidenceJson() != null) {
                    confidence = objectMapper.readValue(res.getConfidenceJson(), new TypeReference<>() {});
                }
            } catch (Exception e) {
                log.warn("Failed to parse json projections: {}", e.getMessage());
            }
        }

        return DesignRequestViewDto.builder()
                .id(request.getId())
                .designVersion(request.getDesignVersion())
                .analysisStatus(request.getAnalysisStatus().name())
                .reviewStatus(request.getReviewStatus().name())
                .nextAction(request.getNextAction())
                .safeFailureCode(request.getSafeFailureCode())
                .candidateFeatures(candidateFeatures)
                .imageQuality(imageQuality)
                .confidence(confidence)
                .safeExplanation(explanation)
                .build();
    }
}
