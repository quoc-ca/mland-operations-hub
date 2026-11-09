package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "notification_request",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_nr_idempotency",
            columnNames = {"business_event_reference", "template_code", "recipient_role", "recipient_address_fingerprint", "original_request_id"}
        )
    },
    indexes = {
        @Index(name = "idx_nr_worker_poll", columnList = "delivery_state, next_attempt_at, id"),
        @Index(name = "idx_nr_retention", columnList = "terminal_at, delivery_state")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_event_reference", nullable = false, length = 128)
    private String businessEventReference;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_version_id", nullable = false)
    private TemplateVersion templateVersion;

    @Column(name = "recipient_role", nullable = false, length = 32)
    private String recipientRole = "CUSTOMER";

    @Column(name = "recipient_address_fingerprint", nullable = false, length = 64)
    private String recipientAddressFingerprint;

    @Column(name = "recipient_address_masked", nullable = false, length = 128)
    private String recipientAddressMasked;

    @Column(name = "recipient_snapshot", nullable = false, columnDefinition = "TEXT")
    private String recipientSnapshot;

    @Column(name = "business_data_snapshot", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String businessDataSnapshot;

    @Column(name = "artifact_reference", length = 255)
    private String artifactReference;

    @Column(name = "credential_reference", length = 255)
    private String credentialReference;

    @Column(name = "credential_expires_at")
    private Instant credentialExpiresAt;

    @Column(name = "delivery_state", nullable = false, length = 32)
    private String deliveryState = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "smtp_invocation_started_at")
    private Instant smtpInvocationStartedAt;

    @Column(name = "terminal_at")
    private Instant terminalAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_request_id")
    private NotificationRequest originalRequest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

