package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "custom_order_idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomOrderIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_key", nullable = false, length = 128)
    private String commandKey;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor_scope", nullable = false, length = 32)
    private String actorScope;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Column(name = "response_reference", nullable = false, length = 128)
    private String responseReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
