package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
class RingDifficultyTestSeedHelper {

    @Autowired private RingMaterialRepository materialRepository;
    @Autowired private RingShapeRepository shapeRepository;
    @Autowired private RingGemstoneRepository gemstoneRepository;
    @Autowired private RingAttachmentRepository attachmentRepository;
    @Autowired private RingCatalogueModelRepository modelRepository;
    @Autowired private RingDifficultyPolicyVersionRepository policyVersionRepository;
    @Autowired private RingDifficultyOptionScoreRepository optionScoreRepository;
    @Autowired private RingDifficultyEffectivePolicyRepository effectivePolicyRepository;
    @Autowired private RingDifficultyCatalogueRevisionRepository catalogueRevisionRepository;
    @Autowired private RingDifficultyPublicationAuditRepository auditRepository;

    void ensureSeeded() {
        seedMaterial("MAT-SILVER-925", "Bạc Ý 925", "900000.00", "1.0000", true);
        seedMaterial("MAT-COPPER", "Đồng Đỏ", "750000.00", "1.0000", true);
        seedMaterial("MAT-STAINLESS-STEEL", "Thép Không Gỉ 316L", "750000.00", "1.2000", true);
        seedMaterial("MAT-GOLD-14K", "Vàng 14K", "1800000.00", "2.5000", false);

        seedShape("SHP-CLASSIC-ROUND", "Tròn cổ điển", "0.00", "1.0000");
        seedShape("SHP-COMFORT-FIT", "Comfort Fit", "50000.00", "1.2000");
        seedShape("SHP-TWISTED", "Xoắn vô cực", "120000.00", "2.0000");
        seedShape("SHP-HAMMERED", "Búa gõ", "80000.00", "1.8000");
        seedShape("SHP-SINGLE-TWIST", "Xoắn đơn", "80000.00", "1.4000");
        seedShape("SHP-WAVE", "Lượn sóng", "100000.00", "1.6000");
        seedShape("SHP-BALL-PEEN", "Búa bi", "90000.00", "1.8000");
        seedShape("SHP-FLAT-BAND", "Đai phẳng", "60000.00", "1.3000");

        seedGemstone("GEM-CZ-WHITE-2MM", "Đá CZ Trắng 2mm", "80000.00", "0.8000", 10);
        seedGemstone("GEM-CZ-BLUE-2MM", "Đá CZ Xanh Lam 2mm", "90000.00", "0.8000", 10);
        seedGemstone("GEM-CZ-RED-2MM", "Đá CZ Đỏ Ruby 2mm", "90000.00", "0.8000", 10);
        seedGemstone("GEM-MOISSANITE-3MM", "Đá Moissanite 3mm", "450000.00", "1.5000", 4);

        seedAttachment("ATT-BEAD-EDGE", "Viền hạt bi Milgrain", "100000.00", "1.2000", 2);
        seedAttachment("ATT-SATIN-FINISH", "Xử lý phay xước mờ", "60000.00", "0.8000", 1);
        seedAttachment("ATT-HEART-EMBOSS", "Dập nổi trái tim nhỏ", "70000.00", "1.0000", 2);

        seedModel("MOD-WS-SILVER-CLASSIC", "PKG-COUPLE-SILVER", "Mẫu nhẫn Bạc Tròn Cổ Điển", "MAT-SILVER-925", "SHP-CLASSIC-ROUND", "{\"components\":[],\"attachments\":[]}");
        seedModel("MOD-WS-SILVER-TWIST", "PKG-COUPLE-SILVER", "Mẫu nhẫn Bạc Xoắn Vô Cực", "MAT-SILVER-925", "SHP-TWISTED", "{\"components\":[{\"reference\":\"GEM-CZ-WHITE-2MM\",\"quantity\":1,\"placement\":\"CENTER\"}],\"attachments\":[]}");
        seedModel("MOD-WS-SINGLE-TWIST", "PKG-COUPLE-SILVER", "Mẫu nhẫn Bạc Xoắn Đơn", "MAT-SILVER-925", "SHP-SINGLE-TWIST", "{\"components\":[],\"attachments\":[]}");
        seedModel("MOD-WS-WAVE-FLOW", "PKG-COUPLE-SILVER", "Mẫu nhẫn Bạc Lượn Sóng", "MAT-SILVER-925", "SHP-WAVE", "{\"components\":[{\"reference\":\"GEM-CZ-BLUE-2MM\",\"quantity\":2,\"placement\":\"ACCENT_LEFT\"}],\"attachments\":[]}");
        seedModel("MOD-WS-BALL-HAMMERED", "PKG-COUPLE-SILVER", "Mẫu nhẫn Bạc Búa Bi", "MAT-SILVER-925", "SHP-BALL-PEEN", "{\"components\":[],\"attachments\":[]}");
        seedModel("MOD-WS-FACET-HAMMERED", "PKG-COUPLE-SILVER", "Mẫu nhẫn Bạc Giác Búa", "MAT-SILVER-925", "SHP-HAMMERED", "{\"components\":[],\"attachments\":[]}");
        seedModel("MOD-WS-COPPER-RUSTIC", null, "Mẫu nhẫn Đồng Mộc", "MAT-COPPER", "SHP-CLASSIC-ROUND", "{\"components\":[],\"attachments\":[]}");
        seedModel("MOD-WS-COPPER-TWIST", null, "Mẫu nhẫn Đồng Xoắn", "MAT-COPPER", "SHP-SINGLE-TWIST", "{\"components\":[{\"reference\":\"GEM-CZ-RED-2MM\",\"quantity\":1,\"placement\":\"CENTER\"}],\"attachments\":[]}");
        seedModel("MOD-WS-STEEL-BRUSHED", null, "Mẫu nhẫn Thép Phay Xước", "MAT-STAINLESS-STEEL", "SHP-COMFORT-FIT", "{\"components\":[],\"attachments\":[{\"reference\":\"ATT-SATIN-FINISH\",\"quantity\":1,\"placement\":\"OUTER_SURFACE\"}]}");
        seedModel("MOD-WS-STEEL-MINIMAL", null, "Mẫu nhẫn Thép Đai Phẳng", "MAT-STAINLESS-STEEL", "SHP-FLAT-BAND", "{\"components\":[],\"attachments\":[]}");
        seedModel("MOD-WS-MILGRAIN-VINTAGE", "PKG-COUPLE-SILVER", "Mẫu nhẫn Bạc Viền Bi", "MAT-SILVER-925", "SHP-CLASSIC-ROUND", "{\"components\":[{\"reference\":\"GEM-MOISSANITE-3MM\",\"quantity\":1,\"placement\":\"CENTER\"}],\"attachments\":[{\"reference\":\"ATT-BEAD-EDGE\",\"quantity\":1,\"placement\":\"EDGES\"}]}");
        seedModel("MOD-WS-HAMMERED-HEART", "PKG-COUPLE-SILVER", "Mẫu nhẫn Búa Gõ Khắc Tim", "MAT-SILVER-925", "SHP-HAMMERED", "{\"components\":[],\"attachments\":[{\"reference\":\"ATT-HEART-EMBOSS\",\"quantity\":1,\"placement\":\"OUTER_SURFACE\"}]}");

        if (effectivePolicyRepository.findById(RingDifficultyEffectivePolicy.DEFAULT_SCOPE).isEmpty()) {
            Instant now = Instant.now();
            RingDifficultyPolicyVersion v1 = policyVersionRepository.findByRulesVersion("2026.11-v1.0")
                    .orElseGet(() -> policyVersionRepository.save(RingDifficultyPolicyVersion.builder()
                            .rulesVersion("2026.11-v1.0")
                            .engravingBonus(new BigDecimal("0.5000"))
                            .customImageBonusPerImage(new BigDecimal("0.3000"))
                            .easyMax(new BigDecimal("3.0000"))
                            .mediumMax(new BigDecimal("6.0000"))
                            .hardMax(new BigDecimal("9.0000"))
                            .veryHardMax(new BigDecimal("11.0000"))
                            .waxAbove(new BigDecimal("9.0000"))
                            .silverClayGemstonesAbove(5)
                            .publishedBy("SYSTEM_SEED")
                            .publishedAt(now)
                            .build()));

            if (optionScoreRepository.findAllByPolicyVersionId(v1.getId()).isEmpty()) {
                List<RingDifficultyOptionScore> scores = new ArrayList<>();
                materialRepository.findAllByIsActiveTrue().forEach(m -> scores.add(RingDifficultyOptionScore.builder()
                        .policyVersionId(v1.getId()).optionKind(OptionScoreKind.MATERIAL)
                        .optionReference(m.getMaterialCode()).score(m.getDifficultyScore()).build()));
                shapeRepository.findAllByIsActiveTrue().forEach(s -> scores.add(RingDifficultyOptionScore.builder()
                        .policyVersionId(v1.getId()).optionKind(OptionScoreKind.SHAPE)
                        .optionReference(s.getShapeCode()).score(s.getDifficultyScore()).build()));
                gemstoneRepository.findAllByIsActiveTrue().forEach(g -> scores.add(RingDifficultyOptionScore.builder()
                        .policyVersionId(v1.getId()).optionKind(OptionScoreKind.GEMSTONE)
                        .optionReference(g.getGemstoneCode()).score(g.getDifficultyScore()).build()));
                attachmentRepository.findAllByIsActiveTrue().forEach(a -> scores.add(RingDifficultyOptionScore.builder()
                        .policyVersionId(v1.getId()).optionKind(OptionScoreKind.ATTACHMENT)
                        .optionReference(a.getAttachmentCode()).score(a.getDifficultyScore()).build()));
                optionScoreRepository.saveAll(scores);
            }

            effectivePolicyRepository.save(RingDifficultyEffectivePolicy.builder()
                    .policyScope(RingDifficultyEffectivePolicy.DEFAULT_SCOPE)
                    .policyVersionId(v1.getId())
                    .updatedAt(now)
                    .build());

            if (catalogueRevisionRepository.findById(RingDifficultyCatalogueRevision.DEFAULT_SCOPE).isEmpty()) {
                catalogueRevisionRepository.save(RingDifficultyCatalogueRevision.builder()
                        .revisionScope(RingDifficultyCatalogueRevision.DEFAULT_SCOPE)
                        .revisionNumber(1L)
                        .updatedAt(now)
                        .build());
            }

            if (auditRepository.count() == 0) {
                auditRepository.save(RingDifficultyPublicationAudit.builder()
                        .actorReference("SYSTEM_SEED")
                        .previousPolicyVersionId(null)
                        .newPolicyVersionId(v1.getId())
                        .correlationId("TEST-SEED")
                        .reason("Test seed")
                        .oldValuesSnapshot(null)
                        .newValuesSnapshot("{\"rulesVersion\":\"2026.11-v1.0\"}")
                        .publishedAt(now)
                        .build());
            }
        }
    }

