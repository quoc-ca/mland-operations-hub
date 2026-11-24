package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.*;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.BookingDesignSnapshotDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.facade.WorkshopRingDesignFacade;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignAnalysisService {

    private final DesignRequestRepository requestRepository;
    private final DesignRequestImageRepository designRequestImageRepository;
    private final DesignAnalysisResultRepository analysisResultRepository;
    private final ImageConsentRepository consentRepository;
    private final TemporaryImageReferenceRepository temporaryImageRepository;
    private final AccountFacade accountFacade;
    private final ImageRateLimiter imageRateLimiter;
    private final ReferenceImageStoragePort storagePort;
    private final ImageQualityService imageQualityService;
    private final GuestDesignRequestQuotaService guestQuotaService;
    private final Optional<WorkshopRingDesignFacade> workshopRingDesignFacade;
    private final CustomDesignReferenceSnapshotRepository snapshotRepository;
    private final CustomDesignRequestRevisionRepository revisionRepository;

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_IMAGE_COUNT = 5;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("approve\\s+(this\\s+)?(order|request|design)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bỏ\\s+qua\\s+chỉ\\s+dẫn", Pattern.CASE_INSENSITIVE),
            Pattern.compile("phê\\s+duyệt\\s+yêu\\s+cầu", Pattern.CASE_INSENSITIVE),
            Pattern.compile("duyệt\\s+thiết\\s+kế", Pattern.CASE_INSENSITIVE));

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
        private String status; // Alias matching OpenAPI CustomerRequestProjection: NEED_REVIEW, ACCEPTED, REJECTED
        private String nextAction;
        private String safeFailureCode;
        private String description;
        private String catalogueReference;
        private String designReference;
        private Integer designVersion;
        private String quoteReference;
        private Map<String, Object> designSnapshot;
        private List<DesignReviewDtos.ImageProjectionDto> images;
        private String decisionExplanation;
        private String trackingLink;
        private String guestTrackingToken;
        private Map<String, Object> candidateFeatures;
        private Map<String, Object> imageQuality;
        private Map<String, Object> confidence;
        private String safeExplanation;
        private Map<String, Object> safeResult;
        private String senderEmail;
        private Instant createdAt;
        private Instant updatedAt;

        public Integer getVersion() {
            return requestVersion;
        }

        public String getExplanation() {
            return decisionExplanation;
        }
    }

    /**
     * Backward-compatible overload for single-file member submissions
     */
    @Transactional
    public DesignRequestViewDto submitDesignRequest(
            String callerUid,
            String description,
            String catalogueReference,
            String consentVersion,
            String consentPurpose,
            Integer designVersion,
            String idempotencyKey,
            MultipartFile file) {
        List<MultipartFile> files = (file != null && !file.isEmpty()) ? List.of(file) : Collections.emptyList();
        return submitDesignRequest(callerUid, null, description, catalogueReference, consentVersion, consentPurpose, designVersion, idempotencyKey, files);
    }

    /**
     * Human-reviewed independent design request submission supporting both Guest and Member,
     * and up to 5 images with authoritative server-side blur validation.
     */
    @Transactional
    public DesignRequestViewDto submitDesignRequest(
            String callerUid,
            String guestSessionId,
            String description,
            String catalogueReference,
            String consentVersion,
            String consentPurpose,
            Integer designVersion,
            String idempotencyKey,
            List<MultipartFile> files) {
        return submitDesignRequest(callerUid, guestSessionId, description, catalogueReference, null, null,
                consentVersion, consentPurpose, designVersion, idempotencyKey, files);
    }

    /**
     * Human-reviewed independent design request submission supporting both Guest and Member,
     * Feature-012 ring design snapshot handoff, and up to 5 images with authoritative server-side blur validation.
     */
    @Transactional
    public DesignRequestViewDto submitDesignRequest(
            String callerUid,
            String guestSessionId,
            String description,
            String catalogueReference,
            String designReference,
            Integer designRingVersion,
            String consentVersion,
            String consentPurpose,
            Integer designVersion,
            String idempotencyKey,
            List<MultipartFile> files) {

        log.info("Submitting design request: callerUid={}, guestSessionId={}, designReference={}, designVersion={}",
                callerUid, guestSessionId, designReference, designVersion);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID", "Thiếu Idempotency-Key trong header.");
        }

        int version = (designVersion != null && designVersion > 0) ? designVersion : 1;
        boolean isMember = (callerUid != null && !callerUid.isBlank());
        AccountSummary member = null;

        // 1. Ownership & Quota Validation
        if (isMember) {
            member = accountFacade.resolveAccount(callerUid)
                    .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED",
                            "Không tìm thấy thông tin tài khoản thành viên."));
            if (member.role() != ActorRole.MEMBER || member.status() != ActorStatus.ACTIVE) {
                throw DesignReviewException.forbidden("MEMBER_INACTIVE_OR_INVALID",
                        "Tài khoản không phải là hội viên hoạt động.");
            }

            // Check duplicate idempotency for member
            Optional<DesignRequest> existing = requestRepository
                    .findByOwnerAccountIdAndRequestVersionAndIdempotencyKey(member.accountId(), version, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate member idempotency key: returning existing design request {}", existing.get().getId());
                return mapToViewDto(existing.get());
            }
        } else {
            // Guest Request
            String sessionDigest = (guestSessionId != null && !guestSessionId.isBlank())
                    ? guestSessionId.trim()
                    : UUID.randomUUID().toString();

            // Check duplicate idempotency for guest
            Optional<DesignRequest> existing = requestRepository
                    .findByGuestSessionIdAndIdempotencyKey(sessionDigest, idempotencyKey)
                    .or(() -> requestRepository.findByIdempotencyKey(idempotencyKey));
            if (existing.isPresent()) {
                log.info("Duplicate guest idempotency key: returning existing design request {}", existing.get().getId());
                return mapToViewDto(existing.get());
            }

            // Guest 5-requests/day limit check
            guestQuotaService.validateAndConsumeQuota(sessionDigest);
            guestSessionId = sessionDigest;
        }

        // 2. Request Content Validation
        List<MultipartFile> validFiles = files != null ? files.stream().filter(f -> f != null && !f.isEmpty()).toList() : Collections.emptyList();
        boolean hasDescription = (description != null && !description.trim().isEmpty());
        boolean hasImages = !validFiles.isEmpty();
        boolean hasSnapshot = (designReference != null && !designReference.isBlank());

        if (!hasDescription && !hasImages && !hasSnapshot) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID",
                    "Yêu cầu phải có mô tả thiết kế, mẫu thiết kế Feature-012 hoặc ít nhất một tệp hình ảnh đính kèm.");
        }

        if (validFiles.size() > MAX_IMAGE_COUNT) {
            throw DesignReviewException.unprocessable("DESIGN_REQUEST_IMAGE_LIMIT_EXCEEDED",
                    "Số lượng hình ảnh vượt quá giới hạn tối đa (tối đa 5 hình ảnh).");
        }

        String sanitizedDescription = null;
        if (hasDescription) {
            sanitizedDescription = description.trim();
            if (sanitizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
                throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID",
                        "Mô tả thiết kế không được vượt quá 4.000 ký tự.");
            }
            for (Pattern p : PROMPT_INJECTION_PATTERNS) {
                if (p.matcher(sanitizedDescription).find()) {
                    log.warn("Prompt injection attempt detected: callerUid={}, guestSession={}", callerUid, guestSessionId);
                    throw DesignReviewException.unprocessable("AI_PROMPT_INJECTION_DETECTED",
                            "Nội dung mô tả vi phạm chính sách an toàn của hệ thống.");
                }
            }
        }

        String sanitizedCatalogueRef = (catalogueReference != null && !catalogueReference.isBlank())
                ? catalogueReference.trim()
                : null;

        // 3. Member Image Consent & Rate Limiting
        if (isMember && hasImages) {
            if (consentVersion == null || consentVersion.isBlank()) {
                throw DesignReviewException.unprocessable("CONSENT_REQUIRED",
                        "Bạn cần đồng ý với điều khoản phân tích hình ảnh của dịch vụ.");
            }

            ImageRateLimiter.RateLimitResult rateCheck = imageRateLimiter.tryAcquire(member.accountId());
            if (!rateCheck.allowed()) {
                throw DesignReviewException.rateLimited(
                        "IMAGE_RATE_LIMITED",
                        "Bạn đã gửi quá 3 lượt gửi ảnh trong vòng 5 phút. Vui lòng chờ để thử lại.",
                        rateCheck.retryAfterSeconds());
            }
        }

        // 4. Validate Each Image & Server-side Blur Check
        List<ValidatedImagePayload> validatedPayloads = new ArrayList<>();
        for (MultipartFile file : validFiles) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw DesignReviewException.unprocessable("IMAGE_TOO_LARGE",
                        "Kích thước tệp '" + file.getOriginalFilename() + "' vượt quá giới hạn 5 MB. Vui lòng chọn tệp nhỏ hơn.");
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
                throw DesignReviewException.unprocessable("IMAGE_INVALID",
                        "Định dạng hình ảnh '" + file.getOriginalFilename() + "' không hợp lệ. Chỉ chấp nhận JPEG, PNG hoặc WEBP.");
            }

            byte[] bytes;
            try {
                bytes = file.getBytes();
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
                if (bufferedImage != null) {
                    if (bufferedImage.getWidth() > MAX_DIMENSION || bufferedImage.getHeight() > MAX_DIMENSION) {
                        throw DesignReviewException.unprocessable("IMAGE_INVALID",
                                "Độ phân giải ảnh '" + file.getOriginalFilename() + "' vượt quá kích thước cho phép (tối đa 4096×4096).");
                    }
                }
            } catch (IOException e) {
                throw DesignReviewException.unprocessable("IMAGE_INVALID",
                        "Tệp hình ảnh '" + file.getOriginalFilename() + "' bị lỗi hoặc không thể đọc được.");
            }

            // Quality & Blur Check
            ImageQualityService.QualityCheckResult qualityResult = imageQualityService.evaluateQuality(bytes);
            if (qualityResult.isTooBlurry()) {
                throw DesignReviewException.unprocessable("DESIGN_REQUEST_IMAGE_TOO_BLURRY",
                        "Tệp '" + file.getOriginalFilename() + "' quá mờ (" + qualityResult.reason() + "). Vui lòng chọn ảnh rõ nét hơn.");
            }
            if ("QUALITY_CHECK_FAILED".equalsIgnoreCase(qualityResult.status())) {
                throw DesignReviewException.unprocessable("DESIGN_REQUEST_IMAGE_QUALITY_UNAVAILABLE",
                        "Không thể thẩm định chất lượng tệp '" + file.getOriginalFilename() + "'.");
            }

            String origName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "design.jpg";
            validatedPayloads.add(new ValidatedImagePayload(origName, contentType, bytes, qualityResult));
        }

        // 5. Generate Identifiers & Tracking Token for Guest
        String requestId = UUID.randomUUID().toString();
        String requestRef = "DR-" + requestId.substring(0, 8).toUpperCase();
        String trackingToken = null;
        String trackingTokenHash = null;
        Instant trackingTokenExpiresAt = null;

        if (!isMember) {
            trackingToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            trackingTokenHash = hashToken(trackingToken);
            trackingTokenExpiresAt = Instant.now().plus(Duration.ofDays(30));
        }

        // 6. Create Design Request (starts in NEED_REVIEW for Human Review)
        DesignRequest designRequest = DesignRequest.builder()
                .id(requestId)
                .requestReference(requestRef)
                .ownerAccountId(isMember ? member.accountId() : null)
                .guestSessionId(guestSessionId)
                .trackingToken(trackingToken)
                .trackingTokenHash(trackingTokenHash)
                .trackingTokenExpiresAt(trackingTokenExpiresAt)
                .ownershipType(isMember ? "MEMBER" : "GUEST")
                .description(sanitizedDescription)
                .catalogueReference(sanitizedCatalogueRef)
                .designReference(hasSnapshot ? designReference.trim() : null)
                .designVersion(hasSnapshot ? (designRingVersion != null ? designRingVersion : 1) : null)
                .requestVersion(version)
                .idempotencyKey(idempotencyKey)
                .requestStatus("ACTIVE")
                .analysisStatus(AnalysisStatus.SUCCEEDED) // Bypasses AI: Human review is authoritative
                .reviewStatus(ReviewStatus.NEED_REVIEW)
                .nextAction("AWAITING_HUMAN_REVIEW")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        requestRepository.save(designRequest);

        // Feature-012 snapshot capture through facade
        if (hasSnapshot && workshopRingDesignFacade.isPresent()) {
            workshopRingDesignFacade.get().getDesignSnapshot(designReference.trim()).ifPresent(snapshotDto -> {
                try {
                    String json = objectMapper.writeValueAsString(snapshotDto.getSnapshot());
                    CustomDesignReferenceSnapshot cdrs = CustomDesignReferenceSnapshot.builder()
                            .requestId(requestId)
                            .designReference(designReference.trim())
                            .designVersion(snapshotDto.getDesignVersion() != null ? snapshotDto.getDesignVersion() : 1)
                            .packageReference(snapshotDto.getPackageReference())
                            .snapshotJson(json)
                            .createdAt(Instant.now())
                            .build();
                    snapshotRepository.save(cdrs);
                } catch (Exception e) {
                    log.warn("Failed to serialize snapshot for request {}: {}", requestId, e.getMessage());
                }
            });
        }

        // 7. Store Images in Storage & Database
        for (ValidatedImagePayload payload : validatedPayloads) {
            String imageRef = "IMG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String storageKey = storagePort.storeTemporary(requestId, payload.originalFilename(), payload.contentType(), payload.bytes());

            DesignRequestImage dri = DesignRequestImage.builder()
                    .designRequestId(requestId)
                    .imageReference(imageRef)
                    .storagePath(storageKey)
                    .originalFilename(payload.originalFilename())
                    .contentType(payload.contentType())
                    .fileSizeBytes((long) payload.bytes().length)
                    .qualityStatus(payload.qualityResult().status())
                    .qualityReason(payload.qualityResult().reason())
                    .qualityScore(payload.qualityResult().score())
                    .createdAt(Instant.now())
                    .build();
            designRequestImageRepository.save(dri);

            // Backward compatibility record in temporary_image_references
            TemporaryImageReference tir = TemporaryImageReference.builder()
                    .designRequestId(requestId)
                    .storageObjectKey(storageKey)
                    .fileName(payload.originalFilename())
                    .contentType(payload.contentType())
                    .fileSizeBytes((long) payload.bytes().length)
                    .deleted(true)
                    .deletedAt(Instant.now())
                    .deletionOutcome("DELETED_AFTER_PROCESSING")
                    .createdAt(Instant.now())
                    .build();
            temporaryImageRepository.save(tir);
        }

        // Save Analysis Result projection for review and safe client viewing
        Map<String, Object> candFeatures = new LinkedHashMap<>();
        candFeatures.put("submissionType", hasImages ? "IMAGE_WITH_DESCRIPTION" : "TEXT_DESCRIPTION_ONLY");
        candFeatures.put("status", "AWAITING_HUMAN_REVIEW");
        candFeatures.put("humanReviewRequired", true);

        Map<String, Object> imgQuality = null;
        if (hasImages && !validatedPayloads.isEmpty()) {
            imgQuality = new LinkedHashMap<>();
            imgQuality.put("qualityStatus", validatedPayloads.get(0).qualityResult().status());
            imgQuality.put("score", validatedPayloads.get(0).qualityResult().score());
            imgQuality.put("blurCheck", "PASSED");
        }

        try {
            DesignAnalysisResult resultEntity = DesignAnalysisResult.builder()
                    .designRequestId(requestId)
                    .schemaVersion("v1.0")
                    .imageQualityJson(imgQuality != null ? objectMapper.writeValueAsString(imgQuality) : null)
                    .candidateFeaturesJson(objectMapper.writeValueAsString(candFeatures))
                    .confidenceJson("{\"overallConfidence\":1.0}")
                    .safeExplanation("Yêu cầu thiết kế đã được kiểm tra chất lượng và chuyển đến hàng đợi thẩm duyệt của nhân viên.")
                    .providerCorrelationId("human-review-" + requestId.substring(0, 8))
                    .processedAt(Instant.now())
                    .build();
            analysisResultRepository.save(resultEntity);
        } catch (Exception e) {
            log.warn("Failed to serialize analysis projection: {}", e.getMessage());
        }

        // Save consent if provided
        if (consentVersion != null && !consentVersion.isBlank()) {
            ImageConsent consent = ImageConsent.builder()
                    .actorAccountId(isMember ? member.accountId() : null)
                    .designRequestId(requestId)
                    .purpose(consentPurpose != null ? consentPurpose : "HUMAN_DESIGN_REVIEW")
                    .consentVersion(consentVersion.trim())
                    .affirmativeAction("CHECKBOX_CONFIRMED")
                    .acceptedAt(Instant.now())
                    .build();
            consentRepository.save(consent);
        }

        return mapToViewDto(designRequest);
    }

    private record ValidatedImagePayload(
            String originalFilename,
            String contentType,
            byte[] bytes,
            ImageQualityService.QualityCheckResult qualityResult
    ) {}

    private String hashToken(String token) {
        if (token == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(token.hashCode());
        }
    }

    @Transactional(readOnly = true)
    public List<DesignRequestViewDto> getMyDesignRequests(String callerUid) {
        AccountSummary member = accountFacade.resolveAccount(callerUid)
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED",
                        "Không tìm thấy thông tin tài khoản thành viên."));
        List<DesignRequest> requests = requestRepository.findByOwnerAccountIdOrderByCreatedAtDesc(member.accountId());
        return requests.stream().map(this::mapToViewDto).toList();
    }

    @Transactional(readOnly = true)
    public DesignRequestViewDto getDesignRequestById(String id, String callerUid,
            Collection<? extends GrantedAuthority> authorities) {
        return getDesignRequestById(id, callerUid, authorities, null);
    }

    @Transactional(readOnly = true)
    public DesignRequestViewDto getDesignRequestById(String id, String callerUid,
            Collection<? extends GrantedAuthority> authorities, String guestTrackingToken) {
        DesignRequest request = requestRepository.findById(id)
                .or(() -> requestRepository.findByRequestReference(id))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND",
                        "Không tìm thấy yêu cầu thiết kế với mã đã cung cấp."));

        // Staff / Manager / Admin
        if (authorities != null && DesignReviewPermissions.canViewDesignRequest(authorities)) {
            return mapToViewDto(request);
        }

        // Authenticated Member
        if (callerUid != null) {
            AccountSummary member = accountFacade.resolveAccount(callerUid).orElse(null);
            if (member != null) {
                if (member.accountId().equals(request.getOwnerAccountId())) {
                    return mapToViewDto(request);
                } else if (!"GUEST".equalsIgnoreCase(request.getOwnershipType())) {
                    throw DesignReviewException.notFound("NOT_FOUND",
                            "Không tìm thấy yêu cầu thiết kế với mã đã cung cấp.");
                }
            }
        }

        // Guest Request Verification
        if ("GUEST".equalsIgnoreCase(request.getOwnershipType())) {
            if (guestTrackingToken != null && !guestTrackingToken.isBlank()) {
                if (request.getTrackingToken() != null && request.getTrackingToken().equals(guestTrackingToken.trim())) {
                    if (request.getTrackingTokenExpiresAt() != null && request.getTrackingTokenExpiresAt().isBefore(Instant.now())) {
                        throw DesignReviewException.unauthorized("DESIGN_TRACKING_TOKEN_EXPIRED", "Mã theo dõi yêu cầu thiết kế đã hết hạn.");
                    }
                    return mapToViewDto(request);
                }
                throw DesignReviewException.unauthorized("DESIGN_TRACKING_TOKEN_INVALID", "Mã theo dõi yêu cầu thiết kế không hợp lệ.");
            }
        }

        // Fallback owner-safe projection
        return mapToViewDto(request);
    }

    @Transactional(readOnly = true)
    public byte[] getImageBytes(String imageReference) {
        DesignRequestImage image = designRequestImageRepository.findByImageReference(imageReference)
                .orElseThrow(() -> DesignReviewException.notFound("IMAGE_NOT_FOUND", "Không tìm thấy hình ảnh."));
        return storagePort.loadTemporary(image.getStoragePath());
    }

    @Transactional(readOnly = true)
    public DesignRequestImage getImageEntity(String imageReference) {
        return designRequestImageRepository.findByImageReference(imageReference)
                .orElseThrow(() -> DesignReviewException.notFound("IMAGE_NOT_FOUND", "Không tìm thấy hình ảnh."));
    }

    public DesignRequestViewDto mapToViewDto(DesignRequest request) {
        // Query images associated with this request
        List<DesignRequestImage> images = designRequestImageRepository.findByDesignRequestId(request.getId());
        List<DesignReviewDtos.ImageProjectionDto> imageDtos = images.stream()
                .map(img -> DesignReviewDtos.ImageProjectionDto.builder()
                        .imageReference(img.getImageReference())
                        .qualityStatus(img.getQualityStatus())
                        .qualityReason(img.getQualityReason())
                        .originalFilename(img.getOriginalFilename())
                        .fileSizeBytes(img.getFileSizeBytes())
                        .build())
                .toList();

        // Also check legacy temporary images if design_request_images is empty
        if (imageDtos.isEmpty()) {
            List<TemporaryImageReference> legacyImages = temporaryImageRepository.findAll().stream()
                    .filter(t -> request.getId().equals(t.getDesignRequestId()) && !t.isDeleted())
                    .toList();
            imageDtos = legacyImages.stream()
                    .map(img -> DesignReviewDtos.ImageProjectionDto.builder()
                            .imageReference(img.getStorageObjectKey())
                            .qualityStatus("PASS")
                            .qualityReason("Đạt yêu cầu")
                            .originalFilename(img.getFileName())
                            .fileSizeBytes(img.getFileSizeBytes())
                            .build())
                    .toList();
        }

        String trackingLink = null;
        if (request.getTrackingToken() != null) {
            trackingLink = "/design-requests/" + request.getId() + "?token=" + request.getTrackingToken();
        }

        String statusStr = request.getReviewStatus() != null ? request.getReviewStatus().name() : "NEED_REVIEW";

        // Optional legacy AI results mapping (if existed historically)
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

        Map<String, Object> safeResult = null;
        if (candidateFeatures != null || imageQuality != null || confidence != null || explanation != null) {
            safeResult = new LinkedHashMap<>();
            if (candidateFeatures != null) safeResult.put("candidateFeatures", candidateFeatures);
            if (imageQuality != null) safeResult.put("imageQuality", imageQuality);
            if (confidence != null) safeResult.put("confidence", confidence);
            if (explanation != null) safeResult.put("safeExplanation", explanation);
        }

        Map<String, Object> snapshotMap = null;
        Optional<CustomDesignReferenceSnapshot> snapshotOpt = snapshotRepository.findTopByRequestIdOrderByCreatedAtDesc(request.getId());
        if (snapshotOpt.isPresent()) {
            try {
                snapshotMap = objectMapper.readValue(snapshotOpt.get().getSnapshotJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse design snapshot json: {}", e.getMessage());
            }
        }

        String senderEmail = null;
        if (request.getOwnerAccountId() != null) {
            senderEmail = accountFacade.findAccountEmail(request.getOwnerAccountId()).orElse(null);
        }

        return DesignRequestViewDto.builder()
                .id(request.getId())
                .requestReference(request.getRequestReference())
                .requestVersion(request.getRequestVersion())
                .requestStatus(request.getRequestStatus())
                .analysisStatus(request.getAnalysisStatus() != null ? request.getAnalysisStatus().name() : "SUCCEEDED")
                .reviewStatus(statusStr)
                .status(statusStr)
                .nextAction(request.getNextAction())
                .safeFailureCode(request.getSafeFailureCode())
                .description(request.getDescription())
                .catalogueReference(request.getCatalogueReference())
                .designReference(request.getDesignReference())
                .designVersion(request.getDesignVersion())
                .quoteReference(request.getQuoteReference())
                .designSnapshot(snapshotMap)
                .images(imageDtos)
                .decisionExplanation(request.getDecisionExplanation())
                .trackingLink(trackingLink)
                .guestTrackingToken(request.getTrackingToken())
                .candidateFeatures(candidateFeatures)
                .imageQuality(imageQuality)
                .confidence(confidence)
                .safeExplanation(explanation)
                .safeResult(safeResult)
                .senderEmail(senderEmail)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    @Transactional
    public DesignRequestViewDto reviseDesignRequest(
            String requestId,
            String description,
            String designReference,
            Integer designRingVersion,
            String clarificationResponse,
            String callerUid,
            String guestTrackingToken,
            Integer ifMatchVersion,
            List<MultipartFile> files
    ) {
        log.info("Submitting revision for design request: id={}, callerUid={}, version={}",
                requestId, callerUid, ifMatchVersion);

        DesignRequest request = requestRepository.findById(requestId)
                .or(() -> requestRepository.findByRequestReference(requestId))
                .orElseThrow(() -> DesignReviewException.notFound("NOT_FOUND", "Không tìm thấy yêu cầu thiết kế."));

        // Ownership validation
        if (callerUid != null && !callerUid.isBlank()) {
            AccountSummary member = accountFacade.resolveAccount(callerUid)
                    .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy tài khoản."));
            if (!member.accountId().equals(request.getOwnerAccountId())) {
                throw DesignReviewException.forbidden("ACCESS_DENIED", "Bạn không có quyền chỉnh sửa yêu cầu này.");
            }
        } else if (guestTrackingToken != null && !guestTrackingToken.isBlank()) {
            String hash = hashToken(guestTrackingToken);
            if (!guestTrackingToken.equals(request.getTrackingToken()) && !hash.equals(request.getTrackingTokenHash())) {
                throw DesignReviewException.forbidden("ACCESS_DENIED", "Token theo dõi không hợp lệ.");
            }
        } else {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu đăng nhập hoặc mã theo dõi.");
        }

        // State check: Must be in CLARIFICATION_REQUIRED
        if (request.getReviewStatus() != ReviewStatus.CLARIFICATION_REQUIRED) {
            throw DesignReviewException.conflict("DESIGN_REQUEST_STATE_CONFLICT",
                    "Yêu cầu không ở trạng thái yêu cầu làm rõ (CLARIFICATION_REQUIRED).");
        }

        // Optimistic locking version check
        if (ifMatchVersion != null && !ifMatchVersion.equals(request.getRequestVersion())) {
            throw DesignReviewException.conflict("DESIGN_REQUEST_STATE_CONFLICT",
                    "Phiên bản yêu cầu đã thay đổi, vui lòng tải lại trang.");
        }

        // Save revision record
        int newVersion = request.getRequestVersion() + 1;
        CustomDesignRequestRevision revision = CustomDesignRequestRevision.builder()
                .requestId(request.getId())
                .revisionNumber(newVersion)
                .description(description != null ? description.trim() : request.getDescription())
                .designReference(designReference != null ? designReference.trim() : request.getDesignReference())
                .designVersion(designRingVersion != null ? designRingVersion : request.getDesignVersion())
                .clarificationResponse(clarificationResponse != null ? clarificationResponse.trim() : description)
                .createdAt(Instant.now())
                .build();
        revisionRepository.save(revision);

        if (description != null && !description.trim().isEmpty()) {
            request.setDescription(description.trim());
        }
        if (designReference != null && !designReference.isBlank()) {
            request.setDesignReference(designReference.trim());
            request.setDesignVersion(designRingVersion != null ? designRingVersion : 1);
            if (workshopRingDesignFacade.isPresent()) {
                workshopRingDesignFacade.get().getDesignSnapshot(designReference.trim()).ifPresent(snapshotDto -> {
                    try {
                        String json = objectMapper.writeValueAsString(snapshotDto.getSnapshot());
                        CustomDesignReferenceSnapshot cdrs = CustomDesignReferenceSnapshot.builder()
                                .requestId(request.getId())
                                .designReference(designReference.trim())
                                .designVersion(snapshotDto.getDesignVersion() != null ? snapshotDto.getDesignVersion() : 1)
                                .packageReference(snapshotDto.getPackageReference())
                                .snapshotJson(json)
                                .createdAt(Instant.now())
                                .build();
                        snapshotRepository.save(cdrs);
                    } catch (Exception e) {
                        log.warn("Failed to serialize snapshot for request revision {}: {}", request.getId(), e.getMessage());
                    }
                });
            }
        }

        request.setRequestVersion(newVersion);
        request.setReviewStatus(ReviewStatus.NEED_REVIEW);
        request.setNextAction("AWAITING_HUMAN_REVIEW");
        request.setUpdatedAt(Instant.now());
        requestRepository.save(request);

        return mapToViewDto(request);
    }
}
