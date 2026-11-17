package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "notification_operational_failure",
    indexes = @Index(name = "idx_nof_event", columnList = "business_event_reference, template_code")
)
@Getter
@Setter
@NoArgsConstructor
public class OperationalFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_event_reference", nullable = false, length = 128)
    private String businessEventReference;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "recipient_fingerprint", length = 64)
    private String recipientFingerprint;

    @Column(name = "failure_code", nullable = false, length = 64)
    private String failureCode;

    @Column(name = "safe_reason", nullable = false, length = 512)
    private String safeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

