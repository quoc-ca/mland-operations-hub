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
                .unitPrice(new BigDecimal("250000.00"))
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
                .unitPrice(new BigDecimal("80000.00"))
                .currency("VND")
                .difficultyScore(new BigDecimal("0.8000"))
                .maxQuantity(5)
                .isActive(true)
                .build();

        RingAttachment milgrain = RingAttachment.builder()
                .attachmentCode("ATT-MILGRAIN")
                .attachmentName("Viền bi")
                .unitPrice(new BigDecimal("60000.00"))
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
                RingMaterial.builder().unitPrice(new BigDecimal("250000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));
        when(shapeRepository.findByShapeCode("SHP-ROUND")).thenReturn(Optional.of(
                RingShape.builder().priceAdjustment(new BigDecimal("50000.00")).difficultyScore(new BigDecimal("1.2000")).build()
        ));
        when(gemstoneRepository.findByGemstoneCode("GEM-CZ")).thenReturn(Optional.of(
                RingGemstone.builder().unitPrice(new BigDecimal("80000.00")).difficultyScore(new BigDecimal("0.8000")).build()
        ));
        when(attachmentRepository.findByAttachmentCode("ATT-MILGRAIN")).thenReturn(Optional.of(
                RingAttachment.builder().unitPrice(new BigDecimal("60000.00")).difficultyScore(new BigDecimal("1.0000")).build()
        ));

        // Expected Price = 250k (material) + 50k (shape) + 160k (2 * CZ) + 60k (attachment) + 80k (engraving) = 600,000 VND
        // Expected Difficulty = 1.0 + 1.2 + 2*0.8 + 1.0 + 0.5 = 5.3000
        EvaluationResultDto eval = ruleAdapter.evaluate(req);

        assertThat(eval.getEstimatedPrice()).isEqualTo("600000.00");
        assertThat(eval.getDifficultyScore()).isEqualTo("5.3000");
        assertThat(eval.isAdvisory()).isTrue();
        assertThat(eval.getCurrency()).isEqualTo("VND");
    }
}

