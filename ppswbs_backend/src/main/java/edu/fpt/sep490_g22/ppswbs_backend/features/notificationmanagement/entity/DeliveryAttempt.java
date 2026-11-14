package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "notification_delivery_attempt",
    indexes = @Index(name = "idx_nda_request_attempt", columnList = "notification_request_id, attempt_number")
)
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_request_id", nullable = false)
    private NotificationRequest notificationRequest;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "outcome_category", nullable = false, length = 32)
    private String outcomeCategory;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "safe_error_summary", length = 512)
    private String safeErrorSummary;
}

