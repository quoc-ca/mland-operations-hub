package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "workshop_booking_attendance_finalization_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uq_workshop_booking_attendance_finalization", columnNames = {"booking_reference", "event_type"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingAttendanceFinalizationOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "booking_reference", nullable = false, length = 128) private String bookingReference;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(name = "finalized_at", nullable = false) private Instant finalizedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
}
