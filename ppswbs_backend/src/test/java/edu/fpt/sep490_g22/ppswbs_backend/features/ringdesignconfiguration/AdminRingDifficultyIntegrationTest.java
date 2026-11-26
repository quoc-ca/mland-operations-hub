package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.DesignOptionRulesPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.AdminRingDifficultyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminRingDifficultyIntegrationTest {

    @Autowired
    private AdminRingDifficultyService adminService;

    @Autowired
    private DesignOptionRulesPort rulesPort;

    @Autowired
    private WorkshopRingDesignApplicationService designService;

    @Autowired
    private RingMaterialRepository materialRepository;

    @Autowired
    private RingDifficultyPreviewProofRepository previewProofRepository;

    @Autowired
    private RingDifficultyCatalogueRevisionRepository catalogueRevisionRepository;

    @Autowired
    private RingDifficultyPublicationAuditRepository auditRepository;

    @Autowired
    private RingDifficultyTestSeedHelper seedHelper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        seedHelper.ensureSeeded();
    }

    private PolicyValuesDto copyAsCandidate(PolicyVersionDto active) {
        return PolicyValuesDto.builder()
                .optionScores(active.getOptionScores().stream()
                        .map(s -> OptionScoreDto.builder()
                                .kind(s.getKind())
                                .reference(s.getReference())
                                .score(s.getScore())
                                .build())
                        .toList())
                .engravingBonus(active.getEngravingBonus())
                .customImageBonusPerImage(active.getCustomImageBonusPerImage())
                .thresholds(DifficultyThresholdsDto.builder()
                        .easyMax(active.getThresholds().getEasyMax())
                        .mediumMax(active.getThresholds().getMediumMax())
                        .hardMax(active.getThresholds().getHardMax())
                        .veryHardMax(active.getThresholds().getVeryHardMax())
                        .waxAbove(active.getThresholds().getWaxAbove())
                        .silverClayGemstonesAbove(active.getThresholds().getSilverClayGemstonesAbove())
                        .build())
                .build();
    }

    @Test
    @DisplayName("V205 seed provides active policy, 12 active models, and general vs package-filtered discovery")
    void seedAndGeneralDiscovery_Works() {
        PolicyVersionDto active = adminService.getActivePolicy();
        assertThat(active.getRulesVersion()).isEqualTo("2026.11-v1.0");
        assertThat(active.getStatus()).isEqualTo("EFFECTIVE");
        assertThat(active.getOptionScores()).isNotEmpty();

        DesignOptionsResponseDto general = rulesPort.loadPackageOptions(null, null);
        assertThat(general.getPackageReference()).isNull();
        assertThat(general.getModels()).hasSize(12);
        assertThat(general.getModels().stream().map(DesignOptionsResponseDto.ModelOptionDto::getMaterialReference))
                .contains("MAT-SILVER-925", "MAT-COPPER", "MAT-STAINLESS-STEEL");

        DesignOptionsResponseDto silverPkg = rulesPort.loadPackageOptions("PKG-COUPLE-SILVER", null);
        assertThat(silverPkg.getPackageReference()).isEqualTo("PKG-COUPLE-SILVER");
        assertThat(silverPkg.getModels()).hasSize(8);
        assertThat(silverPkg.getModels())
                .allMatch(m -> "MAT-SILVER-925".equals(m.getMaterialReference()));
    }

    @Test
    @DisplayName("Preview blocks when candidate makes an active model TOO_RISKY and issues no proof")
    void preview_BlockingModel_IssuesNoProof() {
        PolicyVersionDto active = adminService.getActivePolicy();
        PolicyValuesDto candidate = copyAsCandidate(active);
        List<OptionScoreDto> updatedScores = new ArrayList<>(candidate.getOptionScores());
        updatedScores.replaceAll(s -> s.getKind() == OptionScoreKind.MATERIAL && "MAT-SILVER-925".equals(s.getReference())
                ? OptionScoreDto.builder().kind(s.getKind()).reference(s.getReference()).score("12.0000").build()
                : s);
        candidate.setOptionScores(updatedScores);

        PreviewResultDto result = adminService.previewPolicy(PreviewRequestDto.builder()
                .expectedActiveRulesVersion(active.getRulesVersion())
                .candidate(candidate)
                .build(), 1L);

        assertThat(result.isPublicationEligible()).isFalse();
        assertThat(result.getBlockingModelCount()).isGreaterThan(0);
        assertThat(result.getPreviewReference()).isNull();
        assertThat(result.getModelImpacts())
                .anyMatch(m -> !m.isEligible() && m.getBlockingReasons().contains("MODEL_TOO_RISKY"));
    }

    @Test
    @DisplayName("Preview and atomic publish succeed, record audit, replay idempotently after proof expiry, and preserve historical design scores")
    void previewPublishAuditReplayAndHistoricalPreservation() {
        // 1. Save a design under v1.0
        DesignResponseDto draft = designService.createDraft(CreateDesignRequest.builder()
                .startMode("BLANK")
                .build(), 501L, null, "IDEMP-HIST-CREATE");
        DesignResponseDto savedV1 = designService.saveVersion(draft.getDesignId(), SaveDesignVersionRequest.builder()
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-CLASSIC-ROUND")
                .ringSize("10")
                .components(List.of())
                .attachments(List.of())
                .engraving(EngravingInputDto.builder().enabled(true).text("MLAND").placement("INNER_BAND").build())
                .build(), 501L, null, 1L, "IDEMP-HIST-SAVE");

        assertThat(savedV1.getEvaluation().getRulesVersion()).isEqualTo("2026.11-v1.0");
        assertThat(savedV1.getEvaluation().getDifficultyScore()).isEqualTo("2.5000");

        // 2. Preview new candidate (engravingBonus 0.5 -> 1.0)
        PolicyVersionDto active = adminService.getActivePolicy();
        PolicyValuesDto candidate = copyAsCandidate(active);
        candidate.setEngravingBonus("1.0000");

        PreviewResultDto preview = adminService.previewPolicy(PreviewRequestDto.builder()
                .expectedActiveRulesVersion(active.getRulesVersion())
                .candidate(candidate)
                .samples(List.of(PreviewRequestDto.SampleSelectionDto.builder()
                        .sampleName("ADMIN-SAMPLE-1")
                        .materialReference("MAT-SILVER-925")
                        .shapeReference("SHP-CLASSIC-ROUND")
                        .engravingEnabled(true)
                        .build()))
                .build(), 77L);

        assertThat(preview.isPublicationEligible()).isTrue();
        assertThat(preview.getPreviewReference()).isNotBlank();
        assertThat(preview.getComparisons()).anyMatch(c -> "ADMIN".equals(c.getSource()));

        // 3. Publish with proof and idempotency key
        PublishRequestDto publishReq = PublishRequestDto.builder()
                .expectedActiveRulesVersion(active.getRulesVersion())
                .candidate(candidate)
                .previewReference(preview.getPreviewReference())
                .build();
        PolicyVersionDto published = adminService.publishPolicy(publishReq, 77L, "IDEMP-PUB-1", "CORR-1");

        assertThat(published.getRulesVersion()).isNotEqualTo(active.getRulesVersion());
        assertThat(published.getStatus()).isEqualTo("EFFECTIVE");
        assertThat(published.getEngravingBonus()).isEqualTo("1.0000");
        assertThat(adminService.listPolicyVersions()).hasSize(2);
        assertThat(auditRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);

        // 4. Idempotent replay succeeds even though proof was consumed
        PolicyVersionDto replayed = adminService.publishPolicy(publishReq, 77L, "IDEMP-PUB-1", "CORR-1");
        assertThat(replayed.getRulesVersion()).isEqualTo(published.getRulesVersion());

        // 5. Historical saved design keeps v1.0 score & rulesVersion
        DesignResponseDto reloaded = designService.getDesign(savedV1.getDesignId(), 501L, null);
        assertThat(reloaded.getEvaluation().getRulesVersion()).isEqualTo("2026.11-v1.0");
        assertThat(reloaded.getEvaluation().getDifficultyScore()).isEqualTo("2.5000");
    }

    @Test
    @DisplayName("Publish rejects expired proof with 409 PREVIEW_EXPIRED and catalogue revision change with 409 PREVIEW_MISMATCH")
    void publish_ExpiredOrCatalogueMismatch_Throws409() {
        PolicyVersionDto active = adminService.getActivePolicy();
        PolicyValuesDto candidate = copyAsCandidate(active);

        PreviewResultDto preview = adminService.previewPolicy(PreviewRequestDto.builder()
                .expectedActiveRulesVersion(active.getRulesVersion())
                .candidate(candidate)
                .build(), 88L);

        // Simulate catalogue revision bump
        var rev = catalogueRevisionRepository.findById(RingDifficultyCatalogueRevision.DEFAULT_SCOPE).orElseThrow();
        rev.setRevisionNumber(rev.getRevisionNumber() + 1);
        catalogueRevisionRepository.save(rev);

        assertThatThrownBy(() -> adminService.publishPolicy(PublishRequestDto.builder()
                .expectedActiveRulesVersion(active.getRulesVersion())
                .candidate(candidate)
                .previewReference(preview.getPreviewReference())
                .build(), 88L, "IDEMP-MISMATCH", "CORR-2"))
                .isInstanceOf(RingDesignException.class)
                .satisfies(ex -> {
                    RingDesignException rde = (RingDesignException) ex;
                    assertThat(rde.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rde.getCode()).isEqualTo("PREVIEW_MISMATCH");
                });

        // Fresh preview then expire all proofs
        PreviewResultDto preview2 = adminService.previewPolicy(PreviewRequestDto.builder()
                .expectedActiveRulesVersion(active.getRulesVersion())
                .candidate(candidate)
                .build(), 88L);

        previewProofRepository.findAll().forEach(p -> {
            p.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            previewProofRepository.save(p);
        });

        assertThatThrownBy(() -> adminService.publishPolicy(PublishRequestDto.builder()
                .expectedActiveRulesVersion(active.getRulesVersion())
                .candidate(candidate)
                .previewReference(preview2.getPreviewReference())
                .build(), 88L, "IDEMP-EXPIRED", "CORR-3"))
                .isInstanceOf(RingDesignException.class)
                .satisfies(ex -> {
                    RingDesignException rde = (RingDesignException) ex;
                    assertThat(rde.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rde.getCode()).isEqualTo("PREVIEW_EXPIRED");
                });
    }

    @Test
    @DisplayName("Newly active unscored option is visible with selectable=false and DIFFICULTY_SCORE_PENDING")
    void unscoredActiveOption_VisibleButDisabled() {
        materialRepository.save(RingMaterial.builder()
                .materialCode("MAT-TITANIUM-NEW")
                .materialName("Titanium Mới")
                .unitPrice(new BigDecimal("1100000.00"))
                .currency("VND")
                .difficultyScore(new BigDecimal("2.0000"))
                .isActive(true)
                .build());

        DesignOptionsResponseDto options = rulesPort.loadPackageOptions(null, null);
        var titanium = options.getMaterials().stream()
                .filter(m -> "MAT-TITANIUM-NEW".equals(m.getReference()))
                .findFirst()
                .orElseThrow();
        assertThat(titanium.isSelectable()).isFalse();
        assertThat(titanium.getUnavailableReason()).isEqualTo("DIFFICULTY_SCORE_PENDING");

        ValidationResultDto val = rulesPort.validateCompatibility(SaveDesignVersionRequest.builder()
                .materialReference("MAT-TITANIUM-NEW")
                .shapeReference("SHP-CLASSIC-ROUND")
                .ringSize("10")
                .build(), null);
        assertThat(val.isValid()).isFalse();
        assertThat(val.getReasons()).anyMatch(r -> r.contains("DIFFICULTY_SCORE_PENDING"));
    }

    @Test
    @DisplayName("Evaluation returns material-aware recommendations: S925->S999 disclosure, Copper/Steel NO_COMPATIBLE_PACKAGE, Wax >9, TooRisky >11")
    void evaluation_RecommendationPrecedenceAndDisclosures() {
        // Silver Clay (>5 gems) -> S925 to S999 disclosure
        EvaluationResultDto silverClay = rulesPort.evaluate(SaveDesignVersionRequest.builder()
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-CLASSIC-ROUND")
                .ringSize("10")
                .components(List.of(ComponentSelectionDto.builder().reference("GEM-CZ-WHITE-2MM").quantity(6).build()))
                .attachments(List.of())
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build());
        assertThat(silverClay.getRecommendationCode()).isEqualTo(RecommendationCode.SILVER_CLAY);
        assertThat(silverClay.getMaterialDisclosure()).contains("S999");

        // Copper -> NO_COMPATIBLE_PACKAGE
        EvaluationResultDto copper = rulesPort.evaluate(SaveDesignVersionRequest.builder()
                .materialReference("MAT-COPPER")
                .shapeReference("SHP-CLASSIC-ROUND")
                .ringSize("10")
                .components(List.of())
                .attachments(List.of())
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build());
        assertThat(copper.getRecommendationCode()).isEqualTo(RecommendationCode.NO_COMPATIBLE_PACKAGE);
        assertThat(copper.getRecommendedPackageReference()).isNull();

        // Wax Casting (>9 and <=11)
        EvaluationResultDto wax = rulesPort.evaluate(SaveDesignVersionRequest.builder()
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-TWISTED") // 1 + 2 = 3
                .ringSize("10")
                .components(List.of(ComponentSelectionDto.builder().reference("GEM-MOISSANITE-3MM").quantity(4).build())) // + 6 = 9
                .attachments(List.of())
                .engraving(EngravingInputDto.builder().enabled(true).text("A").placement("INNER_BAND").build()) // + 0.5 = 9.5
                .build());
        assertThat(wax.getDifficultyScore()).isEqualTo("9.5000");
        assertThat(wax.getDifficultyLevel()).isEqualTo(DifficultyLevel.VERY_HARD);
        assertThat(wax.getRecommendationCode()).isEqualTo(RecommendationCode.WAX_CASTING);

        // Too Risky (>11)
        EvaluationResultDto tooRisky = rulesPort.evaluate(SaveDesignVersionRequest.builder()
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-TWISTED")
                .ringSize("10")
                .components(List.of(
                        ComponentSelectionDto.builder().reference("GEM-MOISSANITE-3MM").quantity(4).build(),
                        ComponentSelectionDto.builder().reference("GEM-CZ-WHITE-2MM").quantity(4).build())) // 3 + 6 + 3.2 = 12.2
                .attachments(List.of())
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build());
        assertThat(tooRisky.getDifficultyLevel()).isEqualTo(DifficultyLevel.TOO_RISKY);
        assertThat(tooRisky.getRecommendationCode()).isEqualTo(RecommendationCode.TOO_RISKY);
        assertThat(tooRisky.getRecommendedPackageReference()).isNull();
    }
}
