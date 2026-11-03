package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_invoices", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bi_booking_id", columnNames = {"booking_id"}),
    @UniqueConstraint(name = "uq_bi_invoice_number", columnNames = {"invoice_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "price_per_participant", nullable = false)
    private Long pricePerParticipant;

    @Column(name = "package_total", nullable = false)
    private Long packageTotal;

    @Column(name = "post_promotion_amount", nullable = false)
    private Long postPromotionAmount;

    @Column(name = "deposit_amount", nullable = false)
    private Long depositAmount;

    @Column(name = "remaining_amount", nullable = false)
    private Long remainingAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (currency == null) {
            currency = "VND";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
