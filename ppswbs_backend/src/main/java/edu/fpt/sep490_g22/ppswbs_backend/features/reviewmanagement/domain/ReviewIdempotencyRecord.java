package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "review_idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rir_key_scope", columnNames = {"idempotency_key", "actor_scope"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIdempotencyRecord {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "command_type", length = 32, nullable = false)
    private String commandType;

    @Column(name = "actor_scope", length = 128, nullable = false)
    private String actorScope;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "response_body", columnDefinition = "MEDIUMTEXT")
    private String responseBody;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}

