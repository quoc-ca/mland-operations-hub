package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "guest_design_request_quota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestDesignRequestQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_digest", nullable = false, length = 128)
    private String sessionDigest;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private int requestCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
