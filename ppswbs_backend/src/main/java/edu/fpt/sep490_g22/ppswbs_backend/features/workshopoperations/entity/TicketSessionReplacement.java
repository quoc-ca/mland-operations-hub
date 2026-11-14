package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One moved (ticket, source -> target) pair of a per-ticket session replacement (003.2 FR-034). */
@Entity
@Table(name = "ticket_session_replacements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketSessionReplacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private AttendanceTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_session_id", nullable = false)
    private WorkshopSession sourceSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_session_id", nullable = false)
    private WorkshopSession targetSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "replacement_type", nullable = false, length = 32)
    private TicketSessionReplacementType replacementType;

    @Column(name = "quota_consumed", nullable = false)
    private boolean quotaConsumed;

    @Column(name = "operation_reference", nullable = false, length = 128)
    private String operationReference;

    @Column(name = "correlation_reference", length = 64)
    private String correlationReference;

    @Column(name = "replaced_at", nullable = false)
    private Instant replacedAt;
}
