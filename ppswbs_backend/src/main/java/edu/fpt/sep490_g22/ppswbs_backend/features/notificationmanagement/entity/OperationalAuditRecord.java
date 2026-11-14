package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "notification_operational_audit",
    indexes = @Index(name = "idx_noa_actor", columnList = "actor_account_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
public class OperationalAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_request_id", nullable = false)
    private NotificationRequest notificationRequest;

    @Column(name = "actor_account_id", nullable = false, length = 64)
    private String actorAccountId;

    @Column(name = "actor_role", nullable = false, length = 32)
    private String actorRole;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(name = "previous_state", nullable = false, length = 32)
    private String previousState;

    @Column(name = "new_state", nullable = false, length = 32)
    private String newState;

    @Column(name = "duplicate_risk_accepted", nullable = false)
    private boolean duplicateRiskAccepted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

