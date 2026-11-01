package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "workshop_bookings", uniqueConstraints = {
    @UniqueConstraint(name = "uq_wb_booking_code", columnNames = {"booking_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 32)
    private String bookingCode;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "package_name_snapshot", nullable = false, length = 255)
    private String packageNameSnapshot;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Column(name = "member_account_id")
    private Long memberAccountId;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 32)
    private BookingStatus bookingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
        if (bookingStatus == null) {
            bookingStatus = BookingStatus.PENDING_PAYMENT;
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
