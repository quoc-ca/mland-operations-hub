package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_cancellation_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementCancellationCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_reference", nullable = false, unique = true, length = 128)
    private String caseReference;

    @Column(name = "session_reference", nullable = false, length = 128)
    private String sessionReference;

    @Column(name = "affected_booking_references", nullable = false, columnDefinition = "json")
    private String affectedBookingReferencesJson;

    @Column(name = "cancellation_reason_reference", nullable = false, length = 128)
    private String cancellationReasonReference;

    @Column(name = "operation_reference", nullable = false, unique = true, length = 128)
    private String operationReference;

    @Column(name = "correlation_reference", nullable = false, length = 128)
    private String correlationReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private SettlementCaseStatus status = SettlementCaseStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
