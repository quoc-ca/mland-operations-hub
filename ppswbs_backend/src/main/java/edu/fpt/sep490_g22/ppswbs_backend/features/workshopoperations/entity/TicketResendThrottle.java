package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "ticket_resend_throttles", uniqueConstraints = @UniqueConstraint(name = "uq_ticket_resend_throttle_booking", columnNames = "booking_reference"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketResendThrottle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "booking_reference", nullable = false, unique = true, length = 64)
    private String bookingReference;
    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;
    @Column(name = "request_count", nullable = false)
    private Integer requestCount;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
