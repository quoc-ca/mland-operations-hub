package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ring_difficulty_option_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingDifficultyOptionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_kind", nullable = false, length = 32)
    private OptionScoreKind optionKind;

    @Column(name = "option_reference", nullable = false, length = 128)
    private String optionReference;

    @Column(name = "score", nullable = false, precision = 18, scale = 4)
    private BigDecimal score;
}