    private void seedMaterial(String code, String name, String price, String score, boolean active) {
        if (materialRepository.findByMaterialCode(code).isEmpty()) {
            materialRepository.save(RingMaterial.builder()
                    .materialCode(code).materialName(name).unitPrice(new BigDecimal(price))
                    .currency("VND").difficultyScore(new BigDecimal(score)).isActive(active).build());
        }
    }

    private void seedShape(String code, String name, String adj, String score) {
        if (shapeRepository.findByShapeCode(code).isEmpty()) {
            shapeRepository.save(RingShape.builder()
                    .shapeCode(code).shapeName(name).priceAdjustment(new BigDecimal(adj))
                    .currency("VND").difficultyScore(new BigDecimal(score)).isActive(true).build());
        }
    }

    private void seedGemstone(String code, String name, String price, String score, int maxQty) {
        if (gemstoneRepository.findByGemstoneCode(code).isEmpty()) {
            gemstoneRepository.save(RingGemstone.builder()
                    .gemstoneCode(code).gemstoneName(name).unitPrice(new BigDecimal(price))
                    .currency("VND").difficultyScore(new BigDecimal(score)).maxQuantity(maxQty).isActive(true).build());
        }
    }

    private void seedAttachment(String code, String name, String price, String score, int maxQty) {
        if (attachmentRepository.findByAttachmentCode(code).isEmpty()) {
            attachmentRepository.save(RingAttachment.builder()
                    .attachmentCode(code).attachmentName(name).unitPrice(new BigDecimal(price))
                    .currency("VND").difficultyScore(new BigDecimal(score)).maxQuantity(maxQty).isActive(true).build());
        }
    }

    private void seedModel(String code, String pkg, String name, String mat, String shp, String defaults) {
        if (modelRepository.findByModelCode(code).isEmpty()) {
            modelRepository.save(RingCatalogueModel.builder()
                    .modelCode(code).packageReference(pkg).name(name)
                    .materialReference(mat).shapeReference(shp)
                    .defaultComponents(defaults).isActive(true).build());
        }
    }
}
