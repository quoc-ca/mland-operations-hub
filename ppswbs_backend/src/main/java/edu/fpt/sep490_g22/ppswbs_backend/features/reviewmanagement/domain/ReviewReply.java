package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "review_replies", uniqueConstraints = {
        @UniqueConstraint(name = "uq_review_replies_review", columnNames = {"review_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReply {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "review_id", length = 36, nullable = false)
    private String reviewId;

    @Column(name = "body_text", columnDefinition = "TEXT", nullable = false)
    private String bodyText;

    @Column(name = "created_by_manager_id", nullable = false)
    private Long createdByManagerId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

