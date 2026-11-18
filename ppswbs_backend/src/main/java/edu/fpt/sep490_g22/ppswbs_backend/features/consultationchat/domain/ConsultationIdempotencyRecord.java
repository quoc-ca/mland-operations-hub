package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "consultation_idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cir_key_scope", columnNames = {"idempotency_key", "actor_scope"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationIdempotencyRecord {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "actor_scope", length = 128, nullable = false)
    private String actorScope;

    @Column(name = "command_type", length = 32, nullable = false)
    private String commandType;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "response_body", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String responseBody;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
