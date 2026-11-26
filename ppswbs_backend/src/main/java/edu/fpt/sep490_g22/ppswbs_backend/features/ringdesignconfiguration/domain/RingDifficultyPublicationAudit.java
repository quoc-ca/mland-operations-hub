package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ring_difficulty_publication_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingDifficultyPublicationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_reference", nullable = false, length = 128)
    private String actorReference;

    @Column(name = "previous_policy_version_id")
    private Long previousPolicyVersionId;

    @Column(name = "new_policy_version_id", nullable = false)
    private Long newPolicyVersionId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "old_values_snapshot", columnDefinition = "JSON")
    private String oldValuesSnapshot;

    @Column(name = "new_values_snapshot", nullable = false, columnDefinition = "JSON")
    private String newValuesSnapshot;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
