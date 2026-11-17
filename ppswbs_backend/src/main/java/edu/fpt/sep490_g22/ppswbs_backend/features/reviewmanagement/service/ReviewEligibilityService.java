package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.GuestEligibilityHandleRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.GuestEligibilityHandle;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.EligibleSourceDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.EligibleSourceListResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.GuestEligibilityLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.EligibleSourceItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.RetailFulfilmentReviewPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.WorkshopAttendanceReviewPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewEligibilityService {

    private final WorkshopAttendanceReviewPort workshopAttendancePort;
    private final RetailFulfilmentReviewPort retailFulfilmentPort;
    private final GuestEligibilityHandleRepository handleRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewMaskingService maskingService;
    private final ObjectMapper objectMapper;
    private final ReviewIdempotencyService idempotencyService;

    private static final int HANDLE_TTL_MINUTES = 30;

    @org.springframework.beans.factory.annotation.Autowired
    public ReviewEligibilityService(WorkshopAttendanceReviewPort workshopAttendancePort,
                                    RetailFulfilmentReviewPort retailFulfilmentPort,
                                    GuestEligibilityHandleRepository handleRepository,
                                    ReviewRepository reviewRepository,
                                    ReviewMaskingService maskingService,
                                    ObjectMapper objectMapper,
                                    ReviewIdempotencyService idempotencyService) {
        this.workshopAttendancePort = workshopAttendancePort;
        this.retailFulfilmentPort = retailFulfilmentPort;
        this.handleRepository = handleRepository;
        this.reviewRepository = reviewRepository;
        this.maskingService = maskingService;
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
    }

    @Deprecated
    public ReviewEligibilityService(WorkshopAttendanceReviewPort workshopAttendancePort,
                                    RetailFulfilmentReviewPort retailFulfilmentPort,
                                    GuestEligibilityHandleRepository handleRepository,
                                    ReviewRepository reviewRepository,
                                    ReviewMaskingService maskingService,
                                    ObjectMapper objectMapper) {
        this(workshopAttendancePort, retailFulfilmentPort, handleRepository, reviewRepository,
                maskingService, objectMapper, null);
    }

    @Deprecated
    public EligibleSourceListResponse lookupGuestEligibility(GuestEligibilityLookupRequest request) {
        return lookupGuestEligibility(request, null);
    }

    @Transactional
    public EligibleSourceListResponse lookupGuestEligibility(GuestEligibilityLookupRequest request, String idempotencyKey) {
        if (request == null || request.getContactType() == null || request.getContactValue() == null || request.getContactValue().isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "Thông tin liên hệ không được để trống.");
        }

        ContactType contactType = request.getContactType();
        String contactValue = request.getContactValue().trim();

        String actorScope = "GUEST_LOOKUP_" + contactType.name() + "_" + hashHandle(contactValue.toLowerCase());
        String requestHash = idempotencyService != null ? idempotencyService.computeHash(request) : null;
        Optional<EligibleSourceListResponse> existing = idempotencyService != null
                ? idempotencyService.findCompletedResponse(idempotencyKey, actorScope, requestHash, EligibleSourceListResponse.class)
                : Optional.empty();
        if (existing.isPresent()) return existing.get();
        if (idempotencyService != null) idempotencyService.recordPending(idempotencyKey, "GUEST_ELIGIBILITY_LOOKUP", actorScope, requestHash);

        List<EligibleSourceItem> items = new ArrayList<>();
        items.addAll(workshopAttendancePort.findEligibleByContact(contactType, contactValue));
        items.addAll(retailFulfilmentPort.findEligibleByContact(contactType, contactValue));
        if (items.isEmpty()) {
            EligibleSourceListResponse response = EligibleSourceListResponse.builder()
                    .guestEligibilityHandle(null)
                    .expiresAt(null)
                    .maskedContact(maskingService.maskContact(contactType, contactValue))
                    .sources(Collections.emptyList())
                    .build();
            if (idempotencyService != null) idempotencyService.recordSuccess(idempotencyKey, actorScope, response);
            return response;
        }

        // Kiểm tra các item đã được review chưa
        Set<String> reviewedSet = new HashSet<>();
        for (SourceType sourceType : SourceType.values()) {
            List<String> refs = items.stream().filter(item -> item.getSourceType() == sourceType)
                    .map(EligibleSourceItem::getSourceReference).toList();
            if (!refs.isEmpty()) {
                reviewedSet.addAll(reviewRepository.findBySourceTypeAndSourceReferenceIn(sourceType, refs)
                        .stream().map(review -> sourceKey(sourceType, review.getSourceReference())).collect(Collectors.toSet()));
            }
        }

        List<EligibleSourceDto> dtos = new ArrayList<>();
        List<String> unreviewedRefs = new ArrayList<>();

        for (EligibleSourceItem item : items) {
            boolean alreadyReviewed = reviewedSet.contains(sourceKey(item.getSourceType(), item.getSourceReference()));
            if (!alreadyReviewed) {
                unreviewedRefs.add(sourceKey(item.getSourceType(), item.getSourceReference()));
            }
            dtos.add(EligibleSourceDto.builder()
                    .sourceType(item.getSourceType())
                    .sourceReference(item.getSourceReference())
                    .targetType(item.getTargetType())
                    .targetReference(item.getTargetReference())
                    .targetName(item.getTargetName())
                    .completedAt(item.getCompletedAt())
                    .eligible(item.isEligible())
                    .alreadyReviewed(alreadyReviewed)
                    .reasonCode(alreadyReviewed ? "ALREADY_REVIEWED" : null)
                    .build());
        }

        String rawHandle = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String handleHash = hashHandle(rawHandle);
        Instant expiresAt = Instant.now().plus(HANDLE_TTL_MINUTES, ChronoUnit.MINUTES);
        String maskedContact = maskingService.maskContact(contactType, contactValue);

        try {
            String refsJson = objectMapper.writeValueAsString(unreviewedRefs);
            GuestEligibilityHandle handleEntity = GuestEligibilityHandle.builder()
                    .id(UUID.randomUUID().toString())
                    .handleHash(handleHash)
                    .contactType(contactType)
                    .contactValueMasked(maskedContact)
                    .sourceReferencesJson(refsJson)
                    .expiresAt(expiresAt)
                    .createdAt(Instant.now())
                    .build();
            handleRepository.save(handleEntity);
        } catch (Exception ex) {
            log.error("Failed to save guest eligibility handle", ex);
            throw new RuntimeException("Lỗi lưu trữ guest handle", ex);
        }

        EligibleSourceListResponse response = EligibleSourceListResponse.builder()
                .guestEligibilityHandle(rawHandle)
                .expiresAt(expiresAt)
                .maskedContact(maskedContact)
                .sources(dtos)
                .build();
        if (idempotencyService != null) idempotencyService.recordSuccess(idempotencyKey, actorScope, response);
        return response;
    }

    @Transactional(readOnly = true)
    public EligibleSourceListResponse getMemberEligibleSources(Long memberAccountId) {
        if (memberAccountId == null) {
            throw ReviewManagementException.unauthorized("REVIEW_NOT_AUTHORIZED", "Yêu cầu đăng nhập tài khoản hội viên.");
        }

        List<EligibleSourceItem> workshopItems = workshopAttendancePort.findEligibleByMemberId(memberAccountId);
        List<EligibleSourceItem> retailItems = retailFulfilmentPort.findEligibleByMemberId(memberAccountId);

        List<EligibleSourceItem> allItems = new ArrayList<>();
        allItems.addAll(workshopItems);
        allItems.addAll(retailItems);

        if (allItems.isEmpty()) {
            return EligibleSourceListResponse.builder()
                    .guestEligibilityHandle(null)
                    .expiresAt(null)
                    .maskedContact(maskingService.maskMemberDisplayName(memberAccountId, null))
                    .sources(Collections.emptyList())
                    .build();
        }

        // Kiểm tra workshop đã review
        List<String> workshopRefs = workshopItems.stream().map(EligibleSourceItem::getSourceReference).toList();
        List<Review> existingWorkshopReviews = workshopRefs.isEmpty() ? Collections.emptyList() :
                reviewRepository.findBySourceTypeAndSourceReferenceIn(SourceType.WORKSHOP_ATTENDANCE, workshopRefs);
        Set<String> reviewedWorkshopSet = existingWorkshopReviews.stream().map(Review::getSourceReference).collect(Collectors.toSet());

        // Kiểm tra retail đã review
        List<String> retailRefs = retailItems.stream().map(EligibleSourceItem::getSourceReference).toList();
        List<Review> existingRetailReviews = retailRefs.isEmpty() ? Collections.emptyList() :
                reviewRepository.findBySourceTypeAndSourceReferenceIn(SourceType.RETAIL_ORDER_LINE, retailRefs);
        Set<String> reviewedRetailSet = existingRetailReviews.stream().map(Review::getSourceReference).collect(Collectors.toSet());

        List<EligibleSourceDto> dtos = new ArrayList<>();
        for (EligibleSourceItem item : allItems) {
            boolean already = item.getSourceType() == SourceType.WORKSHOP_ATTENDANCE
                    ? reviewedWorkshopSet.contains(item.getSourceReference())
                    : reviewedRetailSet.contains(item.getSourceReference());

            dtos.add(EligibleSourceDto.builder()
                    .sourceType(item.getSourceType())
                    .sourceReference(item.getSourceReference())
                    .targetType(item.getTargetType())
                    .targetReference(item.getTargetReference())
                    .targetName(item.getTargetName())
                    .completedAt(item.getCompletedAt())
                    .eligible(item.isEligible())
                    .alreadyReviewed(already)
                    .reasonCode(already ? "ALREADY_REVIEWED" : null)
                    .build());
        }

        return EligibleSourceListResponse.builder()
                .guestEligibilityHandle(null)
                .expiresAt(null)
                .maskedContact(maskingService.maskMemberDisplayName(memberAccountId, null))
                .sources(dtos)
                .build();
    }

    public GuestEligibilityHandle validateGuestHandle(String guestHandle, SourceType expectedSourceType, String expectedSourceReference) {
        if (guestHandle == null || guestHandle.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_GUEST_HANDLE_INVALID", "Mã handle của khách vãng lai không hợp lệ.");
        }
        String hash = hashHandle(guestHandle.trim());
        GuestEligibilityHandle handle = handleRepository.findByHandleHash(hash)
                .orElseThrow(() -> ReviewManagementException.badRequest("REVIEW_GUEST_HANDLE_INVALID", "Mã handle không tồn tại hoặc không hợp lệ."));

        if (handle.isExpired()) {
            throw ReviewManagementException.unauthorized("REVIEW_GUEST_HANDLE_EXPIRED", "Mã handle đã hết hạn (30 phút). Vui lòng tra cứu lại.");
        }

        if (expectedSourceReference != null && !expectedSourceReference.isBlank()) {
            try {
                List<String> validRefs = objectMapper.readValue(handle.getSourceReferencesJson(), new TypeReference<List<String>>() {});
                String expectedKey = sourceKey(expectedSourceType, expectedSourceReference.trim());
                if (!validRefs.contains(expectedKey)) {
                    throw ReviewManagementException.unprocessable("REVIEW_SOURCE_NOT_ELIGIBLE", "Mã tham chiếu không thuộc phiên tra cứu hợp lệ.");
                }
            } catch (ReviewManagementException rme) {
                throw rme;
            } catch (Exception ex) {
                log.error("Failed to parse guest handle references JSON", ex);
                throw ReviewManagementException.badRequest("REVIEW_GUEST_HANDLE_INVALID", "Dữ liệu handle không hợp lệ.");
            }
        }

        return handle;
    }

    public GuestEligibilityHandle validateGuestHandle(String guestHandle, String expectedSourceReference) {
        return validateGuestHandle(guestHandle, SourceType.WORKSHOP_ATTENDANCE, expectedSourceReference);
    }

    private String sourceKey(SourceType sourceType, String sourceReference) {
        return (sourceType != null ? sourceType.name() : "UNKNOWN") + ":" + sourceReference;
    }

    public String hashHandle(String rawHandle) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawHandle.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to hash handle", ex);
        }
    }
}

