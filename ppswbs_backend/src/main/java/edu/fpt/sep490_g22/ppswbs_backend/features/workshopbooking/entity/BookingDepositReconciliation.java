package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Durable post-expiry payment resolution, without gateway payloads or credentials. */
@Entity
@Table(name = "booking_deposit_reconciliations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookingDepositReconciliation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attempt_id", nullable = false) private BookingPaymentAttempt attempt;
    @Column(name = "lifecycle_status", nullable = false, length = 32) private String lifecycleStatus;
    @Column(name = "next_query_at") private Instant nextQueryAt;
    @Column(name = "last_query_at") private Instant lastQueryAt;
    @Column(name = "verified_evidence_reference", length = 128) private String verifiedEvidenceReference;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
