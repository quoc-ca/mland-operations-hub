package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.DifficultyLevel;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.OptionScoreKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RecommendationCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Pure, deterministic evaluator for workshop ring difficulty score, level classification,
 * and material-aware workshop recommendation under a given policy snapshot.
 */
@Component
public class RingDifficultyPolicyEvaluator {

    public static final String SILVER_925_CODE = "MAT-SILVER-925";
    public static final String COPPER_CODE = "MAT-COPPER";
    public static final String STEEL_CODE = "MAT-STAINLESS-STEEL";

    public static final String PKG_SIGNATURE_SILVER = "PKG-COUPLE-SILVER";
    public static final String PKG_SIGNATURE_BRASS = "PKG-SIGNATURE-BRASS";
    public static final String PKG_SILVER_CLAY = "PKG-SILVER-CLAY";
    public static final String PKG_WAX_CASTING = "PKG-WAX-CASTING";

    public static final String S925_TO_S999_DISCLOSURE =
            "Lưu ý: Gói workshop Silver Clay sử dụng bạc S999 tại xưởng; chất liệu S925 trong bản thiết kế đã lưu của bạn vẫn được giữ nguyên.";
    public static final String WAX_TWO_SESSION_NOTE =
            "Gợi ý Wax Casting: Thiết kế có độ khó cao, cần 2 buổi workshop cách nhau ít nhất 2 ngày làm việc của xưởng.";
    public static final String NO_COMPATIBLE_PACKAGE_NOTE =
            "Chất liệu này hiện chưa có gói workshop chế tác tương thích trực tiếp; vui lòng liên hệ tư vấn hoặc khám phá các workshop chung.";
    public static final String TOO_RISKY_NOTE =
            "Thiết kế vượt ngưỡng độ khó an toàn của workshop tự chế tác; vui lòng liên hệ tư vấn hoặc đặt chế tác riêng.";

    public record PolicySnapshot(
            String rulesVersion,
            BigDecimal engravingBonus,
            BigDecimal customImageBonusPerImage,
            BigDecimal easyMax,
            BigDecimal mediumMax,
            BigDecimal hardMax,
            BigDecimal veryHardMax,
            BigDecimal waxAbove,
            int silverClayGemstonesAbove,
            Map<OptionKey, BigDecimal> optionScores
    ) {
        public BigDecimal scoreOf(OptionScoreKind kind, String reference) {
            if (reference == null) return null;
            return optionScores.get(new OptionKey(kind, reference));
        }
    }

    public record OptionKey(OptionScoreKind kind, String reference) {}

    public record RecommendationOutcome(
            DifficultyLevel difficultyLevel,
            RecommendationCode recommendationCode,
            String recommendedPackageReference,
            String materialDisclosure,
            String reason
    ) {}

    public DifficultyLevel classify(BigDecimal score, PolicySnapshot policy) {
        if (score.compareTo(policy.easyMax()) <= 0) {
            return DifficultyLevel.EASY;
        }
        if (score.compareTo(policy.mediumMax()) <= 0) {
            return DifficultyLevel.MEDIUM;
        }
        if (score.compareTo(policy.hardMax()) <= 0) {
            return DifficultyLevel.HARD;
        }
        if (score.compareTo(policy.veryHardMax()) <= 0) {
            return DifficultyLevel.VERY_HARD;
        }
        return DifficultyLevel.TOO_RISKY;
    }

    public RecommendationOutcome recommend(
            BigDecimal score,
            String materialReference,
            int totalGemstones,
            PolicySnapshot policy
    ) {
        DifficultyLevel level = classify(score, policy);
        String mat = materialReference != null ? materialReference.trim() : "";

        // 1. Above too-risky cutoff -> TOO_RISKY, no workshop recommendation
        if (score.compareTo(policy.veryHardMax()) > 0) {
            return new RecommendationOutcome(level, RecommendationCode.TOO_RISKY, null, null, TOO_RISKY_NOTE);
        }

        // Copper and Steel 316L currently have no approved compatible workshop package at any score
        boolean isSilver = SILVER_925_CODE.equalsIgnoreCase(mat);
        boolean isCopperOrSteel = COPPER_CODE.equalsIgnoreCase(mat) || STEEL_CODE.equalsIgnoreCase(mat);
        if (isCopperOrSteel || !isSilver) {
            return new RecommendationOutcome(level, RecommendationCode.NO_COMPATIBLE_PACKAGE, null, null, NO_COMPATIBLE_PACKAGE_NOTE);
        }

        // 2. Above Wax Casting difficulty cutoff
        if (score.compareTo(policy.waxAbove()) > 0) {
            return new RecommendationOutcome(level, RecommendationCode.WAX_CASTING, PKG_WAX_CASTING, null, WAX_TWO_SESSION_NOTE);
        }

        // 3. Above total-gemstone cutoff -> Silver Clay for silver family with S999 disclosure for S925
        if (totalGemstones > policy.silverClayGemstonesAbove()) {
            return new RecommendationOutcome(
                    level,
                    RecommendationCode.SILVER_CLAY,
                    PKG_SILVER_CLAY,
                    S925_TO_S999_DISCLOSURE,
                    "Gợi ý Silver Clay: Thiết kế có nhiều chi tiết đính đá (" + totalGemstones + " viên)."
            );
        }

        // 4. Applicable Signature workshop
        return new RecommendationOutcome(
                level,
                RecommendationCode.SIGNATURE_SILVER,
                PKG_SIGNATURE_SILVER,
                null,
                "Phù hợp với workshop Signature Silver tiêu chuẩn."
        );
    }

    public BigDecimal roundScore(BigDecimal raw) {
        return raw.setScale(4, RoundingMode.HALF_UP);
    }
}
