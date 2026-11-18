package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_guest_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiGuestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_digest", nullable = false, unique = true, length = 128)
    private String sessionDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.lastSeenAt == null) {
            this.lastSeenAt = this.createdAt;
        }
        if (this.expiresAt == null) {
            this.expiresAt = this.createdAt.plusSeconds(24 * 3600); // 24-hour TTL
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }

    public boolean isValid() {
        return "ACTIVE".equalsIgnoreCase(this.status) && Instant.now().isBefore(this.expiresAt);
    }
}
