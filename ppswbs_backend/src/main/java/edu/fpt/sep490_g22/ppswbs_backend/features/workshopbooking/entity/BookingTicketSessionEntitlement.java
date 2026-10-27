package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_ticket_session_entitlements", uniqueConstraints = {
    @UniqueConstraint(name = "uq_btse_ticket_commitment", columnNames = {"booking_ticket_id", "session_commitment_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingTicketSessionEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_ticket_id", nullable = false)
    private BookingTicket bookingTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_commitment_id", nullable = false)
    private BookingSessionCommitment sessionCommitment;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
