package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_folio_corrections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementFolioCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correction_reference", nullable = false, unique = true, length = 128)
    private String correctionReference;

    @Column(name = "case_reference", nullable = false, unique = true, length = 128)
    private String caseReference;

    @Column(name = "booking_reference", nullable = false, length = 128)
    private String bookingReference;

    @Column(name = "bill_reference", nullable = false, length = 128)
    private String billReference;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "currency_code", nullable = false, length = 3)
    @Builder.Default
    private String currencyCode = "VND";

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "manager_actor_reference", nullable = false, length = 128)
    private String managerActorReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

