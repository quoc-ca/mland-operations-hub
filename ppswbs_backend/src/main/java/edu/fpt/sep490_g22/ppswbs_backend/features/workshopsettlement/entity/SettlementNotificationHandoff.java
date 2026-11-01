package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_notification_handoffs", uniqueConstraints = {
        @UniqueConstraint(name = "uq_settlement_notification_reference", columnNames = "notification_reference")
}, indexes = {
        @Index(name = "ix_settlement_notification_payload_expiry", columnList = "payload_expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementNotificationHandoff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_reference", nullable = false, updatable = false, length = 128)
    private String notificationReference;

    @Column(name = "settlement_reference", nullable = false, updatable = false, length = 128)
    private String settlementReference;

    @Column(name = "notification_kind", nullable = false, updatable = false, length = 64)
    private String notificationKind;

    @Column(name = "correlation_reference", nullable = false, updatable = false, length = 128)
    private String correlationReference;

    @Column(name = "stored_recipient", nullable = false, updatable = false, length = 320)
    private String storedRecipient;

    @Column(name = "rendered_settlement_content", nullable = false, updatable = false, columnDefinition = "text")
    private String renderedSettlementContent;

    @Column(name = "payload_expires_at", nullable = false, updatable = false)
    private Instant payloadExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;
}
