package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
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
            "26", "27", "28", "29"
    );
    public static final int MAX_ENGRAVING_CHARS = 50;
    public static final BigDecimal ENGRAVING_PRICE = new BigDecimal("80000.00");
    public static final BigDecimal CUSTOM_ENGRAVING_IMAGE_PRICE = new BigDecimal("100000.00");
    public static final Set<String> TEXTURED_SURFACE_SHAPES = Set.of(
            "SHP-HAMMERED", "SHP-BALL-PEEN", "SHP-SINGLE-TWIST", "SHP-TWISTED", "SHP-WAVE"
    );
    public static final List<String> ALLOWED_ENGRAVING_PLACEMENTS = List.of("INNER_BAND", "OUTER_BAND");
    public static final List<String> ALLOWED_COMPONENT_PLACEMENTS = List.of("CENTER", "ACCENT_LEFT", "ACCENT_RIGHT", "HALO", "CUSTOM");
    public static final List<String> ALLOWED_ATTACHMENT_PLACEMENTS = List.of("OUTER_SURFACE", "EDGES", "PRONGS");

    public static final BigDecimal BASE_WEIGHT_LIMIT_GRAMS = new BigDecimal("3.00");
    public static final BigDecimal EXCESS_PRICE_PER_GRAM_SILVER = new BigDecimal("120000.00");
    public static final BigDecimal EXCESS_PRICE_PER_GRAM_COPPER = new BigDecimal("40000.00");
    public static final BigDecimal EXCESS_PRICE_PER_GRAM_STEEL = new BigDecimal("50000.00");

    public static final Map<String, BigDecimal> MATERIAL_DENSITIES = Map.of(
            "MAT-SILVER-925", new BigDecimal("10.36"),
            "MAT-COPPER", new BigDecimal("8.96"),
            "MAT-STAINLESS-STEEL", new BigDecimal("7.99")
    );

    private final RingMaterialRepository materialRepository;
    private final RingShapeRepository shapeRepository;
    private final RingGemstoneRepository gemstoneRepository;
    private final RingAttachmentRepository attachmentRepository;
    private final RingCatalogueModelRepository catalogueModelRepository;

    @Override
    public DesignOptionsResponseDto loadPackageOptions(String packageReference, String branchReference) {
        if (packageReference == null || packageReference.isBlank()) {
            throw RingDesignException.badRequest("DESIGN_INPUT_INVALID", "packageReference không được để trống.");
        }

        List<RingMaterial> materials = materialRepository.findAllByIsActiveTrue();
        List<RingShape> shapes = shapeRepository.findAllByIsActiveTrue();
        List<RingGemstone> gemstones = gemstoneRepository.findAllByIsActiveTrue();
        List<RingAttachment> attachments = attachmentRepository.findAllByIsActiveTrue();
        List<RingCatalogueModel> models = catalogueModelRepository.findAllByPackageReferenceAndIsActiveTrue(packageReference);

        return DesignOptionsResponseDto.builder()
                .packageReference(packageReference)
                .rulesVersion(CURRENT_RULES_VERSION)
                .materials(materials.stream().map(m -> DesignOptionsResponseDto.MaterialOptionDto.builder()
                        .reference(m.getMaterialCode())
                        .name(m.getMaterialName())
                        .description(m.getDescription())
                        .purity(m.getPurity())
                        .unitPrice(m.getUnitPrice().toPlainString())
                        .currency(m.getCurrency())
                        .difficultyScore(m.getDifficultyScore().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .imageUrl(m.getImgUrl())
                        .build()).toList())
                .shapes(shapes.stream().map(s -> DesignOptionsResponseDto.ShapeOptionDto.builder()
                        .reference(s.getShapeCode())
                        .name(s.getShapeName())
                        .description(s.getDescription())
                        .priceAdjustment(s.getPriceAdjustment().toPlainString())
                        .currency(s.getCurrency())
                        .difficultyScore(s.getDifficultyScore().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .imageUrl(s.getImgUrl())
                        .build()).toList())
                .sizes(STANDARD_SIZES)
                .components(gemstones.stream().map(g -> DesignOptionsResponseDto.ComponentOptionDto.builder()
                        .reference(g.getGemstoneCode())
                        .name(g.getGemstoneName())
                        .description(g.getDescription())
                        .type(g.getGemstoneType())
                        .color(g.getColor())
                        .cutName(g.getCutName())
                        .unitPrice(g.getUnitPrice().toPlainString())
                        .currency(g.getCurrency())
                        .difficultyScore(g.getDifficultyScore().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .imageUrl(g.getImgUrl())
                        .maxQuantity(g.getMaxQuantity())
                        .allowedPlacements(ALLOWED_COMPONENT_PLACEMENTS)
                        .build()).toList())
                .attachments(attachments.stream().map(a -> DesignOptionsResponseDto.AttachmentOptionDto.builder()
                        .reference(a.getAttachmentCode())
                        .name(a.getAttachmentName())
                        .description(a.getDescription())
                        .type(a.getAttachmentType())
                        .unitPrice(a.getUnitPrice().toPlainString())
                        .currency(a.getCurrency())
                        .difficultyScore(a.getDifficultyScore().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .imageUrl(a.getImgUrl())
                        .maxQuantity(a.getMaxQuantity())
                        .allowedPlacements(ALLOWED_ATTACHMENT_PLACEMENTS)
                        .build()).toList())
                .engravingConfig(DesignOptionsResponseDto.EngravingConfigDto.builder()
                        .supported(true)
                        .maxCharacters(MAX_ENGRAVING_CHARS)
                        .allowedPlacements(ALLOWED_ENGRAVING_PLACEMENTS)
                        .priceAdjustment(ENGRAVING_PRICE.toPlainString())
                        .customImagePrice(CUSTOM_ENGRAVING_IMAGE_PRICE.toPlainString())
                        .imageFrameSizePx(160)
                        .currency("VND")
                        .build())
                .models(models.stream().map(mod -> DesignOptionsResponseDto.ModelOptionDto.builder()
                        .modelReference(mod.getModelCode())
                        .name(mod.getName())
                        .description(mod.getDescription())
                        .imageUrl(mod.getImgUrl())
                        .materialReference(mod.getMaterialReference())
                        .shapeReference(mod.getShapeReference())
                        .build()).toList())
                .build();
    }

    @Override
    public ValidationResultDto validateCompatibility(SaveDesignVersionRequest request, String branchReference) {
        List<String> reasons = new ArrayList<>();

        // Validate Material
        if (request.getMaterialReference() == null || request.getMaterialReference().isBlank()) {
            reasons.add("Chất liệu nhẫn (materialReference) không được để trống.");
        } else {
            Optional<RingMaterial> matOpt = materialRepository.findByMaterialCode(request.getMaterialReference());
            if (matOpt.isEmpty() || !Boolean.TRUE.equals(matOpt.get().getIsActive())) {
                reasons.add("Chất liệu không tồn tại hoặc đã ngừng hoạt động: " + request.getMaterialReference());
            }
        }

        // Validate Shape
        if (request.getShapeReference() == null || request.getShapeReference().isBlank()) {
            reasons.add("Dáng nhẫn (shapeReference) không được để trống.");
        } else {
            Optional<RingShape> shpOpt = shapeRepository.findByShapeCode(request.getShapeReference());
            if (shpOpt.isEmpty() || !Boolean.TRUE.equals(shpOpt.get().getIsActive())) {
                reasons.add("Dáng nhẫn không tồn tại hoặc đã ngừng hoạt động: " + request.getShapeReference());
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
            for (ComponentSelectionDto comp : request.getComponents()) {
                if (comp.getReference() == null || comp.getReference().isBlank()) {
                    reasons.add("Mã thành phần đính kèm không được để trống.");
                    continue;
                }
                Optional<RingGemstone> gemOpt = gemstoneRepository.findByGemstoneCode(comp.getReference());
                if (gemOpt.isEmpty() || !Boolean.TRUE.equals(gemOpt.get().getIsActive())) {
                    reasons.add("Đá quý/thành phần không tồn tại hoặc đã ngừng hoạt động: " + comp.getReference());
                } else {
                    int maxQty = gemOpt.get().getMaxQuantity() != null ? gemOpt.get().getMaxQuantity() : 10;
                    if (comp.getQuantity() != null && comp.getQuantity() > maxQty) {
                        reasons.add("Số lượng đá quý " + comp.getReference() + " vượt quá giới hạn tối đa (" + maxQty + ").");
                    }
                }
                if (comp.getPlacement() != null && !comp.getPlacement().isBlank()
                        && !ALLOWED_COMPONENT_PLACEMENTS.contains(comp.getPlacement().trim())) {
                    reasons.add("Vị trí gắn đá quý không hợp lệ: " + comp.getPlacement());
                }
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
                    int maxQty = attOpt.get().getMaxQuantity() != null ? attOpt.get().getMaxQuantity() : 10;
                    if (att.getQuantity() != null && att.getQuantity() > maxQty) {
                        reasons.add("Số lượng phụ kiện " + att.getReference() + " vượt quá giới hạn tối đa (" + maxQty + ").");
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
                    for (EngravingItemDto item : request.getEngraving().getItems()) {
                        if (item.getContent() == null || item.getContent().trim().isEmpty()) {
                            reasons.add("Nội dung khắc không được để trống khi có mục khắc.");
                        } else if (item.getContent().trim().length() > MAX_ENGRAVING_CHARS) {
                            reasons.add("Độ dài nội dung khắc vượt quá giới hạn " + MAX_ENGRAVING_CHARS + " ký tự.");
                        }
                        if (item.getPlacement() != null && !item.getPlacement().isBlank()
                                && !ALLOWED_ENGRAVING_PLACEMENTS.contains(item.getPlacement().trim())) {
                            reasons.add("Vị trí khắc không hợp lệ: " + item.getPlacement());
                        }
                    }
                } else {
                    String text = request.getEngraving().getText();
                    if (text == null || text.trim().isEmpty()) {
                        reasons.add("Nội dung khắc chữ không được để trống khi đã bật tùy chọn khắc.");
                    } else if (text.trim().length() > MAX_ENGRAVING_CHARS) {
                        reasons.add("Độ dài nội dung khắc chữ vượt quá giới hạn " + MAX_ENGRAVING_CHARS + " ký tự.");
                    }
                    if (request.getEngraving().getPlacement() != null && !request.getEngraving().getPlacement().isBlank()
                            && !ALLOWED_ENGRAVING_PLACEMENTS.contains(request.getEngraving().getPlacement().trim())) {
                        reasons.add("Vị trí khắc chữ không hợp lệ: " + request.getEngraving().getPlacement());
                    }
                }
            }

            if (request.getEngraving().getCustomImages() != null) {
                for (CustomEngravingImageDto img : request.getEngraving().getCustomImages()) {
                    if (img.getPlacement() != null && !img.getPlacement().isBlank()
                            && !ALLOWED_ENGRAVING_PLACEMENTS.contains(img.getPlacement().trim())) {
                        reasons.add("Vị trí khắc ảnh không hợp lệ: " + img.getPlacement());
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
        BigDecimal totalEstimate = BigDecimal.ZERO;
        BigDecimal totalDifficulty = BigDecimal.ZERO;

        // 1. Calculate physical dimensions, volume and mass
        int sizeNum = 8;
        if (request.getRingSize() != null) {
            try {
                sizeNum = Integer.parseInt(request.getRingSize().trim());
            } catch (NumberFormatException ignored) {}
        }
        double perimeterMm = sizeNum + 40.0;
        double thicknessMm = request.getThicknessMm() != null ? request.getThicknessMm().doubleValue() : 1.8;
        double bandWidthMm = request.getBandWidthMm() != null ? request.getBandWidthMm().doubleValue() : 3.5;

        // Geometric volume of hollow cylindrical ring band: V = (P + π*T) * T * W in mm³
        double volumeMm3 = (perimeterMm + Math.PI * thicknessMm) * thicknessMm * bandWidthMm;
        double volumeCm3 = volumeMm3 / 1000.0;

        String matRef = request.getMaterialReference() != null ? request.getMaterialReference().trim() : "MAT-SILVER-925";
        BigDecimal density = MATERIAL_DENSITIES.getOrDefault(matRef, new BigDecimal("10.36"));
        BigDecimal estimatedWeight = BigDecimal.valueOf(volumeCm3).multiply(density).setScale(2, RoundingMode.HALF_UP);

        BigDecimal materialCost = BigDecimal.ZERO;
        BigDecimal excessMaterialCost = BigDecimal.ZERO;

        // Material cost & difficulty
        if (request.getMaterialReference() != null) {
            Optional<RingMaterial> matOpt = materialRepository.findByMaterialCode(request.getMaterialReference());
            if (matOpt.isPresent()) {
                BigDecimal baseUnitPrice = matOpt.get().getUnitPrice();
                totalDifficulty = totalDifficulty.add(matOpt.get().getDifficultyScore());

                // If mass exceeds 3.0 grams, add excess surcharge
                if (estimatedWeight.compareTo(BASE_WEIGHT_LIMIT_GRAMS) > 0) {
                    BigDecimal excessWeight = estimatedWeight.subtract(BASE_WEIGHT_LIMIT_GRAMS);
                    BigDecimal excessRate = EXCESS_PRICE_PER_GRAM_SILVER;
                    if ("MAT-COPPER".equalsIgnoreCase(matRef)) {
                        excessRate = EXCESS_PRICE_PER_GRAM_COPPER;
                    } else if ("MAT-STAINLESS-STEEL".equalsIgnoreCase(matRef)) {
                        excessRate = EXCESS_PRICE_PER_GRAM_STEEL;
                    }
                    excessMaterialCost = excessWeight.multiply(excessRate).setScale(2, RoundingMode.HALF_UP);
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
                totalDifficulty = totalDifficulty.add(shpOpt.get().getDifficultyScore());
            }
        }

        // Components cost & difficulty
        if (request.getComponents() != null) {
            for (ComponentSelectionDto comp : request.getComponents()) {
                Optional<RingGemstone> gemOpt = gemstoneRepository.findByGemstoneCode(comp.getReference());
                if (gemOpt.isPresent()) {
                    int qty = comp.getQuantity() != null ? comp.getQuantity() : 1;
                    totalEstimate = totalEstimate.add(gemOpt.get().getUnitPrice().multiply(BigDecimal.valueOf(qty)));
                    totalDifficulty = totalDifficulty.add(gemOpt.get().getDifficultyScore().multiply(BigDecimal.valueOf(qty)));
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
                    totalDifficulty = totalDifficulty.add(attOpt.get().getDifficultyScore().multiply(BigDecimal.valueOf(qty)));
                }
            }
        }

        // Engraving cost, custom images & difficulty
        BigDecimal engravingCost = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        boolean hasOuterEngraving = false;

        if (request.getEngraving() != null) {
            // 1. Standard engraving paid once
            if (Boolean.TRUE.equals(request.getEngraving().getEnabled())) {
                engravingCost = engravingCost.add(ENGRAVING_PRICE);
                totalDifficulty = totalDifficulty.add(new BigDecimal("0.5000"));

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

            // 2. Custom uploaded images at 100,000 VND / image
            if (request.getEngraving().getCustomImages() != null && !request.getEngraving().getCustomImages().isEmpty()) {
                int imgCount = request.getEngraving().getCustomImages().size();
                BigDecimal customImgCost = CUSTOM_ENGRAVING_IMAGE_PRICE.multiply(BigDecimal.valueOf(imgCount));
                engravingCost = engravingCost.add(customImgCost);
                totalDifficulty = totalDifficulty.add(new BigDecimal("0.3000").multiply(BigDecimal.valueOf(imgCount)));

                for (CustomEngravingImageDto img : request.getEngraving().getCustomImages()) {
                    if ("OUTER_BAND".equalsIgnoreCase(img.getPlacement())) {
                        hasOuterEngraving = true;
                    }
                }
            }
        }

        totalEstimate = totalEstimate.add(engravingCost);

        // Check textured surface warning on outer band engraving
        String shapeRef = request.getShapeReference() != null ? request.getShapeReference().trim() : "";
        if (hasOuterEngraving && TEXTURED_SURFACE_SHAPES.contains(shapeRef)) {
            warnings.add("Lưu ý: Mặt ngoài đai nhẫn có thiết kế kết cấu bề mặt gồ ghề (đập búa / búa bi / xoắn / lượn sóng). Hình khắc ở mặt ngoài có thể không đạt độ sắc nét hoặc bị biến dạng so với mong muốn.");
        }

        int customImgCount = (request.getEngraving() != null && request.getEngraving().getCustomImages() != null)
                ? request.getEngraving().getCustomImages().size() : 0;

        return EvaluationResultDto.builder()
                .estimatedPrice(totalEstimate.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .currency("VND")
                .difficultyScore(totalDifficulty.setScale(4, RoundingMode.HALF_UP).toPlainString())
                .rulesVersion(CURRENT_RULES_VERSION)
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
}

