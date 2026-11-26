package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.PreviewResultDto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRingDifficultyService {

    private static final BigDecimal MAX_STORAGE_SCORE = new BigDecimal("99999999999999.9999");
    private static final long PREVIEW_TTL_MINUTES = 15;

    private final RingDifficultyEffectivePolicyRepository effectivePolicyRepository;
    private final RingDifficultyCatalogueRevisionRepository catalogueRevisionRepository;
    private final RingDifficultyPolicyVersionRepository policyVersionRepository;
    private final RingDifficultyOptionScoreRepository optionScoreRepository;
    private final RingDifficultyPreviewProofRepository previewProofRepository;
    private final RingDifficultyPublicationAuditRepository publicationAuditRepository;
    private final RingDifficultyPolicyAccessService policyAccessService;
    private final RingDifficultyPolicyEvaluator evaluator;
    private final RingMaterialRepository materialRepository;
    private final RingShapeRepository shapeRepository;
    private final RingGemstoneRepository gemstoneRepository;
    private final RingAttachmentRepository attachmentRepository;
    private final RingCatalogueModelRepository catalogueModelRepository;
    private final RingDesignIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PolicyVersionDto getActivePolicy() {
        var loaded = policyAccessService.loadEffectivePolicy();
        List<RingDifficultyOptionScore> scores = optionScoreRepository
                .findAllByPolicyVersionId(loaded.version().getId());
        return toPolicyVersionDto(loaded.version(), scores, "EFFECTIVE");
    }

    @Transactional(readOnly = true)
    public List<PolicyVersionDto> listPolicyVersions() {
        RingDifficultyEffectivePolicy pointer = effectivePolicyRepository
                .findById(RingDifficultyEffectivePolicy.DEFAULT_SCOPE)
                .orElseThrow(() -> RingDesignException.rulesUnavailable(
                        "Chính sách độ khó thiết kế nhẫn hiện chưa khả dụng."));

        List<RingDifficultyPolicyVersion> versions = policyVersionRepository.findAllByOrderByPublishedAtDescIdDesc();
        if (versions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = versions.stream().map(RingDifficultyPolicyVersion::getId).toList();
        Map<Long, List<RingDifficultyOptionScore>> scoresByVersion = optionScoreRepository
                .findAllByPolicyVersionIdIn(ids).stream()
                .collect(Collectors.groupingBy(RingDifficultyOptionScore::getPolicyVersionId));

        return versions.stream()
                .map(v -> toPolicyVersionDto(
                        v,
                        scoresByVersion.getOrDefault(v.getId(), List.of()),
                        v.getId().equals(pointer.getPolicyVersionId()) ? "EFFECTIVE" : "HISTORICAL"))
                .toList();
    }

    @Transactional
    public PreviewResultDto previewPolicy(PreviewRequestDto request, Long adminAccountId) {
        var loaded = policyAccessService.loadEffectivePolicy();
        RingDifficultyPolicyVersion activeVersion = loaded.version();
        PolicySnapshot activeSnapshot = loaded.snapshot();

        if (!activeVersion.getRulesVersion().equals(request.getExpectedActiveRulesVersion().trim())) {
            throw RingDesignException.conflict("POLICY_VERSION_CONFLICT",
                    "Phiên bản chính sách hiện hành đã thay đổi (" + activeVersion.getRulesVersion()
                            + "). Vui lòng tải lại trước khi xem trước.");
        }

        RingDifficultyCatalogueRevision catRev = catalogueRevisionRepository
                .findById(RingDifficultyCatalogueRevision.DEFAULT_SCOPE)
                .orElseThrow(() -> RingDesignException.rulesUnavailable(
                        "Phiên bản danh mục thiết kế hiện chưa khả dụng."));

        CatalogueIndex catalogue = loadCatalogueIndex();
        PolicySnapshot candidateSnapshot = validateAndBuildCandidateSnapshot(request.getCandidate(), catalogue, "CANDIDATE");

        List<PreviewComparisonDto> comparisons = new ArrayList<>();
        List<UnavailableStandardCaseDto> unavailableStandardCases = new ArrayList<>();

        evaluateStandardCases(activeSnapshot, candidateSnapshot, catalogue, comparisons, unavailableStandardCases);
        if (comparisons.isEmpty()) {
            throw RingDesignException.rulesUnavailable(
                    "Không có mẫu chuẩn nào khả dụng trong danh mục hiện tại để thực hiện xem trước.");
        }

        if (request.getSamples() != null) {
            for (PreviewRequestDto.SampleSelectionDto sample : request.getSamples()) {
                validateAdminSample(sample, catalogue);
                SampleOutcomeDto before = evaluateSelection(
                        sample.getSampleName(),
                        sample.getMaterialReference(),
                        sample.getShapeReference(),
                        toQtyMap(sample.getGemstones()),
                        toQtyMap(sample.getAttachments()),
                        Boolean.TRUE.equals(sample.getEngravingEnabled()),
                        sample.getCustomImageCount() != null ? sample.getCustomImageCount() : 0,
                        activeSnapshot);
                SampleOutcomeDto after = evaluateSelection(
                        sample.getSampleName(),
                        sample.getMaterialReference(),
                        sample.getShapeReference(),
                        toQtyMap(sample.getGemstones()),
                        toQtyMap(sample.getAttachments()),
                        Boolean.TRUE.equals(sample.getEngravingEnabled()),
                        sample.getCustomImageCount() != null ? sample.getCustomImageCount() : 0,
                        candidateSnapshot);
                comparisons.add(PreviewComparisonDto.builder()
                        .source("ADMIN")
                        .before(before)
                        .after(after)
                        .build());
            }
        }

        List<ModelImpactDto> modelImpacts = evaluateAllActiveModels(activeSnapshot, candidateSnapshot, catalogue);
        int blockingCount = (int) modelImpacts.stream().filter(m -> !m.isEligible()).count();
        boolean eligible = blockingCount == 0;

        String previewRef = null;
        Instant expiresAt = null;
        if (eligible) {
            Instant now = Instant.now();
            previewProofRepository.deleteAllByExpiresAtBefore(now);
            previewRef = UUID.randomUUID().toString();
            expiresAt = now.plus(PREVIEW_TTL_MINUTES, ChronoUnit.MINUTES);

            RingDifficultyPreviewProof proof = RingDifficultyPreviewProof.builder()
                    .proofReferenceHash(sha256(previewRef))
                    .adminAccountReference(formatAdminRef(adminAccountId))
                    .candidateDigest(computeCandidateDigest(candidateSnapshot))
                    .expectedPolicyVersionId(activeVersion.getId())
                    .expectedCatalogueRevision(catRev.getRevisionNumber())
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .build();
            previewProofRepository.save(proof);
        }

        return PreviewResultDto.builder()
                .activeRulesVersion(activeVersion.getRulesVersion())
                .catalogueRevision(String.valueOf(catRev.getRevisionNumber()))
                .publicationEligible(eligible)
                .blockingModelCount(blockingCount)
                .previewReference(previewRef)
                .previewExpiresAt(expiresAt)
                .comparisons(comparisons)
                .unavailableStandardCases(unavailableStandardCases)
                .modelImpacts(modelImpacts)
                .build();
    }

    @Transactional
    public PolicyVersionDto publishPolicy(
            PublishRequestDto request,
            Long adminAccountId,
            String idempotencyKey,
            String correlationId
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RingDesignException.badRequest("DESIGN_INPUT_INVALID", "Idempotency-Key không được để trống.");
        }

        String actorScope = formatAdminRef(adminAccountId);
        var replay = idempotencyService.checkReplay(
                idempotencyKey, "PUBLISH_RING_DIFFICULTY_POLICY", actorScope, request);
        if (replay.isPresent()) {
            return idempotencyService.deserialize(replay.get().getResponseBody(), PolicyVersionDto.class);
        }

        if (request.getPreviewReference() == null || request.getPreviewReference().isBlank()) {
            throw RingDesignException.badRequest("PREVIEW_REQUIRED",
                    "Yêu cầu thực hiện xem trước (preview) hợp lệ trước khi công bố chính sách.");
        }

        // Acquire locks in documented stable order: effective_policy -> catalogue_revision
        RingDifficultyEffectivePolicy effectivePointer = effectivePolicyRepository
                .findLockedByPolicyScope(RingDifficultyEffectivePolicy.DEFAULT_SCOPE)
                .orElseThrow(() -> RingDesignException.rulesUnavailable(
                        "Chính sách độ khó thiết kế nhẫn hiện chưa khả dụng."));

        RingDifficultyCatalogueRevision catRev = catalogueRevisionRepository
                .findLockedByRevisionScope(RingDifficultyCatalogueRevision.DEFAULT_SCOPE)
                .orElseThrow(() -> RingDesignException.rulesUnavailable(
                        "Phiên bản danh mục thiết kế hiện chưa khả dụng."));

        RingDifficultyPolicyVersion activeVersion = policyVersionRepository
                .findById(effectivePointer.getPolicyVersionId())
                .orElseThrow(() -> RingDesignException.rulesUnavailable(
                        "Phiên bản chính sách độ khó hiện tại không tồn tại."));

        if (!activeVersion.getRulesVersion().equals(request.getExpectedActiveRulesVersion().trim())) {
            throw RingDesignException.conflict("POLICY_VERSION_CONFLICT",
                    "Phiên bản chính sách hiện hành đã thay đổi (" + activeVersion.getRulesVersion()
                            + "). Vui lòng tải lại và xem trước lại.");
        }

        CatalogueIndex catalogue = loadCatalogueIndex();
        PolicySnapshot candidateSnapshot = validateAndBuildCandidateSnapshot(request.getCandidate(), catalogue, "CANDIDATE");
        String candidateDigest = computeCandidateDigest(candidateSnapshot);

        String proofHash = sha256(request.getPreviewReference().trim());
        RingDifficultyPreviewProof proof = previewProofRepository.findByProofReferenceHash(proofHash)
                .orElseThrow(() -> RingDesignException.conflict("PREVIEW_MISMATCH",
                        "Bằng chứng xem trước không tồn tại hoặc không khớp."));

        Instant now = Instant.now();
        if (proof.getExpiresAt().isBefore(now)) {
            previewProofRepository.delete(proof);
            throw RingDesignException.conflict("PREVIEW_EXPIRED",
                    "Bằng chứng xem trước đã hết hạn (15 phút). Vui lòng xem trước lại.");
        }

        if (!actorScope.equals(proof.getAdminAccountReference())
                || !activeVersion.getId().equals(proof.getExpectedPolicyVersionId())
                || !catRev.getRevisionNumber().equals(proof.getExpectedCatalogueRevision())
                || !candidateDigest.equals(proof.getCandidateDigest())) {
            throw RingDesignException.conflict("PREVIEW_MISMATCH",
                    "Bằng chứng xem trước không khớp với ứng viên, tài khoản Admin hoặc phiên bản danh mục/chính sách hiện tại.");
        }

        // In-transaction exhaustive active-model revalidation
        List<RingDifficultyOptionScore> activeScores = optionScoreRepository.findAllByPolicyVersionId(activeVersion.getId());
        PolicySnapshot activeSnapshot = policyAccessService.toSnapshot(activeVersion, activeScores);
        List<ModelImpactDto> impacts = evaluateAllActiveModels(activeSnapshot, candidateSnapshot, catalogue);
        List<String> blockingModelDetails = impacts.stream()
                .filter(m -> !m.isEligible())
                .map(m -> m.getModelReference() + ": " + String.join(",", m.getBlockingReasons()))
                .toList();
        if (!blockingModelDetails.isEmpty()) {
            throw RingDesignException.businessInvalid("INVALID_POLICY",
                    "Chính sách ứng viên làm mẫu nhẫn đang hoạt động không hợp lệ hoặc vượt ngưỡng rủi ro.",
                    blockingModelDetails);
        }

        // Persist immutable new policy version
        long nextSeq = policyVersionRepository.count() + 1;
        String newRulesVersion = "2026.11-v" + nextSeq + ".0";
        while (policyVersionRepository.findByRulesVersion(newRulesVersion).isPresent()) {
            nextSeq++;
            newRulesVersion = "2026.11-v" + nextSeq + ".0";
        }

        RingDifficultyPolicyVersion savedVersion = policyVersionRepository.save(RingDifficultyPolicyVersion.builder()
                .rulesVersion(newRulesVersion)
                .engravingBonus(candidateSnapshot.engravingBonus())
                .customImageBonusPerImage(candidateSnapshot.customImageBonusPerImage())
                .easyMax(candidateSnapshot.easyMax())
                .mediumMax(candidateSnapshot.mediumMax())
                .hardMax(candidateSnapshot.hardMax())
                .veryHardMax(candidateSnapshot.veryHardMax())
                .waxAbove(candidateSnapshot.waxAbove())
                .silverClayGemstonesAbove(candidateSnapshot.silverClayGemstonesAbove())
                .publishedBy(actorScope)
                .publishedAt(now)
                .build());

        List<RingDifficultyOptionScore> newScoreEntities = candidateSnapshot.optionScores().entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<OptionKey, BigDecimal> e) -> e.getKey().kind().name())
                        .thenComparing(e -> e.getKey().reference()))
                .map(e -> RingDifficultyOptionScore.builder()
                        .policyVersionId(savedVersion.getId())
                        .optionKind(e.getKey().kind())
                        .optionReference(e.getKey().reference())
                        .score(e.getValue())
                        .build())
                .toList();
        optionScoreRepository.saveAll(newScoreEntities);

        PolicyVersionDto oldDto = toPolicyVersionDto(activeVersion, activeScores, "HISTORICAL");
        PolicyVersionDto newDto = toPolicyVersionDto(savedVersion, newScoreEntities, "EFFECTIVE");

        publicationAuditRepository.save(RingDifficultyPublicationAudit.builder()
                .actorReference(actorScope)
                .previousPolicyVersionId(activeVersion.getId())
                .newPolicyVersionId(savedVersion.getId())
                .correlationId(correlationId)
                .reason("Admin policy publication")
                .oldValuesSnapshot(toJson(oldDto))
                .newValuesSnapshot(toJson(newDto))
                .publishedAt(now)
                .build());

        effectivePointer.setPolicyVersionId(savedVersion.getId());
        effectivePointer.setUpdatedAt(now);
        effectivePolicyRepository.save(effectivePointer);

        previewProofRepository.delete(proof);
        idempotencyService.record(
                idempotencyKey, "PUBLISH_RING_DIFFICULTY_POLICY", actorScope, request, 201, newDto);

        return newDto;
    }

    private PolicySnapshot validateAndBuildCandidateSnapshot(
            PolicyValuesDto candidate,
            CatalogueIndex catalogue,
            String rulesVersionLabel
    ) {
        List<String> errors = new ArrayList<>();

        BigDecimal engravingBonus = parseDecimal(candidate.getEngravingBonus(), "engravingBonus", errors);
        BigDecimal customImageBonus = parseDecimal(candidate.getCustomImageBonusPerImage(), "customImageBonusPerImage", errors);

        DifficultyThresholdsDto t = candidate.getThresholds();
        BigDecimal easyMax = parseDecimal(t.getEasyMax(), "thresholds.easyMax", errors);
        BigDecimal mediumMax = parseDecimal(t.getMediumMax(), "thresholds.mediumMax", errors);
        BigDecimal hardMax = parseDecimal(t.getHardMax(), "thresholds.hardMax", errors);
        BigDecimal veryHardMax = parseDecimal(t.getVeryHardMax(), "thresholds.veryHardMax", errors);
        BigDecimal waxAbove = parseDecimal(t.getWaxAbove(), "thresholds.waxAbove", errors);
        int silverClayAbove = t.getSilverClayGemstonesAbove();

        if (easyMax != null && mediumMax != null && easyMax.compareTo(mediumMax) >= 0) {
            errors.add("thresholds.easyMax phải nhỏ hơn thresholds.mediumMax.");
        }
        if (mediumMax != null && hardMax != null && mediumMax.compareTo(hardMax) >= 0) {
            errors.add("thresholds.mediumMax phải nhỏ hơn thresholds.hardMax.");
        }
        if (hardMax != null && veryHardMax != null && hardMax.compareTo(veryHardMax) >= 0) {
            errors.add("thresholds.hardMax phải nhỏ hơn thresholds.veryHardMax.");
        }
        if (waxAbove != null && veryHardMax != null && waxAbove.compareTo(veryHardMax) >= 0) {
            errors.add("thresholds.waxAbove phải nhỏ hơn thresholds.veryHardMax.");
        }
        if (silverClayAbove < 0 || silverClayAbove >= 10) {
            errors.add("thresholds.silverClayGemstonesAbove phải nằm trong khoảng 0..9.");
        }

        Map<OptionKey, BigDecimal> scoreMap = new HashMap<>();
        for (OptionScoreDto os : candidate.getOptionScores()) {
            String ref = os.getReference().trim();
            OptionKey key = new OptionKey(os.getKind(), ref);
            if (!catalogue.exists(os.getKind(), ref)) {
                errors.add("Tùy chọn không tồn tại trong danh mục: " + os.getKind() + ":" + ref);
                continue;
            }
            if (scoreMap.containsKey(key)) {
                errors.add("Trùng lặp điểm tùy chọn: " + os.getKind() + ":" + ref);
                continue;
            }
            BigDecimal score = parseDecimal(os.getScore(), os.getKind() + ":" + ref, errors);
            if (score != null) {
                scoreMap.put(key, score);
            }
        }

        // Every active option must have a score
        for (OptionKey activeKey : catalogue.activeOptionKeys()) {
            if (!scoreMap.containsKey(activeKey)) {
                errors.add("Thiếu điểm độ khó cho tùy chọn đang hoạt động: " + activeKey.kind() + ":" + activeKey.reference());
            }
        }

        // Storage-safety bound check: max possible configuration sum <= MAX_STORAGE_SCORE
        if (errors.isEmpty()) {
            BigDecimal maxMat = maxScore(scoreMap, OptionScoreKind.MATERIAL);
            BigDecimal maxShp = maxScore(scoreMap, OptionScoreKind.SHAPE);
            BigDecimal maxGem = maxScore(scoreMap, OptionScoreKind.GEMSTONE).multiply(BigDecimal.TEN);
            BigDecimal maxAtt = maxScore(scoreMap, OptionScoreKind.ATTACHMENT).multiply(BigDecimal.TEN);
            BigDecimal maxPossible = maxMat.add(maxShp).add(maxGem).add(maxAtt)
                    .add(engravingBonus)
                    .add(customImageBonus.multiply(BigDecimal.valueOf(2)));
            if (maxPossible.compareTo(MAX_STORAGE_SCORE) > 0) {
                errors.add("Tổng điểm tối đa vượt quá giới hạn lưu trữ DECIMAL(18,4).");
            }
        }

        if (!errors.isEmpty()) {
            throw RingDesignException.businessInvalid("INVALID_POLICY",
                    "Chính sách độ khó ứng viên không hợp lệ.", errors);
        }

        return new PolicySnapshot(
                rulesVersionLabel,
                engravingBonus,
                customImageBonus,
                easyMax,
                mediumMax,
                hardMax,
                veryHardMax,
                waxAbove,
                silverClayAbove,
                Map.copyOf(scoreMap)
        );
    }

    private BigDecimal maxScore(Map<OptionKey, BigDecimal> scoreMap, OptionScoreKind kind) {
        return scoreMap.entrySet().stream()
                .filter(e -> e.getKey().kind() == kind)
                .map(Map.Entry::getValue)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal parseDecimal(String raw, String field, List<String> errors) {
        try {
            BigDecimal bd = new BigDecimal(raw.trim());
            if (bd.compareTo(BigDecimal.ZERO) < 0 || bd.scale() > 4) {
                errors.add(field + " phải >= 0 và tối đa 4 chữ số thập phân.");
                return null;
            }
            return bd.setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            errors.add(field + " không phải số hợp lệ.");
            return null;
        }
    }

    private void evaluateStandardCases(
            PolicySnapshot activeSnapshot,
            PolicySnapshot candidateSnapshot,
            CatalogueIndex catalogue,
            List<PreviewComparisonDto> comparisons,
            List<UnavailableStandardCaseDto> unavailable
    ) {
        record StandardCase(
                String name,
                String material,
                String shape,
                Map<String, Integer> gems,
                Map<String, Integer> atts,
                boolean engraving,
                int customImages
        ) {}

        List<StandardCase> standardCases = List.of(
                new StandardCase("STD-BASIC-SILVER", "MAT-SILVER-925", "SHP-CLASSIC-ROUND", Map.of(), Map.of(), false, 0),
                new StandardCase("STD-SILVER-CLAY-GEMS", "MAT-SILVER-925", "SHP-CLASSIC-ROUND", Map.of("GEM-CZ-WHITE-2MM", 6), Map.of(), false, 0),
                new StandardCase("STD-ENGRAVING-IMAGE", "MAT-SILVER-925", "SHP-COMFORT-FIT", Map.of(), Map.of(), true, 1),
                new StandardCase("STD-WAX-BOUNDARY", "MAT-SILVER-925", "SHP-TWISTED", Map.of("GEM-MOISSANITE-3MM", 4), Map.of("ATT-BEAD-EDGE", 1), true, 0)
        );

        for (StandardCase sc : standardCases) {
            String unavailableReason = checkSelectionAvailable(sc.material(), sc.shape(), sc.gems(), sc.atts(), catalogue, activeSnapshot);
            if (unavailableReason != null) {
                unavailable.add(UnavailableStandardCaseDto.builder()
                        .sampleName(sc.name())
                        .reason(unavailableReason)
                        .build());
                continue;
            }
            SampleOutcomeDto before = evaluateSelection(
                    sc.name(), sc.material(), sc.shape(), sc.gems(), sc.atts(), sc.engraving(), sc.customImages(), activeSnapshot);
            SampleOutcomeDto after = evaluateSelection(
                    sc.name(), sc.material(), sc.shape(), sc.gems(), sc.atts(), sc.engraving(), sc.customImages(), candidateSnapshot);
            comparisons.add(PreviewComparisonDto.builder()
                    .source("STANDARD")
                    .before(before)
                    .after(after)
                    .build());
        }
    }

    private String checkSelectionAvailable(
            String mat,
            String shp,
            Map<String, Integer> gems,
            Map<String, Integer> atts,
            CatalogueIndex catalogue,
            PolicySnapshot activeSnapshot
    ) {
        if (!catalogue.isActive(OptionScoreKind.MATERIAL, mat) || activeSnapshot.scoreOf(OptionScoreKind.MATERIAL, mat) == null) {
            return "Chất liệu " + mat + " không khả dụng hoặc chưa có điểm.";
        }
        if (!catalogue.isActive(OptionScoreKind.SHAPE, shp) || activeSnapshot.scoreOf(OptionScoreKind.SHAPE, shp) == null) {
            return "Dáng nhẫn " + shp + " không khả dụng hoặc chưa có điểm.";
        }
        for (var e : gems.entrySet()) {
            if (!catalogue.isActive(OptionScoreKind.GEMSTONE, e.getKey()) || activeSnapshot.scoreOf(OptionScoreKind.GEMSTONE, e.getKey()) == null) {
                return "Đá quý " + e.getKey() + " không khả dụng hoặc chưa có điểm.";
            }
        }
        for (var e : atts.entrySet()) {
            if (!catalogue.isActive(OptionScoreKind.ATTACHMENT, e.getKey()) || activeSnapshot.scoreOf(OptionScoreKind.ATTACHMENT, e.getKey()) == null) {
                return "Phụ kiện " + e.getKey() + " không khả dụng hoặc chưa có điểm.";
            }
        }
        return null;
    }

    private void validateAdminSample(PreviewRequestDto.SampleSelectionDto sample, CatalogueIndex catalogue) {
        List<String> errors = new ArrayList<>();
        if (!catalogue.isActive(OptionScoreKind.MATERIAL, sample.getMaterialReference())) {
            errors.add("Mẫu " + sample.getSampleName() + ": materialReference không hoạt động.");
        }
        if (!catalogue.isActive(OptionScoreKind.SHAPE, sample.getShapeReference())) {
            errors.add("Mẫu " + sample.getSampleName() + ": shapeReference không hoạt động.");
        }
        int totalGems = 0;
        if (sample.getGemstones() != null) {
            for (var g : sample.getGemstones()) {
                if (!catalogue.isActive(OptionScoreKind.GEMSTONE, g.getReference())) {
                    errors.add("Mẫu " + sample.getSampleName() + ": gemstone " + g.getReference() + " không hoạt động.");
                }
                totalGems += g.getQuantity();
            }
        }
        if (totalGems > 10) {
            errors.add("Mẫu " + sample.getSampleName() + ": tổng số đá quý vượt quá 10.");
        }
        if (sample.getAttachments() != null) {
            for (var a : sample.getAttachments()) {
                if (!catalogue.isActive(OptionScoreKind.ATTACHMENT, a.getReference())) {
                    errors.add("Mẫu " + sample.getSampleName() + ": attachment " + a.getReference() + " không hoạt động.");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw RingDesignException.businessInvalid("INVALID_POLICY", "Mẫu thử nghiệm không hợp lệ.", errors);
        }
    }

    private List<ModelImpactDto> evaluateAllActiveModels(
            PolicySnapshot activeSnapshot,
            PolicySnapshot candidateSnapshot,
            CatalogueIndex catalogue
    ) {
        List<RingCatalogueModel> activeModels = catalogueModelRepository.findAllByIsActiveTrue();
        List<ModelImpactDto> impacts = new ArrayList<>();
        for (RingCatalogueModel model : activeModels) {
            ModelDefaults defaults = parseModelDefaults(model.getDefaultComponents());
            SampleOutcomeDto before = evaluateModelSafely(model, defaults, activeSnapshot, catalogue).outcome();
            ModelEvalResult afterResult = evaluateModelSafely(model, defaults, candidateSnapshot, catalogue);

            List<String> blocking = new ArrayList<>(afterResult.reasons());
            boolean eligible = blocking.isEmpty();
            if (afterResult.outcome() != null && afterResult.outcome().getDifficultyLevel() == DifficultyLevel.TOO_RISKY) {
                blocking.add("MODEL_TOO_RISKY");
                eligible = false;
            }

            if (before == null) {
                before = SampleOutcomeDto.builder()
                        .sampleName(model.getName())
                        .difficultyScore("0.0000")
                        .difficultyLevel(DifficultyLevel.TOO_RISKY)
                        .recommendationCode(RecommendationCode.TOO_RISKY)
                        .build();
            }

            impacts.add(ModelImpactDto.builder()
                    .modelReference(model.getModelCode())
                    .name(model.getName())
                    .materialReference(model.getMaterialReference())
                    .before(before)
                    .after(afterResult.outcome())
                    .eligible(eligible)
                    .blockingReasons(blocking.stream().distinct().toList())
                    .build());
        }
        return impacts;
    }

    private record ModelEvalResult(SampleOutcomeDto outcome, List<String> reasons) {}

    private ModelEvalResult evaluateModelSafely(
            RingCatalogueModel model,
            ModelDefaults defaults,
            PolicySnapshot policy,
            CatalogueIndex catalogue
    ) {
        List<String> reasons = new ArrayList<>();
        if (defaults.parseError()) {
            reasons.add("MODEL_DEFAULT_INVALID");
        }
        if (!catalogue.isActive(OptionScoreKind.MATERIAL, model.getMaterialReference())) {
            reasons.add("MODEL_OPTION_INACTIVE");
        } else if (policy.scoreOf(OptionScoreKind.MATERIAL, model.getMaterialReference()) == null) {
            reasons.add("MODEL_OPTION_SCORE_MISSING");
        }
        if (!catalogue.isActive(OptionScoreKind.SHAPE, model.getShapeReference())) {
            reasons.add("MODEL_OPTION_INACTIVE");
        } else if (policy.scoreOf(OptionScoreKind.SHAPE, model.getShapeReference()) == null) {
            reasons.add("MODEL_OPTION_SCORE_MISSING");
        }
        for (var e : defaults.gemstones().entrySet()) {
            if (!catalogue.isActive(OptionScoreKind.GEMSTONE, e.getKey())) {
                reasons.add("MODEL_OPTION_INACTIVE");
            } else if (policy.scoreOf(OptionScoreKind.GEMSTONE, e.getKey()) == null) {
                reasons.add("MODEL_OPTION_SCORE_MISSING");
            }
            if (e.getValue() <= 0) {
                reasons.add("MODEL_DEFAULT_INVALID");
            }
        }
        for (var e : defaults.attachments().entrySet()) {
            if (!catalogue.isActive(OptionScoreKind.ATTACHMENT, e.getKey())) {
                reasons.add("MODEL_OPTION_INACTIVE");
            } else if (policy.scoreOf(OptionScoreKind.ATTACHMENT, e.getKey()) == null) {
                reasons.add("MODEL_OPTION_SCORE_MISSING");
            }
            if (e.getValue() <= 0) {
                reasons.add("MODEL_DEFAULT_INVALID");
            }
        }
        if (!reasons.isEmpty()) {
            return new ModelEvalResult(null, reasons);
        }
        SampleOutcomeDto outcome = evaluateSelection(
                model.getName(),
                model.getMaterialReference(),
                model.getShapeReference(),
                defaults.gemstones(),
                defaults.attachments(),
                false,
                0,
                policy
        );
        return new ModelEvalResult(outcome, List.of());
    }

    private SampleOutcomeDto evaluateSelection(
            String name,
            String materialRef,
            String shapeRef,
            Map<String, Integer> gems,
            Map<String, Integer> atts,
            boolean engravingEnabled,
            int customImageCount,
            PolicySnapshot policy
    ) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal matScore = policy.scoreOf(OptionScoreKind.MATERIAL, materialRef);
        BigDecimal shpScore = policy.scoreOf(OptionScoreKind.SHAPE, shapeRef);
        if (matScore != null) total = total.add(matScore);
        if (shpScore != null) total = total.add(shpScore);

        int totalGemCount = 0;
        for (var e : gems.entrySet()) {
            BigDecimal gs = policy.scoreOf(OptionScoreKind.GEMSTONE, e.getKey());
            if (gs != null) {
                total = total.add(gs.multiply(BigDecimal.valueOf(e.getValue())));
            }
            totalGemCount += e.getValue();
        }
        for (var e : atts.entrySet()) {
            BigDecimal as = policy.scoreOf(OptionScoreKind.ATTACHMENT, e.getKey());
            if (as != null) {
                total = total.add(as.multiply(BigDecimal.valueOf(e.getValue())));
            }
        }
        if (engravingEnabled) {
            total = total.add(policy.engravingBonus());
        }
        if (customImageCount > 0) {
            total = total.add(policy.customImageBonusPerImage().multiply(BigDecimal.valueOf(customImageCount)));
        }

        BigDecimal rounded = evaluator.roundScore(total);
        RecommendationOutcome rec = evaluator.recommend(rounded, materialRef, totalGemCount, policy);

        return SampleOutcomeDto.builder()
                .sampleName(name)
                .difficultyScore(rounded.toPlainString())
                .difficultyLevel(rec.difficultyLevel())
                .recommendationCode(rec.recommendationCode())
                .build();
    }

    private record ModelDefaults(Map<String, Integer> gemstones, Map<String, Integer> attachments, boolean parseError) {}

    private ModelDefaults parseModelDefaults(String json) {
        if (json == null || json.isBlank()) {
            return new ModelDefaults(Map.of(), Map.of(), false);
        }
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Integer> gems = new LinkedHashMap<>();
            Map<String, Integer> atts = new LinkedHashMap<>();
            extractQtyMap(root.get("components"), gems);
            extractQtyMap(root.get("attachments"), atts);
            return new ModelDefaults(gems, atts, false);
        } catch (Exception e) {
            return new ModelDefaults(Map.of(), Map.of(), true);
        }
    }

    @SuppressWarnings("unchecked")
    private void extractQtyMap(Object listObj, Map<String, Integer> target) {
        if (listObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object refObj = map.get("reference");
                    Object qtyObj = map.get("quantity");
                    if (refObj instanceof String ref && !ref.isBlank()) {
                        int qty = qtyObj instanceof Number n ? n.intValue() : 1;
                        target.merge(ref.trim(), qty, Integer::sum);
                    }
                }
            }
        }
    }

    private Map<String, Integer> toQtyMap(List<PreviewRequestDto.SelectionQuantityDto> items) {
        if (items == null || items.isEmpty()) return Map.of();
        Map<String, Integer> map = new LinkedHashMap<>();
        for (var item : items) {
            map.merge(item.getReference().trim(), item.getQuantity(), Integer::sum);
        }
        return map;
    }

    private PolicyVersionDto toPolicyVersionDto(
            RingDifficultyPolicyVersion v,
            List<RingDifficultyOptionScore> scores,
            String status
    ) {
        List<OptionScoreDto> scoreDtos = scores.stream()
                .sorted(Comparator
                        .comparing((RingDifficultyOptionScore s) -> s.getOptionKind().name())
                        .thenComparing(RingDifficultyOptionScore::getOptionReference))
                .map(s -> OptionScoreDto.builder()
                        .kind(s.getOptionKind())
                        .reference(s.getOptionReference())
                        .score(s.getScore().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .build())
                .toList();

        return PolicyVersionDto.builder()
                .rulesVersion(v.getRulesVersion())
                .status(status)
                .publishedAt(v.getPublishedAt())
                .publishedBy(v.getPublishedBy())
                .optionScores(scoreDtos)
                .engravingBonus(v.getEngravingBonus().setScale(4, RoundingMode.HALF_UP).toPlainString())
                .customImageBonusPerImage(v.getCustomImageBonusPerImage().setScale(4, RoundingMode.HALF_UP).toPlainString())
                .thresholds(DifficultyThresholdsDto.builder()
                        .easyMax(v.getEasyMax().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .mediumMax(v.getMediumMax().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .hardMax(v.getHardMax().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .veryHardMax(v.getVeryHardMax().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .waxAbove(v.getWaxAbove().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .silverClayGemstonesAbove(v.getSilverClayGemstonesAbove())
                        .build())
                .build();
    }

    private String computeCandidateDigest(PolicySnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("engraving=").append(snapshot.engravingBonus().setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|');
        sb.append("image=").append(snapshot.customImageBonusPerImage().setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|');
        sb.append("easy=").append(snapshot.easyMax().setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|');
        sb.append("medium=").append(snapshot.mediumMax().setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|');
        sb.append("hard=").append(snapshot.hardMax().setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|');
        sb.append("veryHard=").append(snapshot.veryHardMax().setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|');
        sb.append("wax=").append(snapshot.waxAbove().setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|');
        sb.append("silverClayGems=").append(snapshot.silverClayGemstonesAbove()).append('|');
        snapshot.optionScores().entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<OptionKey, BigDecimal> e) -> e.getKey().kind().name())
                        .thenComparing(e -> e.getKey().reference()))
                .forEach(e -> sb.append(e.getKey().kind().name())
                        .append(':')
                        .append(e.getKey().reference())
                        .append('=')
                        .append(e.getValue().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .append(';'));
        return sha256(sb.toString());
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String formatAdminRef(Long adminAccountId) {
        return "ADMIN:" + adminAccountId;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize policy snapshot", e);
        }
    }

    private CatalogueIndex loadCatalogueIndex() {
        Map<OptionKey, Boolean> all = new HashMap<>();
        for (RingMaterial m : materialRepository.findAll()) {
            all.put(new OptionKey(OptionScoreKind.MATERIAL, m.getMaterialCode()), Boolean.TRUE.equals(m.getIsActive()));
        }
        for (RingShape s : shapeRepository.findAll()) {
            all.put(new OptionKey(OptionScoreKind.SHAPE, s.getShapeCode()), Boolean.TRUE.equals(s.getIsActive()));
        }
        for (RingGemstone g : gemstoneRepository.findAll()) {
            all.put(new OptionKey(OptionScoreKind.GEMSTONE, g.getGemstoneCode()), Boolean.TRUE.equals(g.getIsActive()));
        }
        for (RingAttachment a : attachmentRepository.findAll()) {
            all.put(new OptionKey(OptionScoreKind.ATTACHMENT, a.getAttachmentCode()), Boolean.TRUE.equals(a.getIsActive()));
        }
        return new CatalogueIndex(all);
    }

    private record CatalogueIndex(Map<OptionKey, Boolean> options) {
        boolean exists(OptionScoreKind kind, String ref) {
            return options.containsKey(new OptionKey(kind, ref));
        }

        boolean isActive(OptionScoreKind kind, String ref) {
            return Boolean.TRUE.equals(options.get(new OptionKey(kind, ref)));
        }

        List<OptionKey> activeOptionKeys() {
            return options.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }
}
