package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_notification_intents", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bni_reference", columnNames = {"notification_reference"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingNotificationIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @Column(name = "notification_reference", nullable = false, length = 64)
    private String notificationReference;

    @Column(name = "notification_kind", nullable = false, length = 64)
    private String notificationKind;

    @Column(name = "correlation_reference", nullable = false, length = 64)
    private String correlationReference;

    @Column(name = "delivery_snapshot", nullable = false, columnDefinition = "json")
    private String deliverySnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
