package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_text_interactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTextInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_scope", nullable = false, length = 16)
    private ActorScope actorScope;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "prompt_text", nullable = false, length = 2000)
    private String promptText;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private AiScope scope;

    @Column(name = "provider_correlation_id", length = 128)
    private String providerCorrelationId;

    @Column(name = "safe_failure_code", length = 64)
    private String safeFailureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusSeconds(30L * 24 * 3600); // 30-day retention
        }
    }
}
