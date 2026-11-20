package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "ai_prompt_quota_usage", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ai_quota", columnNames = {"quota_scope", "quota_key_digest", "quota_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPromptQuotaUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quota_scope", nullable = false, length = 16)
    private String quotaScope;

    @Column(name = "quota_key_digest", nullable = false, length = 128)
    private String quotaKeyDigest;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Column(name = "limit_count", nullable = false)
    private int limitCount;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
