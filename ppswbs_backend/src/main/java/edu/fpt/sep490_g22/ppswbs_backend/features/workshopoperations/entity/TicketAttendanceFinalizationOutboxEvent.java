package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "workshop_ticket_attendance_finalization_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_attendance_outbox_ref_type",
                columnNames = {"ticket_reference", "event_type"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketAttendanceFinalizationOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "ticket_reference", nullable = false, length = 128)
    private String ticketReference;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "window_closed_at", nullable = false)
    private Instant windowClosedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}

