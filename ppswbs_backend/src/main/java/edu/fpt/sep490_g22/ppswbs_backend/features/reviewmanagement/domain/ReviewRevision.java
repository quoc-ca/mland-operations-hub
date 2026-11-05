package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "review_revisions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_review_revisions_rev", columnNames = {"review_id", "revision_number"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRevision {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "review_id", length = 36, nullable = false)
    private String reviewId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by_type", length = 16, nullable = false)
    private OwnerType changedByType;

    @Column(name = "changed_by_reference", length = 128, nullable = false)
    private String changedByReference;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @PrePersist
    public void prePersist() {
        if (changedAt == null) changedAt = Instant.now();
    }
}

