package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "workshop_cancellation_outbox")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CancellationHandoffOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_reference", nullable = false, unique = true, length = 128)
    private String eventReference;
    @Column(name = "session_reference", nullable = false, length = 128)
    private String sessionReference;
    @Column(name = "affected_booking_references_json", nullable = false, columnDefinition = "json")
    private String affectedBookingReferencesJson;
    @Column(name = "cancellation_reason_reference", nullable = false, length = 1000)
    private String cancellationReasonReference;
    @Column(name = "operation_reference", nullable = false, length = 128)
    private String operationReference;
    @Column(name = "correlation_reference", nullable = false, length = 128)
    private String correlationReference;
    @Column(name = "cancellation_case_reference", nullable = false, length = 128)
    private String cancellationCaseReference;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
