package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Invoice snapshot frozen at booking creation.
 * Immutable after creation: amount, deposit amount, deposit percent and currency must not change.
 */
@Entity
@Table(name = "workshop_invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoice_booking", columnNames = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    /** Package price snapshot in smallest currency unit. */
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    /** Deposit percent snapshot (e.g. 50). */
    @Column(name = "deposit_percent", nullable = false)
    private Integer depositPercent;

    /** Deposit amount snapshot = totalAmount * depositPercent / 100. */
    @Column(name = "deposit_amount", nullable = false)
    private Long depositAmount;

    /** ISO 4217 currency code snapshot. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
