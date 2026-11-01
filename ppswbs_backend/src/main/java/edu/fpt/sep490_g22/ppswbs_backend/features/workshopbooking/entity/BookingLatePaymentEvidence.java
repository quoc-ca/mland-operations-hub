package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "booking_late_payment_evidence", uniqueConstraints = {
        @UniqueConstraint(name = "uq_blpe_merchant_reference", columnNames = "merchant_reference"),
        @UniqueConstraint(name = "uq_blpe_evidence_reference", columnNames = "evidence_reference")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookingLatePaymentEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @Column(name = "booking_code", nullable = false, length = 32)
    private String bookingCode;

    @Column(name = "merchant_reference", nullable = false, length = 128)
    private String merchantReference;

    @Column(name = "evidence_reference", nullable = false, length = 128)
    private String evidenceReference;

    @Column(name = "provider_transaction_reference", length = 64)
    private String providerTransactionReference;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    @PrePersist void onCreate() { if (verifiedAt == null) verifiedAt = Instant.now(); }
}
