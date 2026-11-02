package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "human_review_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HumanReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "design_request_id", nullable = false, length = 36)
    private String designRequestId;

    @Column(name = "reviewer_user_id", nullable = false, length = 128)
    private String reviewerUserId;

    @Column(name = "reviewer_role", nullable = false, length = 32)
    private String reviewerRole;

    @Column(name = "design_version", nullable = false)
    private Integer designVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    private ReviewDecision decision;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Column(name = "estimated_value_snapshot")
    private Long estimatedValueSnapshot;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @PrePersist
    protected void onCreate() {
        if (this.decidedAt == null) {
            this.decidedAt = Instant.now();
        }
    }
}
