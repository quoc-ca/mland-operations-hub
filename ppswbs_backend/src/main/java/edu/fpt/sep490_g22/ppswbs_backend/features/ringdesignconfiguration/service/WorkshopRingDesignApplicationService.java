package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingCatalogueModelRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDesignRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.DesignOptionRulesPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopRingDesignApplicationService {

    private final RingDesignRepository designRepository;
    private final RingCatalogueModelRepository catalogueModelRepository;
    private final GuestDesignSessionService guestSessionService;
    private final RingDesignIdempotencyService idempotencyService;
    private final DesignOptionRulesPort rulesPort;
    private final ObjectMapper objectMapper;

    @Transactional
    public DesignResponseDto createDraft(
            CreateDesignRequest request,
            Long memberAccountId,
            String guestSessionHeader,
            String idempotencyKey) {
        String actorScope = resolveActorScope(memberAccountId, guestSessionHeader);

        // Check idempotency replay
        var replay = idempotencyService.checkReplay(idempotencyKey, "CREATE_DESIGN", actorScope, request);
        if (replay.isPresent()) {
            return idempotencyService.deserialize(replay.get().getResponseBody(), DesignResponseDto.class);
        }

        ActorBinding binding = resolveActorBinding(memberAccountId, guestSessionHeader);

        String designReference = "DSG-" + UUID.randomUUID();
        String materialRef = "MAT-SILVER-925";
        String shapeRef = "SHP-CLASSIC-ROUND";
        String ringSize = "8";
        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("components", Collections.emptyList());
        snapshotMap.put("attachments", Collections.emptyList());
        snapshotMap.put("engraving", Map.of("enabled", false));

        if ("PUBLISHED_MODEL".equalsIgnoreCase(request.getStartMode())) {
            if (request.getSourceModelReference() == null || request.getSourceModelReference().isBlank()) {
                throw RingDesignException.badRequest("DESIGN_INPUT_INVALID",
                        "sourceModelReference bắt buộc khi chọn startMode = PUBLISHED_MODEL.");
            }
            RingCatalogueModel model = catalogueModelRepository
                    .findByModelCode(request.getSourceModelReference().trim())
                    .orElseThrow(() -> RingDesignException.businessInvalid("DESIGN_OPTION_INVALID",
                            "Mẫu thiết kế catalogue không tồn tại.", List.of(request.getSourceModelReference())));
            materialRef = model.getMaterialReference();
            shapeRef = model.getShapeReference();
            if (model.getDefaultComponents() != null) {
                try {
                    snapshotMap = objectMapper.readValue(model.getDefaultComponents(), new TypeReference<>() {
                    });
                } catch (Exception ignored) {
                }
            }
        }

        List<ComponentSelectionDto> initialComponents = Collections.emptyList();
        List<AttachmentSelectionDto> initialAttachments = Collections.emptyList();
        if (snapshotMap.get("components") instanceof List<?> list) {
            try {
                initialComponents = objectMapper.convertValue(list, new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }
        if (snapshotMap.get("attachments") instanceof List<?> list) {
            try {
                initialAttachments = objectMapper.convertValue(list, new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        SaveDesignVersionRequest dummyValidationReq = SaveDesignVersionRequest.builder()
                .packageReference(request.getPackageReference())
                .materialReference(materialRef)
                .shapeReference(shapeRef)
                .ringSize(ringSize)
                .components(initialComponents)
                .attachments(initialAttachments)
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build();

        EvaluationResultDto eval = rulesPort.evaluate(dummyValidationReq);
        ValidationResultDto val = rulesPort.validateCompatibility(dummyValidationReq, null);

        RingDesign initialDesign = RingDesign.builder()
                .designReference(designReference)
                .designVersion(1)
                .designName(
                        "PUBLISHED_MODEL".equalsIgnoreCase(request.getStartMode()) ? request.getSourceModelReference()
                                : "Thiết kế nhẫn tùy chỉnh")
                .designType(RingDesignType.configured_design)
                .ownerType(binding.ownerType)
                .ownerReference(binding.ownerReference)
                .packageReference(request.getPackageReference())
                .sourceModelReference(request.getSourceModelReference())
                .materialReference(materialRef)
                .shapeReference(shapeRef)
                .ringSize(ringSize)
                .componentSnapshot(serializeSnapshot(snapshotMap))
                .estimatedPrice(
                        eval.getEstimatedPrice() != null ? new BigDecimal(eval.getEstimatedPrice()) : BigDecimal.ZERO)
                .currency(eval.getCurrency())
                .difficultyScore(
                        eval.getDifficultyScore() != null ? new BigDecimal(eval.getDifficultyScore()) : BigDecimal.ONE)
                .rulesVersion(eval.getRulesVersion())
                .policyVersionId(eval.getPolicyVersionId())
                .difficultyLevel(eval.getDifficultyLevel() != null ? eval.getDifficultyLevel().name() : null)
                .recommendationCode(eval.getRecommendationCode() != null ? eval.getRecommendationCode().name() : null)
                .recommendedPackageReference(eval.getRecommendedPackageReference())
                .materialDisclosure(eval.getMaterialDisclosure())
                .evaluatedAt(java.time.Instant.now())
                .status(RingDesignStatus.DRAFT)
                .build();

        RingDesign saved = designRepository.save(initialDesign);

        DesignResponseDto response = mapToResponse(saved, val, eval, snapshotMap);
        if (binding.ownerType == RingDesignOwnerType.GUEST_SESSION && binding.guestSession != null) {
            binding.guestSession.setDesignReference(designReference);
            response.setGuestSession(binding.guestSession.getSessionReference());
        }
        idempotencyService.record(idempotencyKey, "CREATE_DESIGN", actorScope, request, 201, response);
        return response;
    }

    @Transactional
    public List<DesignResponseDto> listMyDesigns(Long memberAccountId, String guestSessionHeader) {
        return listMyDesigns(memberAccountId, guestSessionHeader, false);
    }

    @Transactional
    public List<DesignResponseDto> listMyDesigns(Long memberAccountId, String guestSessionHeader,
            boolean includeArchived) {
        if (memberAccountId == null) {
            throw RingDesignException.unauthorized("DESIGN_MEMBER_AUTH_REQUIRED",
                    "Yêu cầu đăng nhập tài khoản hội viên để xem danh sách thiết kế.");
        }
        List<RingDesign> allDesigns = new ArrayList<>(
                designRepository.findAllByOwner(RingDesignOwnerType.MEMBER, String.valueOf(memberAccountId)));

        if (allDesigns.isEmpty()) {
            return Collections.emptyList();
        }

        // Group by designReference and pick the latest version for each design
        Map<String, RingDesign> latestByRef = new LinkedHashMap<>();
        for (RingDesign d : allDesigns) {
            RingDesign existing = latestByRef.get(d.getDesignReference());
            if (existing == null || d.getDesignVersion() > existing.getDesignVersion()) {
                latestByRef.put(d.getDesignReference(), d);
            }
        }

        List<RingDesign> latestList = new ArrayList<>();
        for (RingDesign d : latestByRef.values()) {
            if (includeArchived || d.getStatus() != RingDesignStatus.ARCHIVED) {
                latestList.add(d);
            }
        }
        latestList.sort((a, b) -> {
            if (b.getCreatedAt() != null && a.getCreatedAt() != null) {
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            }
            return Long.compare(b.getId() != null ? b.getId() : 0L, a.getId() != null ? a.getId() : 0L);
        });

        return latestList.stream().map(d -> {
            Map<String, Object> snapshot = deserializeSnapshot(d.getComponentSnapshot());
            EvaluationResultDto eval = buildPersistedEvaluation(d);
            ValidationResultDto val = ValidationResultDto.builder().valid(true).reasons(Collections.emptyList())
                    .build();
            return mapToResponse(d, val, eval, snapshot);
        }).toList();
    }

    @Transactional
    public DesignResponseDto getDesign(String designId, Long memberAccountId, String guestSessionHeader) {
        RingDesign latest = designRepository.findLatestByDesignReference(designId)
                .orElseThrow(() -> RingDesignException.notFound("Không tìm thấy thiết kế nhẫn: " + designId));

        verifyOwnership(latest, memberAccountId, guestSessionHeader);

        Map<String, Object> snapshot = deserializeSnapshot(latest.getComponentSnapshot());
        SaveDesignVersionRequest mockReq = buildRequestFromDesign(latest, snapshot);
        ValidationResultDto val = rulesPort.validateCompatibility(mockReq, null);
        EvaluationResultDto eval = buildPersistedEvaluation(latest);

        return mapToResponse(latest, val, eval, snapshot);
    }

    private EvaluationResultDto buildPersistedEvaluation(RingDesign d) {
        DifficultyLevel level = null;
        if (d.getDifficultyLevel() != null) {
            try {
                level = DifficultyLevel.valueOf(d.getDifficultyLevel());
            } catch (IllegalArgumentException ignored) {
            }
        }
        RecommendationCode recCode = null;
        if (d.getRecommendationCode() != null) {
            try {
                recCode = RecommendationCode.valueOf(d.getRecommendationCode());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return EvaluationResultDto.builder()
                .policyVersionId(d.getPolicyVersionId())
                .estimatedPrice(d.getEstimatedPrice() != null ? d.getEstimatedPrice().toPlainString() : "0.00")
                .difficultyScore(d.getDifficultyScore() != null ? d.getDifficultyScore().toPlainString() : "1.0000")
                .difficultyLevel(level)
                .recommendationCode(recCode)
                .recommendedPackageReference(d.getRecommendedPackageReference())
                .materialDisclosure(d.getMaterialDisclosure())
                .rulesVersion(d.getRulesVersion() != null ? d.getRulesVersion() : "2026.11-v1.0")
                .currency(d.getCurrency() != null ? d.getCurrency() : "VND")
                .advisory(true)
                .build();
    }

    @Transactional(readOnly = true)
    public WorkshopRingDesignTechnicalProjectionDto getTechnicalProjection(String designId) {
        RingDesign latest = designRepository.findLatestByDesignReference(designId)
                .orElseThrow(() -> RingDesignException.notFound("Không tìm thấy thiết kế nhẫn: " + designId));
        return WorkshopRingDesignTechnicalProjectionDto.builder()
                .designId(latest.getDesignReference())
                .packageReference(latest.getPackageReference())
                .designVersion(latest.getDesignVersion())
                .designType(latest.getDesignType() != null ? latest.getDesignType().name() : null)
                .status(latest.getStatus() != null ? latest.getStatus().name() : null)
                .materialReference(latest.getMaterialReference())
                .shapeReference(latest.getShapeReference())
                .ringSize(latest.getRingSize())
                .difficultyScore(latest.getDifficultyScore())
                .rulesVersion(latest.getRulesVersion())
                .isLocked(latest.getBookingReference() != null && !latest.getBookingReference().isBlank())
                .createdAt(latest.getCreatedAt())
                .updatedAt(latest.getUpdatedAt())
                .build();
    }

    @Transactional
    public DesignResponseDto saveVersion(
            String designId,
            SaveDesignVersionRequest request,
            Long memberAccountId,
            String guestSessionHeader,
            Long ifMatchVersion,
            String idempotencyKey) {
        String actorScope = resolveActorScope(memberAccountId, guestSessionHeader);
        var replay = idempotencyService.checkReplay(idempotencyKey, "SAVE_VERSION", actorScope, request);
        if (replay.isPresent()) {
            return idempotencyService.deserialize(replay.get().getResponseBody(), DesignResponseDto.class);
        }

        RingDesign latest = designRepository.findLatestByDesignReference(designId)
                .orElseThrow(() -> RingDesignException.notFound("Không tìm thấy thiết kế nhẫn: " + designId));

        verifyOwnership(latest, memberAccountId, guestSessionHeader);

        if (latest.getStatus() == RingDesignStatus.LOCKED || latest.getStatus() == RingDesignStatus.ARCHIVED) {
            throw RingDesignException.conflict("DESIGN_LOCKED",
                    "Thiết kế nhẫn đã bị khóa hoặc lưu trữ, không thể cập nhật phiên bản mới.");
        }

        if (ifMatchVersion != null && !latest.getDesignVersion().equals(ifMatchVersion.intValue())) {
            throw RingDesignException
                    .versionConflict("Phiên bản thiết kế không khớp (stale version). Vui lòng tải lại trước khi lưu.");
        }

        // Validate compatibility
        ValidationResultDto val = rulesPort.validateCompatibility(request, null);
        if (!val.isValid()) {
            String errorCode = determineValidationErrorCode(val.getReasons());
            throw RingDesignException.businessInvalid(errorCode,
                    "Tùy chọn thiết kế không hợp lệ hoặc không tương thích.", val.getReasons());
        }

        EvaluationResultDto eval = rulesPort.evaluate(request);

        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("materialReference", request.getMaterialReference());
        snapshotMap.put("shapeReference", request.getShapeReference());
        snapshotMap.put("ringSize", request.getRingSize());
        snapshotMap.put("thicknessMm", request.getThicknessMm());
        snapshotMap.put("bandWidthMm", request.getBandWidthMm());
        snapshotMap.put("components", request.getComponents());
        snapshotMap.put("attachments", request.getAttachments());
        snapshotMap.put("engraving", request.getEngraving());
        snapshotMap.put("gemstonePositions", request.getGemstonePositions());

        RingDesignOwnerType targetOwnerType = memberAccountId != null ? RingDesignOwnerType.MEMBER : latest.getOwnerType();
        String targetOwnerReference = memberAccountId != null ? String.valueOf(memberAccountId) : latest.getOwnerReference();

        // If a member is saving an existing guest design, claim historical versions too
        if (memberAccountId != null && latest.getOwnerType() == RingDesignOwnerType.GUEST_SESSION) {
            latest.setOwnerType(RingDesignOwnerType.MEMBER);
            latest.setOwnerReference(String.valueOf(memberAccountId));
            designRepository.save(latest);
        }

        RingDesign targetToSave;
        if (latest.getStatus() == RingDesignStatus.DRAFT && latest.getDesignVersion() == 1) {
            // Initial draft save: update draft in place to version 1 with SAVED status
            latest.setOwnerType(targetOwnerType);
            latest.setOwnerReference(targetOwnerReference);
            latest.setPackageReference(request.getPackageReference());
            latest.setMaterialReference(request.getMaterialReference());
            latest.setShapeReference(request.getShapeReference());
            latest.setRingSize(request.getRingSize());
            latest.setComponentSnapshot(serializeSnapshot(snapshotMap));
            latest.setEstimatedPrice(new BigDecimal(eval.getEstimatedPrice()));
            latest.setCurrency(eval.getCurrency());
            latest.setDifficultyScore(new BigDecimal(eval.getDifficultyScore()));
            latest.setRulesVersion(eval.getRulesVersion());
            latest.setPolicyVersionId(eval.getPolicyVersionId());
            latest.setDifficultyLevel(eval.getDifficultyLevel() != null ? eval.getDifficultyLevel().name() : null);
            latest.setRecommendationCode(eval.getRecommendationCode() != null ? eval.getRecommendationCode().name() : null);
            latest.setRecommendedPackageReference(eval.getRecommendedPackageReference());
            latest.setMaterialDisclosure(eval.getMaterialDisclosure());
            latest.setEvaluatedAt(java.time.Instant.now());
            latest.setStatus(RingDesignStatus.SAVED);
            targetToSave = latest;
        } else {
            targetToSave = RingDesign.builder()
                    .designReference(latest.getDesignReference())
                    .designVersion(latest.getDesignVersion() + 1)
                    .designName(latest.getDesignName())
                    .designType(latest.getDesignType())
                    .ownerType(targetOwnerType)
                    .ownerReference(targetOwnerReference)
                    .packageReference(request.getPackageReference())
                    .sourceModelReference(latest.getSourceModelReference())
                    .sourceDesign(latest)
                    .materialReference(request.getMaterialReference())
                    .shapeReference(request.getShapeReference())
                    .ringSize(request.getRingSize())
                    .componentSnapshot(serializeSnapshot(snapshotMap))
                    .estimatedPrice(new BigDecimal(eval.getEstimatedPrice()))
                    .currency(eval.getCurrency())
                    .difficultyScore(new BigDecimal(eval.getDifficultyScore()))
                    .rulesVersion(eval.getRulesVersion())
                    .policyVersionId(eval.getPolicyVersionId())
                    .difficultyLevel(eval.getDifficultyLevel() != null ? eval.getDifficultyLevel().name() : null)
                    .recommendationCode(eval.getRecommendationCode() != null ? eval.getRecommendationCode().name() : null)
                    .recommendedPackageReference(eval.getRecommendedPackageReference())
                    .materialDisclosure(eval.getMaterialDisclosure())
                    .evaluatedAt(java.time.Instant.now())
                    .status(RingDesignStatus.SAVED)
                    .build();
        }

        RingDesign saved = designRepository.save(targetToSave);
        DesignResponseDto response = mapToResponse(saved, val, eval, snapshotMap);

        idempotencyService.record(idempotencyKey, "SAVE_VERSION", actorScope, request, 200, response);
        return response;
    }

    @Transactional
    public DesignResponseDto evaluateDesign(
            String designId,
            SaveDesignVersionRequest request,
            Long memberAccountId,
            String guestSessionHeader,
            Long ifMatchVersion,
            String idempotencyKey) {
        return saveVersion(designId, request, memberAccountId, guestSessionHeader, ifMatchVersion, idempotencyKey);
    }

    @Transactional
    public DesignResponseDto archive(
            String designId,
            Long memberAccountId,
            String guestSessionHeader,
            Long ifMatchVersion,
            String idempotencyKey) {
        String actorScope = resolveActorScope(memberAccountId, guestSessionHeader);
        var replay = idempotencyService.checkReplay(idempotencyKey, "ARCHIVE_DESIGN", actorScope, designId);
        if (replay.isPresent()) {
            return idempotencyService.deserialize(replay.get().getResponseBody(), DesignResponseDto.class);
        }

        RingDesign latest = designRepository.findLatestByDesignReference(designId)
                .orElseThrow(() -> RingDesignException.notFound("Không tìm thấy thiết kế nhẫn: " + designId));

        verifyOwnership(latest, memberAccountId, guestSessionHeader);

        if (latest.getStatus() == RingDesignStatus.LOCKED
                || latest.getStatus() == RingDesignStatus.ATTACHED_TO_BOOKING) {
            throw RingDesignException.conflict("DESIGN_CANNOT_ARCHIVE",
                    "Không thể lưu trữ thiết kế đã gắn với lịch đặt hoặc đã bị khóa.");
        }

        if (ifMatchVersion != null && !latest.getDesignVersion().equals(ifMatchVersion.intValue())) {
            throw RingDesignException
                    .versionConflict("Phiên bản thiết kế không khớp. Vui lòng tải lại trước khi lưu trữ.");
        }

        latest.setStatus(RingDesignStatus.ARCHIVED);
        RingDesign saved = designRepository.save(latest);

        Map<String, Object> snapshot = deserializeSnapshot(saved.getComponentSnapshot());
        DesignResponseDto response = mapToResponse(saved,
                ValidationResultDto.builder().valid(true).reasons(Collections.emptyList()).build(),
                EvaluationResultDto.builder()
                        .estimatedPrice(
                                saved.getEstimatedPrice() != null ? saved.getEstimatedPrice().toPlainString() : null)
                        .currency(saved.getCurrency())
                        .difficultyScore(
                                saved.getDifficultyScore() != null ? saved.getDifficultyScore().toPlainString() : null)
                        .rulesVersion(saved.getRulesVersion())
                        .advisory(true)
                        .build(),
                snapshot);

        idempotencyService.record(idempotencyKey, "ARCHIVE_DESIGN", actorScope, designId, 200, response);
        return response;
    }

    @Transactional
    public DesignResponseDto restore(
            String designId,
            Long memberAccountId,
            String guestSessionHeader,
            Long ifMatchVersion,
            String idempotencyKey) {
        String actorScope = resolveActorScope(memberAccountId, guestSessionHeader);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var replay = idempotencyService.checkReplay(idempotencyKey, "RESTORE_DESIGN", actorScope, designId);
            if (replay.isPresent()) {
                return idempotencyService.deserialize(replay.get().getResponseBody(), DesignResponseDto.class);
            }
        }

        RingDesign latest = designRepository.findLatestByDesignReference(designId)
                .orElseThrow(() -> RingDesignException.notFound("Không tìm thấy thiết kế nhẫn: " + designId));

        verifyOwnership(latest, memberAccountId, guestSessionHeader);

        latest.setStatus(RingDesignStatus.SAVED);
        RingDesign saved = designRepository.save(latest);

        Map<String, Object> snapshot = deserializeSnapshot(saved.getComponentSnapshot());
        DesignResponseDto response = mapToResponse(saved,
                ValidationResultDto.builder().valid(true).reasons(Collections.emptyList()).build(),
                EvaluationResultDto.builder()
                        .estimatedPrice(
                                saved.getEstimatedPrice() != null ? saved.getEstimatedPrice().toPlainString() : "0.00")
                        .currency(saved.getCurrency())
                        .difficultyScore(
                                saved.getDifficultyScore() != null ? saved.getDifficultyScore().toPlainString() : "1.0000")
                        .rulesVersion(saved.getRulesVersion())
                        .advisory(true)
                        .build(),
                snapshot);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.record(idempotencyKey, "RESTORE_DESIGN", actorScope, designId, 200, response);
        }
        return response;
    }

    @Transactional
    public BookingDesignSnapshotDto attachToBooking(
            String designId,
            AttachDesignRequestDto request,
            Long memberAccountId,
            String guestSessionHeader,
            Long ifMatchVersion,
            String idempotencyKey) {
        String actorScope = resolveActorScope(memberAccountId, guestSessionHeader);
        var replay = idempotencyService.checkReplay(idempotencyKey, "ATTACH_DESIGN", actorScope, request);
        if (replay.isPresent()) {
            return idempotencyService.deserialize(replay.get().getResponseBody(), BookingDesignSnapshotDto.class);
        }

        RingDesign latest = designRepository.findLatestByDesignReference(designId)
                .orElseThrow(() -> RingDesignException.notFound("Không tìm thấy thiết kế nhẫn: " + designId));

        verifyOwnership(latest, memberAccountId, guestSessionHeader);

        if (latest.getStatus() != RingDesignStatus.VALIDATED && latest.getStatus() != RingDesignStatus.SAVED) {
            throw RingDesignException.conflict("DESIGN_NOT_VALIDATED",
                    "Chỉ thiết kế đã được lưu (SAVED) mới có thể gắn vào lịch đặt.");
        }

        if (ifMatchVersion != null && !latest.getDesignVersion().equals(ifMatchVersion.intValue())) {
            throw RingDesignException.versionConflict("Phiên bản thiết kế không khớp.");
        }

        // Revalidate branch compatibility
        Map<String, Object> snapshot = deserializeSnapshot(latest.getComponentSnapshot());
        SaveDesignVersionRequest revalReq = buildRequestFromDesign(latest, snapshot);
        revalReq.setPackageReference(request.getPackageReference());

        ValidationResultDto val = rulesPort.validateCompatibility(revalReq, request.getBranchReference());
        if (!val.isValid()) {
            throw RingDesignException.businessInvalid("DESIGN_BRANCH_INCOMPATIBLE",
                    "Thiết kế không tương thích với chi nhánh workshop đã chọn.", val.getReasons());
        }

        latest.setStatus(RingDesignStatus.ATTACHED_TO_BOOKING);
        latest.setBookingReference(request.getBookingReference());
        designRepository.save(latest);

        BookingDesignSnapshotDto snapshotDto = BookingDesignSnapshotDto.builder()
                .designReference(latest.getDesignReference())
                .designVersion(latest.getDesignVersion())
                .packageReference(latest.getPackageReference())
                .snapshot(snapshot)
                .build();

        idempotencyService.record(idempotencyKey, "ATTACH_DESIGN", actorScope, request, 200, snapshotDto);
        return snapshotDto;
    }

    private void verifyOwnership(RingDesign design, Long memberAccountId, String guestSessionHeader) {
        if (memberAccountId == null) {
            throw RingDesignException.unauthorized("DESIGN_MEMBER_AUTH_REQUIRED",
                    "Yêu cầu đăng nhập tài khoản hội viên.");
        }
        if (design.getOwnerType() == RingDesignOwnerType.MEMBER) {
            if (!design.getOwnerReference().equals(String.valueOf(memberAccountId))) {
                throw RingDesignException.accessDenied("Bạn không có quyền truy cập vào thiết kế nhẫn này.");
            }
        } else {
            throw RingDesignException.accessDenied("Bạn không có quyền truy cập vào thiết kế nhẫn này.");
        }
    }

    private String resolveActorScope(Long memberAccountId, String guestSessionHeader) {
        if (memberAccountId != null) {
            return "MEMBER:" + memberAccountId;
        }
        return "ANONYMOUS";
    }

    private record ActorBinding(RingDesignOwnerType ownerType, String ownerReference, GuestDesignSession guestSession) {
    }

    private ActorBinding resolveActorBinding(Long memberAccountId, String guestSessionHeader) {
        if (memberAccountId != null) {
            return new ActorBinding(RingDesignOwnerType.MEMBER, String.valueOf(memberAccountId), null);
        }
        throw RingDesignException.unauthorized("DESIGN_MEMBER_AUTH_REQUIRED",
                "Yêu cầu đăng nhập tài khoản hội viên để tạo mẫu thiết kế nhẫn.");
    }

    private DesignResponseDto mapToResponse(
            RingDesign design,
            ValidationResultDto val,
            EvaluationResultDto eval,
            Map<String, Object> snapshot) {
        if (snapshot == null) {
            snapshot = new HashMap<>();
        } else {
            snapshot = new HashMap<>(snapshot);
        }
        if (!snapshot.containsKey("materialReference") && design.getMaterialReference() != null) {
            snapshot.put("materialReference", design.getMaterialReference());
        }
        if (!snapshot.containsKey("shapeReference") && design.getShapeReference() != null) {
            snapshot.put("shapeReference", design.getShapeReference());
        }
        if (!snapshot.containsKey("ringSize") && design.getRingSize() != null) {
            snapshot.put("ringSize", design.getRingSize());
        }
        return DesignResponseDto.builder()
                .designId(design.getDesignReference())
                .packageReference(design.getPackageReference())
                .designVersion(design.getDesignVersion())
                .status(design.getStatus().name())
                .validation(val)
                .evaluation(eval)
                .componentSnapshot(snapshot)
                .build();
    }

    private String serializeSnapshot(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> deserializeSnapshot(String json) {
        if (json == null || json.isBlank())
            return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private SaveDesignVersionRequest buildRequestFromDesign(RingDesign design, Map<String, Object> snapshot) {
        List<ComponentSelectionDto> components = new ArrayList<>();
        List<AttachmentSelectionDto> attachments = new ArrayList<>();
        EngravingInputDto engraving = EngravingInputDto.builder().enabled(false).build();

        if (snapshot.get("components") instanceof List<?> list) {
            try {
                components = objectMapper.convertValue(list, new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        if (snapshot.get("attachments") instanceof List<?> list) {
            try {
                attachments = objectMapper.convertValue(list, new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        if (snapshot.get("engraving") instanceof Map<?, ?> map) {
            try {
                engraving = objectMapper.convertValue(map, EngravingInputDto.class);
            } catch (Exception ignored) {
            }
        }

        BigDecimal thickness = null;
        if (snapshot.get("thicknessMm") != null) {
            try {
                thickness = new BigDecimal(snapshot.get("thicknessMm").toString());
            } catch (Exception ignored) {
            }
        }
        BigDecimal bandWidth = null;
        if (snapshot.get("bandWidthMm") != null) {
            try {
                bandWidth = new BigDecimal(snapshot.get("bandWidthMm").toString());
            } catch (Exception ignored) {
            }
        }

        List<GemstonePositionDto> gemstonePositions = new ArrayList<>();
        if (snapshot.get("gemstonePositions") instanceof List<?> list) {
            try {
                gemstonePositions = objectMapper.convertValue(list, new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }

        return SaveDesignVersionRequest.builder()
                .packageReference(design.getPackageReference())
                .materialReference(design.getMaterialReference())
                .shapeReference(design.getShapeReference())
                .ringSize(design.getRingSize())
                .thicknessMm(thickness)
                .bandWidthMm(bandWidth)
                .components(components)
                .attachments(attachments)
                .engraving(engraving)
                .gemstonePositions(gemstonePositions)
                .build();
    }

    private String determineValidationErrorCode(List<String> reasons) {
        if (reasons == null)
            return "DESIGN_OPTION_INVALID";
        for (String r : reasons) {
            if (r.contains("GEMSTONE_TOTAL_LIMIT_EXCEEDED")) {
                return "GEMSTONE_TOTAL_LIMIT_EXCEEDED";
            }
            if (r.contains("TEXT_ENGRAVING_LIMIT_EXCEEDED")) {
                return "TEXT_ENGRAVING_LIMIT_EXCEEDED";
            }
            if (r.contains("SYMBOL_ENGRAVING_LIMIT_EXCEEDED")) {
                return "SYMBOL_ENGRAVING_LIMIT_EXCEEDED";
            }
            if (r.contains("IMAGE_ENGRAVING_LIMIT_EXCEEDED")) {
                return "IMAGE_ENGRAVING_LIMIT_EXCEEDED";
            }
            if (r.contains("IMAGE_PREPROCESSING_INVALID")) {
                return "IMAGE_PREPROCESSING_INVALID";
            }
        }
        return "DESIGN_OPTION_INVALID";
    }
}
