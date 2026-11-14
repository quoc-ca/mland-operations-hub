package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Protected delivery snapshot of one booking-group email. Only {@code notificationReference} leaves Booking in the
 * handoff event; Feature 007 pulls the snapshot through the notification facade.
 */
@Entity
@Table(name = "booking_group_notification_intents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGroupNotificationIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_reference", nullable = false, length = 64, updatable = false)
    private String notificationReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", updatable = false)
    private TicketBooking ticket;

    @Column(name = "notification_kind", nullable = false, length = 64, updatable = false)
    private String notificationKind;

    @Column(name = "correlation_reference", nullable = false, length = 64, updatable = false)
    private String correlationReference;

    @Column(name = "delivery_snapshot", columnDefinition = "json")
    private String deliverySnapshot;

    /** When set, the snapshot is no longer served after this time and is scrubbed. */
    @Column(name = "snapshot_expires_at")
    private Instant snapshotExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
