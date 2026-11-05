package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "review_moderation_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewModerationEvent {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "review_id", length = 36, nullable = false)
    private String reviewId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_role", length = 32, nullable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 32, nullable = false)
    private ModerationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 16)
    private ReviewStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 16)
    private ReviewStatus newStatus;

    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

