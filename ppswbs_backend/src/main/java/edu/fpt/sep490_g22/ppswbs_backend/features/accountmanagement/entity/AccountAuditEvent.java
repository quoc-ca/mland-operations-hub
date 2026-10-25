package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "account_audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "target_account_id")
    private Long targetAccountId;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "outcome", nullable = false)
    private String outcome; // SUCCESS, FAILURE

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;
}
