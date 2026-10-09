package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Core booking aggregate for Workshop Booking feature.
 * <p>
 * Service states: PENDING_PAYMENT → CONFIRMED | EXPIRED | CANCELLED
 * Financial states: UNPAID → DEPOSIT_PAID (on successful payment)
 * <p>
 * Contact fields are snapshots at booking creation; they do not reflect later profile changes.
 * For Members booking for another person, submitted contact values are used, not Member defaults.
 * <p>
 * Package/price/currency fields are immutable snapshots from WorkshopPackage at booking time.
 */
@Entity
@Table(name = "workshop_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique booking identifier, format: WS-XXXXXXXX. */
    @Column(name = "booking_code", nullable = false, unique = true, length = 64)
    private String bookingCode;

    /** Null for Guest bookings. */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** Participant count reserved for this booking. */
    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    /** Contact email snapshot (submitted at booking time). */
    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    /** Normalized (lowercased, trimmed) contact email for lookup. */
    @Column(name = "canonical_email", nullable = false, length = 255)
    private String canonicalEmail;

    /** Contact phone snapshot (submitted at booking time). */
    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    /** Normalized contact phone for lookup. */
    @Column(name = "canonical_phone", nullable = false, length = 32)
    private String canonicalPhone;

    /** Optional design reference submitted by customer. */
    @Column(name = "design_reference", length = 2000)
    private String designReference;

    /** Package name snapshot. */
    @Column(name = "package_name_snapshot", nullable = false, length = 255)
    private String packageNameSnapshot;

    /** Package price snapshot in smallest currency unit. */
    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot;

    /** Currency snapshot (ISO 4217). */
    @Column(name = "currency_snapshot", nullable = false, length = 3)
    private String currencySnapshot;

    /** Service/lifecycle state. */
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 32)
    private BookingStatus bookingStatus;

    /** Financial payment state. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private BookingPaymentStatus paymentStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.bookingStatus == null) {
            this.bookingStatus = BookingStatus.PENDING_PAYMENT;
        }
        if (this.paymentStatus == null) {
            this.paymentStatus = BookingPaymentStatus.UNPAID;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * State-transition guard: confirm booking after valid deposit.
     * Only allowed from PENDING_PAYMENT + UNPAID.
     */
    public void confirm() {
        if (this.bookingStatus != BookingStatus.PENDING_PAYMENT
                || this.paymentStatus != BookingPaymentStatus.UNPAID) {
            throw new IllegalStateException(
                    "Cannot confirm booking in state: " + this.bookingStatus + "/" + this.paymentStatus);
        }
        this.bookingStatus = BookingStatus.CONFIRMED;
        this.paymentStatus = BookingPaymentStatus.DEPOSIT_PAID;
    }

    /**
     * State-transition guard: expire booking when hold deadline passes.
     * Only allowed from PENDING_PAYMENT + UNPAID.
     */
    public void expire() {
        if (this.bookingStatus != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "Cannot expire booking in state: " + this.bookingStatus);
        }
        this.bookingStatus = BookingStatus.EXPIRED;
    }

    /**
     * State-transition guard: cancel booking (explicit pre-payment hold release).
     * Only allowed from PENDING_PAYMENT + UNPAID.
     * No QR ticket is issued; no refund processing.
     */
    public void cancelPendingHold() {
        if (this.bookingStatus != BookingStatus.PENDING_PAYMENT
                || this.paymentStatus != BookingPaymentStatus.UNPAID) {
            throw new IllegalStateException(
                    "Cannot cancel booking: hold release only valid for PENDING_PAYMENT/UNPAID. Current: "
                            + this.bookingStatus + "/" + this.paymentStatus);
        }
        this.bookingStatus = BookingStatus.CANCELLED;
    }

    public boolean isPendingPayment() {
        return this.bookingStatus == BookingStatus.PENDING_PAYMENT;
    }

    public boolean isConfirmed() {
        return this.bookingStatus == BookingStatus.CONFIRMED;
    }

    public boolean isTerminal() {
        return this.bookingStatus == BookingStatus.EXPIRED
                || this.bookingStatus == BookingStatus.CANCELLED;
    }
}
