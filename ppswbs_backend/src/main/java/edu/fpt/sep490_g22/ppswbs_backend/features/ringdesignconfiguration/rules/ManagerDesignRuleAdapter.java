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
            "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20"
    );
    public static final int MAX_ENGRAVING_CHARS = 50;
    public static final BigDecimal ENGRAVING_PRICE = new BigDecimal("80000.00");
    public static final List<String> ALLOWED_ENGRAVING_PLACEMENTS = List.of("INNER_BAND", "OUTER_BAND");
    public static final List<String> ALLOWED_COMPONENT_PLACEMENTS = List.of("CENTER", "ACCENT_LEFT", "ACCENT_RIGHT", "HALO");
    public static final List<String> ALLOWED_ATTACHMENT_PLACEMENTS = List.of("OUTER_SURFACE", "EDGES", "PRONGS");

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
        if (request.getEngraving() != null && Boolean.TRUE.equals(request.getEngraving().getEnabled())) {
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

        // Material cost & difficulty
        if (request.getMaterialReference() != null) {
            materialRepository.findByMaterialCode(request.getMaterialReference()).ifPresent(m -> {
                // Base material unit price
            });
            Optional<RingMaterial> matOpt = materialRepository.findByMaterialCode(request.getMaterialReference());
            if (matOpt.isPresent()) {
                totalEstimate = totalEstimate.add(matOpt.get().getUnitPrice());
                totalDifficulty = totalDifficulty.add(matOpt.get().getDifficultyScore());
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

        // Engraving cost & difficulty
        if (request.getEngraving() != null && Boolean.TRUE.equals(request.getEngraving().getEnabled())) {
            totalEstimate = totalEstimate.add(ENGRAVING_PRICE);
            totalDifficulty = totalDifficulty.add(new BigDecimal("0.5000"));
        }

        return EvaluationResultDto.builder()
                .estimatedPrice(totalEstimate.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .currency("VND")
                .difficultyScore(totalDifficulty.setScale(4, RoundingMode.HALF_UP).toPlainString())
                .rulesVersion(CURRENT_RULES_VERSION)
                .advisory(true)
                .build();
    }
}

