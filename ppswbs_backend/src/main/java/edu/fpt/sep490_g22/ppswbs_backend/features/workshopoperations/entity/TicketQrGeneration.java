package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A revoked QR of an attendance ticket (003.2 FR-033). The active QR lives on {@link AttendanceTicket}. */
@Entity
@Table(name = "ticket_qr_generations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketQrGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private AttendanceTicket ticket;

    @Column(name = "qr_generation", nullable = false)
    private Integer qrGeneration;

    @Column(name = "qr_reference", nullable = false, length = 64)
    private String qrReference;

    @Column(name = "revoke_reason", nullable = false, length = 32)
    private String revokeReason;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(name = "correlation_reference", length = 64)
    private String correlationReference;
}
