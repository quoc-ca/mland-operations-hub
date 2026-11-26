package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ring_difficulty_policy_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingDifficultyPolicyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rules_version", nullable = false, unique = true, length = 64)
    private String rulesVersion;

    @Column(name = "engraving_bonus", nullable = false, precision = 18, scale = 4)
    private BigDecimal engravingBonus;

    @Column(name = "custom_image_bonus_per_image", nullable = false, precision = 18, scale = 4)
    private BigDecimal customImageBonusPerImage;

    @Column(name = "easy_max", nullable = false, precision = 18, scale = 4)
    private BigDecimal easyMax;

    @Column(name = "medium_max", nullable = false, precision = 18, scale = 4)
    private BigDecimal mediumMax;

    @Column(name = "hard_max", nullable = false, precision = 18, scale = 4)
    private BigDecimal hardMax;

    @Column(name = "very_hard_max", nullable = false, precision = 18, scale = 4)
    private BigDecimal veryHardMax;

    @Column(name = "wax_above", nullable = false, precision = 18, scale = 4)
    private BigDecimal waxAbove;

    @Column(name = "silver_clay_gemstones_above", nullable = false)
    private Integer silverClayGemstonesAbove;

    @Column(name = "published_by", nullable = false, length = 128)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
