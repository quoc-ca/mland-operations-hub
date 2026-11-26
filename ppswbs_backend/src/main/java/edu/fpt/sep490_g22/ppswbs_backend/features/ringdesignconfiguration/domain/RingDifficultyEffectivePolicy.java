package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ring_difficulty_effective_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingDifficultyEffectivePolicy {

    public static final String DEFAULT_SCOPE = "WORKSHOP_RING_DIFFICULTY";

    @Id
    @Column(name = "policy_scope", nullable = false, length = 64)
    private String policyScope;

    @Column(name = "policy_version_id", nullable = false)
    private Long policyVersionId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
