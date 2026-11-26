package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.ManagerDesignRuleAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerDesignRuleAdapterTest {

    @Mock
    private RingMaterialRepository materialRepository;
    @Mock
    private RingShapeRepository shapeRepository;
    @Mock
    private RingGemstoneRepository gemstoneRepository;
    @Mock
    private RingAttachmentRepository attachmentRepository;
    @Mock
    private RingCatalogueModelRepository catalogueModelRepository;

    @InjectMocks
    private ManagerDesignRuleAdapter ruleAdapter;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("loadPackageOptions should return active options, sizes, engravingConfig and published models")
    void loadPackageOptions_Success() {
        RingMaterial silver = RingMaterial.builder()
                .materialCode("MAT-SILVER-925")
                .materialName("Bạc 925")
                .unitPrice(new BigDecimal("900000.00"))
                .currency("VND")
                .difficultyScore(new BigDecimal("1.0000"))
                .isActive(true)
                .build();

        RingShape round = RingShape.builder()
                .shapeCode("SHP-ROUND")
                .shapeName("Tròn cổ điển")
                .priceAdjustment(BigDecimal.ZERO)
                .currency("VND")
                .difficultyScore(new BigDecimal("1.0000"))
                .isActive(true)
                .build();

        RingGemstone cz = RingGemstone.builder()
                .gemstoneCode("GEM-CZ")
                .gemstoneName("Đá CZ")
                .unitPrice(new BigDecimal("50000.00"))
                .currency("VND")
                .difficultyScore(new BigDecimal("0.8000"))
                .maxQuantity(5)
                .isActive(true)
                .build();

        RingAttachment milgrain = RingAttachment.builder()
                .attachmentCode("ATT-MILGRAIN")
                .attachmentName("Viền bi")
                .unitPrice(new BigDecimal("100000.00"))
                .currency("VND")
                .difficultyScore(new BigDecimal("1.2000"))
                .maxQuantity(2)
                .isActive(true)
                .build();

        RingCatalogueModel model = RingCatalogueModel.builder()
                .modelCode("MOD-01")
                .name("Nhẫn mẫu 1")
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .isActive(true)
                .build();

        when(materialRepository.findAllByIsActiveTrue()).thenReturn(List.of(silver));
        when(shapeRepository.findAllByIsActiveTrue()).thenReturn(List.of(round));
        when(gemstoneRepository.findAllByIsActiveTrue()).thenReturn(List.of(cz));
        when(attachmentRepository.findAllByIsActiveTrue()).thenReturn(List.of(milgrain));
        when(catalogueModelRepository.findAllByPackageReferenceAndIsActiveTrue("PKG-01")).thenReturn(List.of(model));

        DesignOptionsResponseDto options = ruleAdapter.loadPackageOptions("PKG-01", null);

        assertThat(options.getPackageReference()).isEqualTo("PKG-01");
        assertThat(options.getMaterials()).hasSize(1);
        assertThat(options.getShapes()).hasSize(1);
        assertThat(options.getSizes()).contains("8", "9", "10");
        assertThat(options.getComponents()).hasSize(1);
        assertThat(options.getAttachments()).hasSize(1);
        assertThat(options.getModels()).hasSize(1);
        assertThat(options.getEngravingConfig().getSupported()).isTrue();
    }

    @Test
    @DisplayName("validateCompatibility should pass when valid material, shape, size and components are provided")
    void validateCompatibility_Valid_ReturnsTrue() {
        SaveDesignVersionRequest req = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .components(List.of(ComponentSelectionDto.builder()
                        .reference("GEM-CZ")
                        .quantity(2)
                        .placement("CENTER")
                        .build()))
                .attachments(Collections.emptyList())
                .engraving(EngravingInputDto.builder()
                        .enabled(true)
                        .text("Forever Love")
                        .placement("INNER_BAND")
                        .build())
                .build();

        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(RingMaterial.builder().materialCode("MAT-SILVER-925").isActive(true).build()));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(RingShape.builder().shapeCode("SHP-ROUND").isActive(true).build()));
        when(gemstoneRepository.findByGemstoneCode("GEM-CZ")).thenReturn(Optional.of(RingGemstone.builder().gemstoneCode("GEM-CZ").maxQuantity(5).isActive(true).build()));

        ValidationResultDto result = ruleAdapter.validateCompatibility(req, null);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getReasons()).isEmpty();
    }

    @Test
    @DisplayName("validateCompatibility should reject invalid size, unknown material, and excessive gemstone quantity")
    void validateCompatibility_InvalidFields_ReturnsReasons() {
        SaveDesignVersionRequest req = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-UNKNOWN")
                .shapeReference("SHP-ROUND")
                .ringSize("999") // invalid size
                .components(List.of(ComponentSelectionDto.builder()
                        .reference("GEM-CZ")
                        .quantity(20) // max is 5
                        .placement("INVALID_PLACEMENT")
                        .build()))
                .attachments(Collections.emptyList())
                .engraving(EngravingInputDto.builder()
                        .enabled(true)
                        .text("This engraving text is way too long and should exceed the maximum allowed fifty characters limit definitely!")
                        .placement("INNER_BAND")
                        .build())
                .build();

        when(materialRepository.findByMaterialCode("MAT-UNKNOWN")).thenReturn(Optional.empty());
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(RingShape.builder().shapeCode("SHP-ROUND").isActive(true).build()));
        when(gemstoneRepository.findByGemstoneCode("GEM-CZ")).thenReturn(Optional.of(RingGemstone.builder().gemstoneCode("GEM-CZ").maxQuantity(5).isActive(true).build()));

        ValidationResultDto result = ruleAdapter.validateCompatibility(req, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getReasons()).anyMatch(r -> r.contains("Chất liệu không tồn tại"));
        assertThat(result.getReasons()).anyMatch(r -> r.contains("Size nhẫn không nằm trong danh mục"));
        assertThat(result.getReasons()).anyMatch(r -> r.contains("vượt quá giới hạn tối đa"));
        assertThat(result.getReasons()).anyMatch(r -> r.contains("Vị trí gắn đá quý không hợp lệ"));
        assertThat(result.getReasons()).anyMatch(r -> r.contains("vượt quá giới hạn"));
    }

    @Test
    @DisplayName("evaluate should correctly calculate advisory estimate and difficulty score")
    void evaluate_CalculatesAccurately() {
        SaveDesignVersionRequest req = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .components(List.of(ComponentSelectionDto.builder().reference("GEM-CZ").quantity(2).build()))
                .attachments(List.of(AttachmentSelectionDto.builder().reference("ATT-MILGRAIN").quantity(1).build()))
                .engraving(EngravingInputDto.builder().enabled(true).text("MLAND").build())
                .build();

        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(
                RingMaterial.builder().unitPrice(new BigDecimal("900000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(
                RingShape.builder().priceAdjustment(BigDecimal.ZERO).difficultyScore(new BigDecimal("1.2000")).build()
        ));
        when(gemstoneRepository.findByGemstoneCode("GEM-CZ")).thenReturn(Optional.of(
                RingGemstone.builder().unitPrice(new BigDecimal("50000.00")).difficultyScore(new BigDecimal("0.8000")).build()
        ));
        when(attachmentRepository.findByAttachmentCode("ATT-MILGRAIN")).thenReturn(Optional.of(
                RingAttachment.builder().unitPrice(new BigDecimal("100000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));

        // Expected Price = 900k (base material) + 60k (excess 0.50g silver @ 120k/g) + 0k (shape free) + 100k (2 * 50k CZ) + 100k (attachment) + 80k (engraving) = 1,240,000 VND
        // Expected Difficulty = 1.0 + 1.2 + 2*0.8 + 1.0 + 0.5 = 5.3000
        EvaluationResultDto eval = ruleAdapter.evaluate(req);

        assertThat(eval.getEstimatedPrice()).isEqualTo("1240000.00");
        assertThat(eval.getDifficultyScore()).isEqualTo("5.3000");
        assertThat(eval.isAdvisory()).isTrue();
        assertThat(eval.getCurrency()).isEqualTo("VND");
    }

    @Test
    @DisplayName("evaluate should charge standard engraving once for multiple items and add 100k per custom image")
    void evaluate_MultiEngravingAndCustomImages() {
        SaveDesignVersionRequest req = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-BALL-PEEN")
                .ringSize("8")
                .thicknessMm(new BigDecimal("1.8"))
                .bandWidthMm(new BigDecimal("3.5"))
                .engraving(EngravingInputDto.builder()
                        .enabled(true)
                        .items(List.of(
                                EngravingItemDto.builder().id("1").content("Forever").placement("INNER_BAND").build(),
                                EngravingItemDto.builder().id("2").content("2026").placement("OUTER_BAND").build()
                        ))
                        .customImages(List.of(
                                CustomEngravingImageDto.builder().id("img1").placement("OUTER_BAND").imageUrl("data:image/png;base64,abc").build()
                        ))
                        .build())
                .build();

        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(
                RingMaterial.builder().unitPrice(new BigDecimal("900000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));
        when(shapeRepository.findByShapeCode("SHP-BALL-PEEN")).thenReturn(Optional.of(
                RingShape.builder().priceAdjustment(BigDecimal.ZERO).difficultyScore(new BigDecimal("1.8000")).build()
        ));

        // Base material = 900k + excess (0.50g * 120k = 60k) = 960,000 VND
        // Shape SHP-BALL-PEEN = 0k (free design shape)
        // Standard engraving once = 80k
        // 1 Custom image = 100k
        // Total price = 960,000 + 0k + 80k + 100k = 1,140,000 VND
        // Warnings: OUTER_BAND engraving on SHP-BALL-PEEN must trigger textured surface warning
        EvaluationResultDto eval = ruleAdapter.evaluate(req);

        assertThat(eval.getEstimatedPrice()).isEqualTo("1140000.00");
        assertThat(eval.getEngravingCost()).isEqualTo("180000.00");
        assertThat(eval.getCustomImageCount()).isEqualTo(1);
        assertThat(eval.getWarnings()).isNotEmpty();
        assertThat(eval.getWarnings().get(0)).contains("bề mặt gồ ghề");
    }

    @Test
    @DisplayName("evaluate should fix base price for ring mass <= 3.0g (Silver: 900k, Copper/Steel: 750k)")
    void evaluate_FixedBasePriceWhenMassUnderOrEqual3Grams() {
        // Slim ring geometry: size 6, thickness 1.0mm, width 1.8mm -> weight ~0.92g (well under 3.0g)
        SaveDesignVersionRequest silverReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("6")
                .thicknessMm(new BigDecimal("1.0"))
                .bandWidthMm(new BigDecimal("1.8"))
                .build();

        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(
                RingMaterial.builder().unitPrice(new BigDecimal("900000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(
                RingShape.builder().priceAdjustment(BigDecimal.ZERO).difficultyScore(new BigDecimal("1.0000")).build()
        ));

        EvaluationResultDto silverEval = ruleAdapter.evaluate(silverReq);
        assertThat(silverEval.getEstimatedPrice()).isEqualTo("900000.00");
        assertThat(silverEval.getMaterialCost()).isEqualTo("900000.00");
        assertThat(silverEval.getExcessMaterialCost()).isEqualTo("0.00");
        assertThat(new BigDecimal(silverEval.getEstimatedWeightGrams())).isLessThanOrEqualTo(new BigDecimal("3.00"));

        // Copper ring under 3g
        SaveDesignVersionRequest copperReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE-01")
                .materialReference("MAT-COPPER")
                .shapeReference("SHP-ROUND")
                .ringSize("6")
                .thicknessMm(new BigDecimal("1.0"))
                .bandWidthMm(new BigDecimal("1.8"))
                .build();

        when(materialRepository.findByMaterialCode("MAT-COPPER")).thenReturn(Optional.of(
                RingMaterial.builder().unitPrice(new BigDecimal("750000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));

        EvaluationResultDto copperEval = ruleAdapter.evaluate(copperReq);
        assertThat(copperEval.getEstimatedPrice()).isEqualTo("750000.00");
        assertThat(copperEval.getMaterialCost()).isEqualTo("750000.00");
        assertThat(copperEval.getExcessMaterialCost()).isEqualTo("0.00");

        // Stainless steel ring under 3g
        SaveDesignVersionRequest steelReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE-01")
                .materialReference("MAT-STAINLESS-STEEL")
                .shapeReference("SHP-ROUND")
                .ringSize("6")
                .thicknessMm(new BigDecimal("1.0"))
                .bandWidthMm(new BigDecimal("1.8"))
                .build();

        when(materialRepository.findByMaterialCode("MAT-STAINLESS-STEEL")).thenReturn(Optional.of(
                RingMaterial.builder().unitPrice(new BigDecimal("750000.00")).difficultyScore(new BigDecimal("1.2000")).build()
        ));

        EvaluationResultDto steelEval = ruleAdapter.evaluate(steelReq);
        assertThat(steelEval.getEstimatedPrice()).isEqualTo("750000.00");
        assertThat(steelEval.getMaterialCost()).isEqualTo("750000.00");
        assertThat(steelEval.getExcessMaterialCost()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("evaluate should calculate excess cost as 120,000 * (totalMass - 3) when mass > 3.0g")
    void evaluate_ProportionalExcessPriceWhenMassOver3Grams() {
        // Silver ring with size 8, thickness 1.8mm, width 3.5mm -> weight = 3.50g (excess 0.50g)
        SaveDesignVersionRequest silverReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .thicknessMm(new BigDecimal("1.8"))
                .bandWidthMm(new BigDecimal("3.5"))
                .build();

        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(
                RingMaterial.builder().unitPrice(new BigDecimal("900000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(
                RingShape.builder().priceAdjustment(BigDecimal.ZERO).difficultyScore(new BigDecimal("1.0000")).build()
        ));

        // Excess = 0.50g * 120,000 = 60,000; total = 900,000 + 60,000 = 960,000
        EvaluationResultDto silverEval = ruleAdapter.evaluate(silverReq);
        assertThat(silverEval.getEstimatedPrice()).isEqualTo("960000.00");
        assertThat(silverEval.getMaterialCost()).isEqualTo("960000.00");
        assertThat(silverEval.getExcessMaterialCost()).isEqualTo("60000.00");
        assertThat(silverEval.getEstimatedWeightGrams()).isEqualTo("3.50");
    }

    @Test
    @DisplayName("validateCompatibility should allow up to 10 total gemstones and reject 11")
    void validateCompatibility_AggregateGemstoneLimit() {
        // 5 CZ + 5 Moissanite = 10 -> Valid
        SaveDesignVersionRequest validReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .components(List.of(
                        ComponentSelectionDto.builder().reference("GEM-CZ").quantity(5).placement("CENTER").build(),
                        ComponentSelectionDto.builder().reference("GEM-MOISS").quantity(5).placement("HALO").build()
                ))
                .build();

        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(RingMaterial.builder().materialCode("MAT-SILVER-925").isActive(true).build()));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(RingShape.builder().shapeCode("SHP-ROUND").isActive(true).build()));
        when(gemstoneRepository.findByGemstoneCode("GEM-CZ")).thenReturn(Optional.of(RingGemstone.builder().gemstoneCode("GEM-CZ").maxQuantity(10).isActive(true).build()));
        when(gemstoneRepository.findByGemstoneCode("GEM-MOISS")).thenReturn(Optional.of(RingGemstone.builder().gemstoneCode("GEM-MOISS").maxQuantity(10).isActive(true).build()));

        ValidationResultDto validResult = ruleAdapter.validateCompatibility(validReq, null);
        assertThat(validResult.isValid()).isTrue();

        // 6 CZ + 5 Moissanite = 11 -> Rejects with GEMSTONE_TOTAL_LIMIT_EXCEEDED
        SaveDesignVersionRequest invalidReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .components(List.of(
                        ComponentSelectionDto.builder().reference("GEM-CZ").quantity(6).placement("CENTER").build(),
                        ComponentSelectionDto.builder().reference("GEM-MOISS").quantity(5).placement("HALO").build()
                ))
                .build();

        ValidationResultDto invalidResult = ruleAdapter.validateCompatibility(invalidReq, null);
        assertThat(invalidResult.isValid()).isFalse();
        assertThat(invalidResult.getReasons()).anyMatch(r -> r.contains("GEMSTONE_TOTAL_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("validateCompatibility should enforce separate quotas: max 2 TEXT, max 2 SYMBOL, max 2 CUSTOM_IMAGE")
    void validateCompatibility_EngravingQuotas() {
        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(RingMaterial.builder().materialCode("MAT-SILVER-925").isActive(true).build()));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(RingShape.builder().shapeCode("SHP-ROUND").isActive(true).build()));

        // 3 TEXT items -> Rejects with TEXT_ENGRAVING_LIMIT_EXCEEDED
        SaveDesignVersionRequest req3Text = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .engraving(EngravingInputDto.builder()
                        .enabled(true)
                        .items(List.of(
                                EngravingItemDto.builder().id("1").type("TEXT").content("A").placement("INNER_BAND").build(),
                                EngravingItemDto.builder().id("2").type("TEXT").content("B").placement("INNER_BAND").build(),
                                EngravingItemDto.builder().id("3").type("TEXT").content("C").placement("INNER_BAND").build()
                        ))
                        .build())
                .build();

        ValidationResultDto res3Text = ruleAdapter.validateCompatibility(req3Text, null);
        assertThat(res3Text.isValid()).isFalse();
        assertThat(res3Text.getReasons()).anyMatch(r -> r.contains("TEXT_ENGRAVING_LIMIT_EXCEEDED"));

        // 3 SYMBOL items -> Rejects with SYMBOL_ENGRAVING_LIMIT_EXCEEDED
        SaveDesignVersionRequest req3Symbol = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .engraving(EngravingInputDto.builder()
                        .enabled(true)
                        .items(List.of(
                                EngravingItemDto.builder().id("1").type("SYMBOL").content("♥").placement("INNER_BAND").build(),
                                EngravingItemDto.builder().id("2").type("SYMBOL").content("★").placement("INNER_BAND").build(),
                                EngravingItemDto.builder().id("3").type("SYMBOL").content("✿").placement("INNER_BAND").build()
                        ))
                        .build())
                .build();

        ValidationResultDto res3Symbol = ruleAdapter.validateCompatibility(req3Symbol, null);
        assertThat(res3Symbol.isValid()).isFalse();
        assertThat(res3Symbol.getReasons()).anyMatch(r -> r.contains("SYMBOL_ENGRAVING_LIMIT_EXCEEDED"));

        // 3 Custom images -> Rejects with IMAGE_ENGRAVING_LIMIT_EXCEEDED
        SaveDesignVersionRequest req3Images = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .engraving(EngravingInputDto.builder()
                        .enabled(false)
                        .customImages(List.of(
                                CustomEngravingImageDto.builder().id("1").placement("INNER_BAND").contrast(50).sharpness(50).build(),
                                CustomEngravingImageDto.builder().id("2").placement("INNER_BAND").contrast(50).sharpness(50).build(),
                                CustomEngravingImageDto.builder().id("3").placement("INNER_BAND").contrast(50).sharpness(50).build()
                        ))
                        .build())
                .build();

        ValidationResultDto res3Images = ruleAdapter.validateCompatibility(req3Images, null);
        assertThat(res3Images.isValid()).isFalse();
        assertThat(res3Images.getReasons()).anyMatch(r -> r.contains("IMAGE_ENGRAVING_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("validateCompatibility should reject contrast or sharpness outside 0..100")
    void validateCompatibility_ImagePreprocessingLimits() {
        when(materialRepository.findByMaterialCode("MAT-SILVER-925")).thenReturn(Optional.of(RingMaterial.builder().materialCode("MAT-SILVER-925").isActive(true).build()));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(RingShape.builder().shapeCode("SHP-ROUND").isActive(true).build()));

        // contrast = 105 -> Rejects with IMAGE_PREPROCESSING_INVALID
        SaveDesignVersionRequest reqBadContrast = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .engraving(EngravingInputDto.builder()
                        .enabled(false)
                        .customImages(List.of(
                                CustomEngravingImageDto.builder().id("1").placement("INNER_BAND").contrast(105).sharpness(50).build()
                        ))
                        .build())
                .build();

        ValidationResultDto resBadContrast = ruleAdapter.validateCompatibility(reqBadContrast, null);
        assertThat(resBadContrast.isValid()).isFalse();
        assertThat(resBadContrast.getReasons()).anyMatch(r -> r.contains("IMAGE_PREPROCESSING_INVALID"));

        // sharpness = -5 -> Rejects with IMAGE_PREPROCESSING_INVALID
        SaveDesignVersionRequest reqBadSharpness = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .engraving(EngravingInputDto.builder()
                        .enabled(false)
                        .customImages(List.of(
                                CustomEngravingImageDto.builder().id("1").placement("INNER_BAND").contrast(50).sharpness(-5).build()
                        ))
                        .build())
                .build();

        ValidationResultDto resBadSharpness = ruleAdapter.validateCompatibility(reqBadSharpness, null);
        assertThat(resBadSharpness.isValid()).isFalse();
        assertThat(resBadSharpness.getReasons()).anyMatch(r -> r.contains("IMAGE_PREPROCESSING_INVALID"));
    }
}

