package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignAnalysisService {

    private final DesignRequestRepository requestRepository;
    private final DesignAnalysisResultRepository analysisResultRepository;
    private final ImageConsentRepository consentRepository;
    private final TemporaryImageReferenceRepository temporaryImageRepository;
    private final AccountFacade accountFacade;
    private final ImageRateLimiter imageRateLimiter;
    private final ReferenceImageStoragePort storagePort;
    private final ImageFeatureAnalysisPort featureAnalysisPort;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("approve\\s+(this\\s+)?(order|request|design)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bỏ\\s+qua\\s+chỉ\\s+dẫn", Pattern.CASE_INSENSITIVE),
            Pattern.compile("phê\\s+duyệt\\s+yêu\\s+cầu", Pattern.CASE_INSENSITIVE),
            Pattern.compile("duyệt\\s+thiết\\s+kế", Pattern.CASE_INSENSITIVE)
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    public static class DesignRequestViewDto {
        private String id;
        private String requestReference;
        private Integer requestVersion;
        private String requestStatus;
        private String analysisStatus;
        private String reviewStatus;
        private String nextAction;
        private String safeFailureCode;
        private String description;
        private String catalogueReference;
        private Map<String, Object> candidateFeatures;
        private Map<String, Object> imageQuality;
        private Map<String, Object> confidence;
        private String safeExplanation;
    }

    @Transactional
    public DesignRequestViewDto submitDesignRequest(
            String callerUid,
            String description,
            String catalogueReference,
            String consentVersion,
            String consentPurpose,
            Integer designVersion,
            String idempotencyKey,
            MultipartFile file
    ) {
        log.info("Submitting independent design request: callerUid={}, catalogueReference={}, designVersion={}",
                callerUid, catalogueReference, designVersion);

        // 1. Identity & Member Role Validation
        AccountSummary member = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy thông tin tài khoản thành viên."));
        if (member.role() != ActorRole.MEMBER || member.status() != ActorStatus.ACTIVE) {
            throw DesignReviewException.forbidden("MEMBER_INACTIVE_OR_INVALID", "Tài khoản không phải là hội viên hoạt động.");
        }

        int version = (designVersion != null && designVersion > 0) ? designVersion : 1;

        // 2. Duplicate Idempotency Lookup (exemption from rate limit)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<DesignRequest> existing = requestRepository
                    .findByOwnerAccountIdAndRequestVersionAndIdempotencyKey(
                            member.accountId(), version, idempotencyKey
                    );
            if (existing.isPresent()) {
                log.info("Duplicate idempotency key detected: returning existing design request {}", existing.get().getId());
                return mapToViewDto(existing.get());
            }
        } else {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Thiếu Idempotency-Key trong header.");
        }

        // 3. Request Content Validation (at least description or image is required)
        boolean hasDescription = (description != null && !description.trim().isEmpty());
        boolean hasImage = (file != null && !file.isEmpty());

        if (!hasDescription && !hasImage) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Yêu cầu phải có mô tả thiết kế hoặc tệp hình ảnh đính kèm.");
        }

        String sanitizedDescription = null;
        if (hasDescription) {
            sanitizedDescription = description.trim();
            if (sanitizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
                throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Mô tả thiết kế không được vượt quá 4.000 ký tự.");
            }
            for (Pattern p : PROMPT_INJECTION_PATTERNS) {
                if (p.matcher(sanitizedDescription).find()) {
                    log.warn("Prompt injection attempt detected in design description: callerUid={}", callerUid);
                    throw DesignReviewException.unprocessable("AI_PROMPT_INJECTION_DETECTED", "Nội dung mô tả vi phạm chính sách an toàn của hệ thống.");
                }
            }
        }

        String sanitizedCatalogueRef = (catalogueReference != null && !catalogueReference.isBlank())
                ? catalogueReference.trim() : null;

        String requestId = UUID.randomUUID().toString();
        String requestRef = "DR-" + requestId.substring(0, 8).toUpperCase();

        // 4. Image Validation & Processing (if image supplied)
        byte[] imageBytes = null;
        String contentType = null;
        String originalFilename = null;

        if (hasImage) {
            // Consent Validation
            if (consentVersion == null || consentVersion.isBlank()) {
                throw DesignReviewException.unprocessable("CONSENT_REQUIRED", "Bạn cần đồng ý với điều khoản phân tích hình ảnh của dịch vụ.");
            }

            // Atomic Rate Limiting (3 attempts per rolling 5 minutes)
            ImageRateLimiter.RateLimitResult rateCheck = imageRateLimiter.tryAcquire(member.accountId());
            if (!rateCheck.allowed()) {
                throw DesignReviewException.rateLimited(
                        "IMAGE_RATE_LIMITED",
                        "Bạn đã gửi quá 3 lượt phân tích ảnh trong vòng 5 phút. Vui lòng chờ để thử lại.",
                        rateCheck.retryAfterSeconds()
                );
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                throw DesignReviewException.unprocessable("IMAGE_TOO_LARGE", "Kích thước hình ảnh vượt quá giới hạn 10 MB.");
            }

            contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
                throw DesignReviewException.unprocessable("IMAGE_INVALID", "Định dạng hình ảnh không hợp lệ. Chỉ chấp nhận JPEG, PNG hoặc WEBP.");
            }

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

            originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "design.jpg";
        }

        // 5. Create Independent Design Request Record
        DesignRequest designRequest = DesignRequest.builder()
                .id(requestId)
                .requestReference(requestRef)
                .ownerAccountId(member.accountId())
                .ownershipType("MEMBER")
                .description(sanitizedDescription)
                .catalogueReference(sanitizedCatalogueRef)
                .requestVersion(version)
                .idempotencyKey(idempotencyKey)
                .requestStatus("ACTIVE")
                .analysisStatus(AnalysisStatus.PROCESSING)
                .reviewStatus(ReviewStatus.NEED_REVIEW)
                .nextAction("AWAITING_ANALYSIS")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        requestRepository.save(designRequest);

        // 6. Execute AI Analysis or Text-only flow
        if (hasImage) {
            // Save Consent Record
            ImageConsent consent = ImageConsent.builder()
                    .actorAccountId(member.accountId())
                    .designRequestId(requestId)
                    .purpose("INDEPENDENT_DESIGN_IMAGE_ANALYSIS")
                    .consentVersion(consentVersion.trim())
                    .affirmativeAction("CHECKBOX_CONFIRMED")
                    .acceptedAt(Instant.now())
                    .build();
            consentRepository.save(consent);

            // Temporary Storage Record
            String storageKey = storagePort.storeTemporary(requestId, originalFilename, contentType, imageBytes);
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

            ImageFeatureAnalysisPort.FeatureAnalysisResult analysisResult;
            try {
                analysisResult = featureAnalysisPort.analyzeRingImage(
                        imageBytes, contentType, sanitizedCatalogueRef, requestId
                );
            } finally {
                // Immediate deletion of temporary image on both success and failure
                storagePort.deleteTemporary(storageKey);
                imageRef.markDeleted("DELETED_AFTER_PROCESSING");
                temporaryImageRepository.save(imageRef);
            }

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
        } else {
            // Text-only request
            designRequest.setAnalysisStatus(AnalysisStatus.SUCCEEDED);
            designRequest.setEstimatedValue(1_800_000L);
            designRequest.setNextAction("AWAITING_HUMAN_REVIEW");
            requestRepository.save(designRequest);

            DesignAnalysisResult resultEntity = DesignAnalysisResult.builder()
                    .designRequestId(requestId)
                    .schemaVersion("v1.0")
                    .imageQualityJson(null)
                    .candidateFeaturesJson("""
                            {
                              "requestType": "TEXT_DESCRIPTION_ONLY",
                              "feasibilityScore": 0.90
                            }
                            """.trim())
                    .confidenceJson("""
                            {
                              "overallConfidence": 0.90
                            }
                            """.trim())
                    .safeExplanation("Yêu cầu thiết kế mô tả dạng văn bản được ghi nhận và chuyển thẩm duyệt kỹ thuật.")
                    .providerCorrelationId("text-design-" + requestId.substring(0, 8))
                    .processedAt(Instant.now())
                    .build();
            analysisResultRepository.save(resultEntity);
        }

        return mapToViewDto(designRequest);
    }

    @Transactional(readOnly = true)
    public DesignRequestViewDto getDesignRequestById(String id, String callerUid, Collection<? extends GrantedAuthority> authorities) {
        DesignRequest request = requestRepository.findById(id)
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy yêu cầu thiết kế với mã đã cung cấp."));

        if (!DesignReviewPermissions.canViewDesignRequest(authorities)) {
            // Must be owning Member
            AccountSummary member = accountFacade.resolveAccount(callerUid).orElse(null);
            if (member == null || !member.accountId().equals(request.getOwnerAccountId())) {
                throw DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy yêu cầu thiết kế.");
            }
        }

        return mapToViewDto(request);
    }

    public DesignRequestViewDto mapToViewDto(DesignRequest request) {
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
                .requestReference(request.getRequestReference())
                .requestVersion(request.getRequestVersion())
                .requestStatus(request.getRequestStatus())
                .analysisStatus(request.getAnalysisStatus().name())
                .reviewStatus(request.getReviewStatus().name())
                .nextAction(request.getNextAction())
                .safeFailureCode(request.getSafeFailureCode())
                .description(request.getDescription())
                .catalogueReference(request.getCatalogueReference())
                .candidateFeatures(candidateFeatures)
                .imageQuality(imageQuality)
                .confidence(confidence)
                .safeExplanation(explanation)
                .build();
    }
}
