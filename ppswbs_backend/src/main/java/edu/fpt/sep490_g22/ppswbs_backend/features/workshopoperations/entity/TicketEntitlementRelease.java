package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A future seat a cancelled ticket gave back (003.2 FR-031); the entitlement row itself is removed. */
@Entity
@Table(name = "ticket_entitlement_releases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketEntitlementRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private AttendanceTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkshopSession session;

    @Column(name = "cancellation_case_reference", nullable = false, length = 128)
    private String cancellationCaseReference;

    @Column(name = "operation_reference", nullable = false, length = 128)
    private String operationReference;

    @Column(name = "correlation_reference", length = 64)
    private String correlationReference;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;
}
