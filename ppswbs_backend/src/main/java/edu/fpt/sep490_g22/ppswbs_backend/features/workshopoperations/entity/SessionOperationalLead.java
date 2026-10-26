package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "session_operational_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionOperationalLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkshopSession session;

    @Column(name = "manager_personnel_id", nullable = false)
    private Long managerPersonnelId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "reason_note", length = 500)
    private String reasonNote;

    @Column(name = "evidence_reference", length = 255)
    private String evidenceReference;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
