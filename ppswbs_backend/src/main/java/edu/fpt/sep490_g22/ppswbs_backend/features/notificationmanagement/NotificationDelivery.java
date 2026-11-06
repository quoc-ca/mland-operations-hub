package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Outcome of one notification. Holds references and a status only: never the recipient, tickets or access link. */
@Entity
@Table(name = "notification_deliveries")
@Getter
@Setter
@NoArgsConstructor
class NotificationDelivery {

    static final String SENDING = "SENDING";
    static final String SENT = "SENT";
    static final String FAILED = "FAILED";
    static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_reference", nullable = false, length = 64)
    private String notificationReference;

    @Column(name = "booking_reference", nullable = false, length = 64)
    private String bookingReference;

    @Column(name = "notification_kind", nullable = false, length = 64)
    private String notificationKind;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
