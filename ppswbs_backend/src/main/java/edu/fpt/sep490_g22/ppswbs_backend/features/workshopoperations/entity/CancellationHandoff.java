package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cancellation_handoffs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationHandoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkshopSession session;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "reason_note", length = 500)
    private String reasonNote;

    @Column(name = "evidence_reference", length = 255)
    private String evidenceReference;

    @Column(name = "cancellation_case_reference", length = 128)
    private String cancellationCaseReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private CancellationHandoffStatus status = CancellationHandoffStatus.REQUESTED;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
