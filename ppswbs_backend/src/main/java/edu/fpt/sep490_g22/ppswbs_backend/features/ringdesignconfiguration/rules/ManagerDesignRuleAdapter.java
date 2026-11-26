package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator.OptionKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator.PolicySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator.RecommendationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDifficultyPolicyAccessService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDifficultyPolicyAccessService.LoadedPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ManagerDesignRuleAdapter implements DesignOptionRulesPort {

    public static final String CURRENT_RULES_VERSION = "2026.11-v1.0";
    public static final List<String> STANDARD_SIZES = List.of(
            "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
            "16", "17", "18", "19", "20", "21", "22", "23", "24", "25",
            "26", "27", "28", "29");
    public static final int MAX_ENGRAVING_CHARS = 50;
    public static final BigDecimal ENGRAVING_PRICE = new BigDecimal("80000.00");
    public static final BigDecimal CUSTOM_ENGRAVING_IMAGE_PRICE = new BigDecimal("100000.00");
    public static final Set<String> TEXTURED_SURFACE_SHAPES = Set.of(
            "SHP-HAMMERED", "SHP-BALL-PEEN", "SHP-SINGLE-TWIST", "SHP-TWISTED", "SHP-WAVE");
    public static final List<String> ALLOWED_ENGRAVING_PLACEMENTS = List.of("INNER_BAND", "OUTER_BAND");
    public static final List<String> ALLOWED_COMPONENT_PLACEMENTS = List.of("CENTER", "ACCENT_LEFT", "ACCENT_RIGHT",
            "HALO", "CUSTOM");
    public static final List<String> ALLOWED_ATTACHMENT_PLACEMENTS = List.of("OUTER_SURFACE", "EDGES", "PRONGS");

    public static final BigDecimal BASE_WEIGHT_LIMIT_GRAMS = new BigDecimal("3.00");
    public static final BigDecimal EXCESS_MATERIAL_PRICE_PER_GRAM = new BigDecimal("120000.00");
    public static final BigDecimal EXCESS_PRICE_PER_GRAM_SILVER = new BigDecimal("120000.00");
    public static final BigDecimal EXCESS_PRICE_PER_GRAM_COPPER = new BigDecimal("120000.00");
    public static final BigDecimal EXCESS_PRICE_PER_GRAM_STEEL = new BigDecimal("120000.00");

    public static final Map<String, BigDecimal> MATERIAL_DENSITIES = Map.of(
            "MAT-SILVER-925", new BigDecimal("10.36"),
            "MAT-COPPER", new BigDecimal("8.96"),
            "MAT-STAINLESS-STEEL", new BigDecimal("7.99"));

    private final RingMaterialRepository materialRepository;
    private final RingShapeRepository shapeRepository;
    private final RingGemstoneRepository gemstoneRepository;
    private final RingAttachmentRepository attachmentRepository;
    private final RingCatalogueModelRepository catalogueModelRepository;
    private final RingDifficultyPolicyAccessService policyAccessService;
    private final RingDifficultyPolicyEvaluator policyEvaluator;
    private final ObjectMapper objectMapper;

    @Override
    public DesignOptionsResponseDto loadPackageOptions(String packageReference, String branchReference) {
        String normalizedPkg = (packageReference != null && !packageReference.isBlank())
                ? packageReference.trim()
                : null;

        LoadedPolicy loadedPolicy = loadPolicyOrNull();
        PolicySnapshot policy = loadedPolicy != null ? loadedPolicy.snapshot() : null;
        String effectiveRulesVersion = policy != null ? policy.rulesVersion() : CURRENT_RULES_VERSION;

        List<RingMaterial> materials = materialRepository.findAllByIsActiveTrue();
        List<RingShape> shapes = shapeRepository.findAllByIsActiveTrue();
        List<RingGemstone> gemstones = gemstoneRepository.findAllByIsActiveTrue();
        List<RingAttachment> attachments = attachmentRepository.findAllByIsActiveTrue();
        List<RingCatalogueModel> models = normalizedPkg != null
                ? catalogueModelRepository.findAllByPackageReferenceAndIsActiveTrue(normalizedPkg)
                : catalogueModelRepository.findAllByIsActiveTrue();

        return DesignOptionsResponseDto.builder()
                .packageReference(normalizedPkg)
                .rulesVersion(effectiveRulesVersion)
                .materials(materials.stream().map(m -> {
                    BigDecimal score = resolveScore(policy, OptionScoreKind.MATERIAL, m.getMaterialCode(), m.getDifficultyScore());
                    boolean selectable = score != null;
                    return DesignOptionsResponseDto.MaterialOptionDto.builder()
                            .reference(m.getMaterialCode())
                            .name(m.getMaterialName())
                            .description(m.getDescription())
                            .purity(m.getPurity())
                            .unitPrice(m.getUnitPrice().toPlainString())
                            .currency(m.getCurrency())
                            .difficultyScore(selectable ? score.setScale(2, RoundingMode.HALF_UP).toPlainString() : null)
                            .imageUrl(m.getImgUrl())
                            .selectable(selectable)
                            .unavailableReason(selectable ? null : "DIFFICULTY_SCORE_PENDING")
                            .build();
                }).toList())
                .shapes(shapes.stream().map(s -> {
                    BigDecimal score = resolveScore(policy, OptionScoreKind.SHAPE, s.getShapeCode(), s.getDifficultyScore());
                    boolean selectable = score != null;
                    return DesignOptionsResponseDto.ShapeOptionDto.builder()
                            .reference(s.getShapeCode())
                            .name(s.getShapeName())
                            .description(s.getDescription())
                            .priceAdjustment(s.getPriceAdjustment().toPlainString())
                            .currency(s.getCurrency())
                            .difficultyScore(selectable ? score.setScale(2, RoundingMode.HALF_UP).toPlainString() : null)
                            .imageUrl(s.getImgUrl())
                            .selectable(selectable)
                            .unavailableReason(selectable ? null : "DIFFICULTY_SCORE_PENDING")
                            .build();
                }).toList())
                .sizes(STANDARD_SIZES)
                .components(gemstones.stream().map(g -> {
                    BigDecimal score = resolveScore(policy, OptionScoreKind.GEMSTONE, g.getGemstoneCode(), g.getDifficultyScore());
                    boolean selectable = score != null;
                    return DesignOptionsResponseDto.ComponentOptionDto.builder()
                            .reference(g.getGemstoneCode())
                            .name(g.getGemstoneName())
                            .description(g.getDescription())
                            .type(g.getGemstoneType())
                            .color(g.getColor())
                            .cutName(g.getCutName())
                            .unitPrice(g.getUnitPrice().toPlainString())
                            .currency(g.getCurrency())
                            .difficultyScore(selectable ? score.setScale(2, RoundingMode.HALF_UP).toPlainString() : null)
                            .imageUrl(g.getImgUrl())
                            .maxQuantity(g.getMaxQuantity())
                            .allowedPlacements(ALLOWED_COMPONENT_PLACEMENTS)
                            .selectable(selectable)
                            .unavailableReason(selectable ? null : "DIFFICULTY_SCORE_PENDING")
                            .build();
                }).toList())
                .attachments(attachments.stream().map(a -> {
                    BigDecimal score = resolveScore(policy, OptionScoreKind.ATTACHMENT, a.getAttachmentCode(), a.getDifficultyScore());
                    boolean selectable = score != null;
                    return DesignOptionsResponseDto.AttachmentOptionDto.builder()
                            .reference(a.getAttachmentCode())
                            .name(a.getAttachmentName())
                            .description(a.getDescription())
                            .type(a.getAttachmentType())
                            .unitPrice(a.getUnitPrice().toPlainString())
                            .currency(a.getCurrency())
                            .difficultyScore(selectable ? score.setScale(2, RoundingMode.HALF_UP).toPlainString() : null)
                            .imageUrl(a.getImgUrl())
                            .maxQuantity(a.getMaxQuantity())
                            .allowedPlacements(ALLOWED_ATTACHMENT_PLACEMENTS)
                            .selectable(selectable)
                            .unavailableReason(selectable ? null : "DIFFICULTY_SCORE_PENDING")
                            .build();
                }).toList())
                .engravingConfig(DesignOptionsResponseDto.EngravingConfigDto.builder()
                        .supported(true)
                        .maxCharacters(MAX_ENGRAVING_CHARS)
                        .allowedPlacements(ALLOWED_ENGRAVING_PLACEMENTS)
                        .priceAdjustment(ENGRAVING_PRICE.toPlainString())
                        .customImagePrice(CUSTOM_ENGRAVING_IMAGE_PRICE.toPlainString())
                        .imageFrameSizePx(160)
                        .currency("VND")
                        .contrastMin(0)
                        .contrastMax(100)
                        .contrastDefault(50)
                        .sharpnessMin(0)
                        .sharpnessMax(100)
                        .sharpnessDefault(50)
                        .maxTextItems(2)
                        .maxSymbolItems(2)
                        .maxCustomImages(2)
                        .build())
                .totalGemstoneLimit(10)
                .models(models.stream().map(mod -> toModelOptionDto(mod, policy)).toList())
                .build();
    }

    @Override
    public ValidationResultDto validateCompatibility(SaveDesignVersionRequest request, String branchReference) {
        List<String> reasons = new ArrayList<>();

        // Validate Material
        LoadedPolicy loadedPolicy = loadPolicyOrNull();
        PolicySnapshot policy = loadedPolicy != null ? loadedPolicy.snapshot() : null;

        if (request.getMaterialReference() == null || request.getMaterialReference().isBlank()) {
            reasons.add("Chất liệu nhẫn (materialReference) không được để trống.");
        } else {
            Optional<RingMaterial> matOpt = materialRepository.findByMaterialCode(request.getMaterialReference());
            if (matOpt.isEmpty() || !Boolean.TRUE.equals(matOpt.get().getIsActive())) {
                reasons.add("Chất liệu không tồn tại hoặc đã ngừng hoạt động: " + request.getMaterialReference());
            } else if (policy != null && policy.scoreOf(OptionScoreKind.MATERIAL, request.getMaterialReference()) == null) {
                reasons.add("DIFFICULTY_SCORE_PENDING: Chất liệu chưa được chấm điểm độ khó: " + request.getMaterialReference());
            }
        }

        // Validate Shape
        if (request.getShapeReference() == null || request.getShapeReference().isBlank()) {
            reasons.add("Dáng nhẫn (shapeReference) không được để trống.");
        } else {
            Optional<RingShape> shpOpt = shapeRepository.findByShapeCode(request.getShapeReference());
            if (shpOpt.isEmpty() || !Boolean.TRUE.equals(shpOpt.get().getIsActive())) {
                reasons.add("Dáng nhẫn không tồn tại hoặc đã ngừng hoạt động: " + request.getShapeReference());
            } else if (policy != null && policy.scoreOf(OptionScoreKind.SHAPE, request.getShapeReference()) == null) {
                reasons.add("DIFFICULTY_SCORE_PENDING: Dáng nhẫn chưa được chấm điểm độ khó: " + request.getShapeReference());
            }
        }

        // Validate Size
        if (request.getRingSize() == null || request.getRingSize().isBlank()) {
            reasons.add("Size nhẫn (ringSize) không được để trống.");
        } else if (!STANDARD_SIZES.contains(request.getRingSize().trim())) {
            reasons.add("Size nhẫn không nằm trong danh mục kích thước hỗ trợ: " + request.getRingSize());
        }

        // Validate Components
        if (request.getComponents() != null) {
            Set<String> seenComponentRefs = new HashSet<>();
            int totalGemstoneQuantity = 0;
            for (ComponentSelectionDto comp : request.getComponents()) {
                if (comp.getReference() == null || comp.getReference().isBlank()) {
                    reasons.add("Mã thành phần đính kèm không được để trống.");
                    continue;
                }
                int qty = comp.getQuantity() != null ? comp.getQuantity() : 1;
                totalGemstoneQuantity += qty;
                Optional<RingGemstone> gemOpt = gemstoneRepository.findByGemstoneCode(comp.getReference());
                if (gemOpt.isEmpty() || !Boolean.TRUE.equals(gemOpt.get().getIsActive())) {
                    reasons.add("Đá quý/thành phần không tồn tại hoặc đã ngừng hoạt động: " + comp.getReference());
                } else {
                    if (policy != null && policy.scoreOf(OptionScoreKind.GEMSTONE, comp.getReference()) == null) {
                        reasons.add("DIFFICULTY_SCORE_PENDING: Đá quý chưa được chấm điểm độ khó: " + comp.getReference());
                    }
                    int maxQty = gemOpt.get().getMaxQuantity() != null ? gemOpt.get().getMaxQuantity() : 10;
                    if (qty > maxQty) {
                        reasons.add("Số lượng đá quý " + comp.getReference() + " vượt quá giới hạn tối đa (" + maxQty
                                + ").");
                    }
                }
                if (comp.getPlacement() != null && !comp.getPlacement().isBlank()
                        && !ALLOWED_COMPONENT_PLACEMENTS.contains(comp.getPlacement().trim())) {
                    reasons.add("Vị trí gắn đá quý không hợp lệ: " + comp.getPlacement());
                }
            }
            if (totalGemstoneQuantity > 10) {
                reasons.add(
                        "GEMSTONE_TOTAL_LIMIT_EXCEEDED: Tổng số lượng đá quý không được vượt quá 10 viên (hiện tại: "
                                + totalGemstoneQuantity + ").");
            }
        }

        // Validate Attachments
        if (request.getAttachments() != null) {
            for (AttachmentSelectionDto att : request.getAttachments()) {
                if (att.getReference() == null || att.getReference().isBlank()) {
                    reasons.add("Mã phụ kiện/họa tiết không được để trống.");
                    continue;
                }
                Optional<RingAttachment> attOpt = attachmentRepository.findByAttachmentCode(att.getReference());
                if (attOpt.isEmpty() || !Boolean.TRUE.equals(attOpt.get().getIsActive())) {
                    reasons.add("Phụ kiện không tồn tại hoặc đã ngừng hoạt động: " + att.getReference());
                } else {
                    if (policy != null && policy.scoreOf(OptionScoreKind.ATTACHMENT, att.getReference()) == null) {
                        reasons.add("DIFFICULTY_SCORE_PENDING: Phụ kiện chưa được chấm điểm độ khó: " + att.getReference());
                    }
                    int maxQty = attOpt.get().getMaxQuantity() != null ? attOpt.get().getMaxQuantity() : 10;
                    if (att.getQuantity() != null && att.getQuantity() > maxQty) {
                        reasons.add("Số lượng phụ kiện " + att.getReference() + " vượt quá giới hạn tối đa (" + maxQty
                                + ").");
                    }
                }
                if (att.getPlacement() != null && !att.getPlacement().isBlank()
                        && !ALLOWED_ATTACHMENT_PLACEMENTS.contains(att.getPlacement().trim())) {
                    reasons.add("Vị trí gắn phụ kiện không hợp lệ: " + att.getPlacement());
                }
            }
        }

        // Validate Engraving
        if (request.getEngraving() != null) {
            if (Boolean.TRUE.equals(request.getEngraving().getEnabled())) {
                if (request.getEngraving().getItems() != null && !request.getEngraving().getItems().isEmpty()) {
                    int textCount = 0;
                    int symbolCount = 0;
                    for (EngravingItemDto item : request.getEngraving().getItems()) {
                        String type = item.getType() != null ? item.getType().trim().toUpperCase() : "TEXT";
                        if ("SYMBOL".equals(type)) {
                            symbolCount++;
                        } else {
                            textCount++;
                        }
                        if (item.getContent() == null || item.getContent().trim().isEmpty()) {
                            reasons.add("Nội dung khắc không được để trống khi có mục khắc.");
                        } else if (item.getContent().length() > MAX_ENGRAVING_CHARS) {
                            reasons.add("Độ dài nội dung khắc vượt quá giới hạn " + MAX_ENGRAVING_CHARS + " ký tự.");
                        }
                        if (item.getPlacement() != null && !item.getPlacement().isBlank()
                                && !ALLOWED_ENGRAVING_PLACEMENTS.contains(item.getPlacement().trim())) {
                            reasons.add("Vị trí khắc không hợp lệ: " + item.getPlacement());
                        }
                    }
                    if (textCount > 2) {
                        reasons.add(
                                "TEXT_ENGRAVING_LIMIT_EXCEEDED: Số lượng nội dung chữ khắc vượt quá giới hạn tối đa 2 mục (hiện tại: "
                                        + textCount + ").");
                    }
                    if (symbolCount > 2) {
                        reasons.add(
                                "SYMBOL_ENGRAVING_LIMIT_EXCEEDED: Số lượng biểu tượng khắc vượt quá giới hạn tối đa 2 mục (hiện tại: "
                                        + symbolCount + ").");
                    }
                    if (request.getEngraving().getItems().size() > 2 && textCount <= 2 && symbolCount <= 2) {
                        reasons.add(
                                "TEXT_ENGRAVING_LIMIT_EXCEEDED: Số lượng mục khắc chữ & biểu tượng không được vượt quá 2 mục (hiện tại: "
                                        + request.getEngraving().getItems().size() + ").");
                    }
                } else {
                    String text = request.getEngraving().getText();
                    if (text == null || text.trim().isEmpty()) {
                        reasons.add("Nội dung khắc chữ không được để trống khi đã bật tùy chọn khắc.");
                    } else if (text.length() > MAX_ENGRAVING_CHARS) {
                        reasons.add("Độ dài nội dung khắc chữ vượt quá giới hạn " + MAX_ENGRAVING_CHARS + " ký tự.");
                    }
                    if (request.getEngraving().getPlacement() != null
                            && !request.getEngraving().getPlacement().isBlank()
                            && !ALLOWED_ENGRAVING_PLACEMENTS.contains(request.getEngraving().getPlacement().trim())) {
                        reasons.add("Vị trí khắc chữ không hợp lệ: " + request.getEngraving().getPlacement());
                    }
                }
            }

            // Check conflict: Cannot engrave outer band if stones are mounted on outer band
            int totalGemstones = 0;
            if (request.getComponents() != null) {
                totalGemstones = request.getComponents().stream()
                        .mapToInt(c -> c.getQuantity() != null ? c.getQuantity() : 1)
                        .sum();
            }
            if (totalGemstones > 0 && request.getEngraving() != null) {
                boolean outerEngravingRequested = false;
                if (request.getEngraving().getItems() != null) {
                    outerEngravingRequested = request.getEngraving().getItems().stream()
                            .anyMatch(it -> "OUTER_BAND".equalsIgnoreCase(it.getPlacement()));
                } else if ("OUTER_BAND".equalsIgnoreCase(request.getEngraving().getPlacement())) {
                    outerEngravingRequested = true;
                }
                if (request.getEngraving().getCustomImages() != null) {
                    if (request.getEngraving().getCustomImages().stream().anyMatch(img -> "OUTER_BAND".equalsIgnoreCase(img.getPlacement()))) {
                        outerEngravingRequested = true;
                    }
                }
                if (outerEngravingRequested) {
                    reasons.add("Do mẫu thiết kế có đính đá ở ngoài nên không thể khắc lazer");
                }
            }

            if (request.getEngraving().getCustomImages() != null) {
                if (request.getEngraving().getCustomImages().size() > 2) {
                    reasons.add(
                            "IMAGE_ENGRAVING_LIMIT_EXCEEDED: Số lượng ảnh khắc laser vượt quá giới hạn tối đa 2 ảnh (hiện tại: "
                                    + request.getEngraving().getCustomImages().size() + ").");
                }
                for (CustomEngravingImageDto img : request.getEngraving().getCustomImages()) {
                    if (img.getPlacement() != null && !img.getPlacement().isBlank()
                            && !ALLOWED_ENGRAVING_PLACEMENTS.contains(img.getPlacement().trim())) {
                        reasons.add("Vị trí khắc ảnh không hợp lệ: " + img.getPlacement());
                    }
                    if (img.getContrast() != null && (img.getContrast() < 0 || img.getContrast() > 100)) {
                        reasons.add("IMAGE_PREPROCESSING_INVALID: Độ tương phản phải nằm trong khoảng từ 0 đến 100.");
                    }
                    if (img.getSharpness() != null && (img.getSharpness() < 0 || img.getSharpness() > 100)) {
                        reasons.add("IMAGE_PREPROCESSING_INVALID: Độ sắc nét phải nằm trong khoảng từ 0 đến 100.");
                    }
                }
            }
        }

        // Branch-specific compatibility check (if branch specified)
        if (branchReference != null && !branchReference.isBlank()) {
            if ("BRANCH-RESTRICTED-VANG".equalsIgnoreCase(branchReference)
                    && request.getMaterialReference() != null
                    && request.getMaterialReference().toUpperCase().contains("GOLD")) {
                reasons.add("Chi nhánh được chọn hiện chưa hỗ trợ lò nung nhiệt độ cao gia công vật liệu vàng.");
            }
        }

        return ValidationResultDto.builder()
                .valid(reasons.isEmpty())
                .reasons(reasons)
                .build();
    }

    @Override
    public EvaluationResultDto evaluate(SaveDesignVersionRequest request) {
        LoadedPolicy loadedPolicy = loadPolicyOrNull();
        PolicySnapshot policy = loadedPolicy != null ? loadedPolicy.snapshot() : defaultFallbackSnapshot();
        Long policyVersionId = loadedPolicy != null ? loadedPolicy.version().getId() : null;

        BigDecimal totalEstimate = BigDecimal.ZERO;
        BigDecimal totalDifficulty = BigDecimal.ZERO;

        // 1. Calculate physical dimensions, volume and mass
        int sizeNum = 8;
        if (request.getRingSize() != null) {
            try {
                sizeNum = Integer.parseInt(request.getRingSize().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        double perimeterMm = sizeNum + 40.0;
        double thicknessMm = request.getThicknessMm() != null ? request.getThicknessMm().doubleValue() : 1.8;
        double bandWidthMm = request.getBandWidthMm() != null ? request.getBandWidthMm().doubleValue() : 3.5;

        double volumeMm3 = (perimeterMm + Math.PI * thicknessMm) * thicknessMm * bandWidthMm;
        double volumeCm3 = volumeMm3 / 1000.0;

        String matRef = request.getMaterialReference() != null ? request.getMaterialReference().trim()
                : "MAT-SILVER-925";
        BigDecimal density = MATERIAL_DENSITIES.getOrDefault(matRef, new BigDecimal("10.36"));
        BigDecimal estimatedWeight = BigDecimal.valueOf(volumeCm3).multiply(density).setScale(2, RoundingMode.HALF_UP);

        BigDecimal materialCost = BigDecimal.ZERO;
        BigDecimal excessMaterialCost = BigDecimal.ZERO;

        // Material cost & difficulty
        if (request.getMaterialReference() != null) {
            Optional<RingMaterial> matOpt = materialRepository.findByMaterialCode(request.getMaterialReference());
            if (matOpt.isPresent()) {
                BigDecimal baseUnitPrice = matOpt.get().getUnitPrice();
                BigDecimal matScore = resolveScore(policy, OptionScoreKind.MATERIAL, matOpt.get().getMaterialCode(),
                        matOpt.get().getDifficultyScore());
                if (matScore != null) {
                    totalDifficulty = totalDifficulty.add(matScore);
                }

                if (estimatedWeight.compareTo(BASE_WEIGHT_LIMIT_GRAMS) > 0) {
                    BigDecimal excessWeight = estimatedWeight.subtract(BASE_WEIGHT_LIMIT_GRAMS);
                    excessMaterialCost = EXCESS_MATERIAL_PRICE_PER_GRAM.multiply(excessWeight).setScale(2, RoundingMode.HALF_UP);
                }
                materialCost = baseUnitPrice.add(excessMaterialCost);
                totalEstimate = totalEstimate.add(materialCost);
            }
        }

        // Shape adjustment & difficulty
        if (request.getShapeReference() != null) {
            Optional<RingShape> shpOpt = shapeRepository.findByShapeCode(request.getShapeReference());
            if (shpOpt.isPresent()) {
                totalEstimate = totalEstimate.add(shpOpt.get().getPriceAdjustment());
                BigDecimal shpScore = resolveScore(policy, OptionScoreKind.SHAPE, shpOpt.get().getShapeCode(),
                        shpOpt.get().getDifficultyScore());
                if (shpScore != null) {
                    totalDifficulty = totalDifficulty.add(shpScore);
                }
            }
        }

        // Components cost & difficulty
        int totalGemstones = 0;
        if (request.getComponents() != null) {
            for (ComponentSelectionDto comp : request.getComponents()) {
                Optional<RingGemstone> gemOpt = gemstoneRepository.findByGemstoneCode(comp.getReference());
                if (gemOpt.isPresent()) {
                    int qty = comp.getQuantity() != null ? comp.getQuantity() : 1;
                    totalGemstones += qty;
                    totalEstimate = totalEstimate.add(gemOpt.get().getUnitPrice().multiply(BigDecimal.valueOf(qty)));
                    BigDecimal gemScore = resolveScore(policy, OptionScoreKind.GEMSTONE, gemOpt.get().getGemstoneCode(),
                            gemOpt.get().getDifficultyScore());
                    if (gemScore != null) {
                        totalDifficulty = totalDifficulty.add(gemScore.multiply(BigDecimal.valueOf(qty)));
                    }
                }
            }
        }

        // Attachments cost & difficulty
        if (request.getAttachments() != null) {
            for (AttachmentSelectionDto att : request.getAttachments()) {
                Optional<RingAttachment> attOpt = attachmentRepository.findByAttachmentCode(att.getReference());
                if (attOpt.isPresent()) {
                    int qty = att.getQuantity() != null ? att.getQuantity() : 1;
                    totalEstimate = totalEstimate.add(attOpt.get().getUnitPrice().multiply(BigDecimal.valueOf(qty)));
                    BigDecimal attScore = resolveScore(policy, OptionScoreKind.ATTACHMENT, attOpt.get().getAttachmentCode(),
                            attOpt.get().getDifficultyScore());
                    if (attScore != null) {
                        totalDifficulty = totalDifficulty.add(attScore.multiply(BigDecimal.valueOf(qty)));
                    }
                }
            }
        }

        // Engraving cost, custom images & difficulty
        BigDecimal engravingCost = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        boolean hasOuterEngraving = false;

        if (request.getEngraving() != null) {
            if (Boolean.TRUE.equals(request.getEngraving().getEnabled())) {
                engravingCost = engravingCost.add(ENGRAVING_PRICE);
                totalDifficulty = totalDifficulty.add(policy.engravingBonus());

                if ("OUTER_BAND".equalsIgnoreCase(request.getEngraving().getPlacement())) {
                    hasOuterEngraving = true;
                }
                if (request.getEngraving().getItems() != null) {
                    for (EngravingItemDto it : request.getEngraving().getItems()) {
                        if ("OUTER_BAND".equalsIgnoreCase(it.getPlacement())) {
                            hasOuterEngraving = true;
                        }
                    }
                }
            }

            if (request.getEngraving().getCustomImages() != null
                    && !request.getEngraving().getCustomImages().isEmpty()) {
                int imgCount = request.getEngraving().getCustomImages().size();
                BigDecimal customImgCost = CUSTOM_ENGRAVING_IMAGE_PRICE.multiply(BigDecimal.valueOf(imgCount));
                engravingCost = engravingCost.add(customImgCost);
                totalDifficulty = totalDifficulty.add(policy.customImageBonusPerImage().multiply(BigDecimal.valueOf(imgCount)));

                for (CustomEngravingImageDto img : request.getEngraving().getCustomImages()) {
                    if ("OUTER_BAND".equalsIgnoreCase(img.getPlacement())) {
                        hasOuterEngraving = true;
                    }
                }
            }
        }

        totalEstimate = totalEstimate.add(engravingCost);

        String shapeRef = request.getShapeReference() != null ? request.getShapeReference().trim() : "";
        if (hasOuterEngraving && TEXTURED_SURFACE_SHAPES.contains(shapeRef)) {
            warnings.add(
                    "Lưu ý: Mặt ngoài đai nhẫn có thiết kế kết cấu bề mặt gồ ghề (đập búa / búa bi / xoắn / lượn sóng). Hình khắc ở mặt ngoài có thể không đạt độ sắc nét hoặc bị biến dạng so với mong muốn.");
        }

        int customImgCount = (request.getEngraving() != null && request.getEngraving().getCustomImages() != null)
                ? request.getEngraving().getCustomImages().size()
                : 0;

        BigDecimal roundedDifficulty = totalDifficulty.setScale(4, RoundingMode.HALF_UP);
        RingDifficultyPolicyEvaluator eval = policyEvaluator != null ? policyEvaluator : new RingDifficultyPolicyEvaluator();
        RecommendationOutcome rec = eval.recommend(roundedDifficulty, matRef, totalGemstones, policy);

        return EvaluationResultDto.builder()
                .policyVersionId(policyVersionId)
                .estimatedPrice(totalEstimate.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .currency("VND")
                .difficultyScore(roundedDifficulty.toPlainString())
                .difficultyLevel(rec.difficultyLevel())
                .recommendationCode(rec.recommendationCode())
                .recommendedPackageReference(rec.recommendedPackageReference())
                .materialDisclosure(rec.materialDisclosure())
                .recommendationReason(rec.reason())
                .rulesVersion(policy.rulesVersion())
                .estimatedWeightGrams(estimatedWeight.toPlainString())
                .baseWeightLimitGrams(BASE_WEIGHT_LIMIT_GRAMS.toPlainString())
                .materialCost(materialCost.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .excessMaterialCost(excessMaterialCost.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .engravingCost(engravingCost.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .customImageCount(customImgCount)
                .warnings(warnings)
                .advisory(true)
                .build();
    }

    private LoadedPolicy loadPolicyOrNull() {
        if (policyAccessService == null) {
            return null;
        }
        return policyAccessService.loadEffectivePolicy();
    }

    private BigDecimal resolveScore(PolicySnapshot policy, OptionScoreKind kind, String ref, BigDecimal legacyFallback) {
        if (policy != null && policy.optionScores() != null && !policy.optionScores().isEmpty()) {
            return policy.scoreOf(kind, ref);
        }
        return legacyFallback;
    }

    private PolicySnapshot defaultFallbackSnapshot() {
        return new PolicySnapshot(
                CURRENT_RULES_VERSION,
                new BigDecimal("0.5000"),
                new BigDecimal("0.3000"),
                new BigDecimal("3.0000"),
                new BigDecimal("6.0000"),
                new BigDecimal("9.0000"),
                new BigDecimal("11.0000"),
                new BigDecimal("9.0000"),
                5,
                Map.of()
        );
    }

    @SuppressWarnings("unchecked")
    private DesignOptionsResponseDto.ModelOptionDto toModelOptionDto(RingCatalogueModel mod, PolicySnapshot policy) {
        List<ComponentSelectionDto> defaultComponents = new ArrayList<>();
        List<AttachmentSelectionDto> defaultAttachments = new ArrayList<>();
        if (mod.getDefaultComponents() != null && !mod.getDefaultComponents().isBlank() && objectMapper != null) {
            try {
                Map<String, Object> root = objectMapper.readValue(mod.getDefaultComponents(), new TypeReference<>() {});
                if (root.get("components") instanceof List<?> list) {
                    defaultComponents = objectMapper.convertValue(list, new TypeReference<>() {});
                }
                if (root.get("attachments") instanceof List<?> list) {
                    defaultAttachments = objectMapper.convertValue(list, new TypeReference<>() {});
                }
            } catch (Exception ignored) {
            }
        }

        PolicySnapshot effective = policy != null ? policy : defaultFallbackSnapshot();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal matScore = effective.scoreOf(OptionScoreKind.MATERIAL, mod.getMaterialReference());
        BigDecimal shpScore = effective.scoreOf(OptionScoreKind.SHAPE, mod.getShapeReference());
        boolean selectable = true;
        if (matScore != null) {
            total = total.add(matScore);
        } else if (policy != null && !policy.optionScores().isEmpty()) {
            selectable = false;
        }
        if (shpScore != null) {
            total = total.add(shpScore);
        } else if (policy != null && !policy.optionScores().isEmpty()) {
            selectable = false;
        }
        for (ComponentSelectionDto c : defaultComponents) {
            int qty = c.getQuantity() != null ? c.getQuantity() : 1;
            BigDecimal gs = effective.scoreOf(OptionScoreKind.GEMSTONE, c.getReference());
            if (gs != null) {
                total = total.add(gs.multiply(BigDecimal.valueOf(qty)));
            } else if (policy != null && !policy.optionScores().isEmpty()) {
                selectable = false;
            }
        }
        for (AttachmentSelectionDto a : defaultAttachments) {
            int qty = a.getQuantity() != null ? a.getQuantity() : 1;
            BigDecimal as = effective.scoreOf(OptionScoreKind.ATTACHMENT, a.getReference());
            if (as != null) {
                total = total.add(as.multiply(BigDecimal.valueOf(qty)));
            } else if (policy != null && !policy.optionScores().isEmpty()) {
                selectable = false;
            }
        }

        RingDifficultyPolicyEvaluator eval = policyEvaluator != null ? policyEvaluator : new RingDifficultyPolicyEvaluator();
        BigDecimal rounded = eval.roundScore(total);
        DifficultyLevel level = eval.classify(rounded, effective);

        return DesignOptionsResponseDto.ModelOptionDto.builder()
                .modelReference(mod.getModelCode())
                .name(mod.getName())
                .description(mod.getDescription())
                .imageUrl(mod.getImgUrl())
                .materialReference(mod.getMaterialReference())
                .shapeReference(mod.getShapeReference())
                .defaultComponents(defaultComponents)
                .defaultAttachments(defaultAttachments)
                .difficultyScore(selectable ? rounded.toPlainString() : null)
                .difficultyLevel(selectable ? level.name() : null)
                .selectable(selectable)
                .build();
    }
}
