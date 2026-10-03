package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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

    @Column(name = "booking_code", nullable = false, unique = true, length = 64)
    private String bookingCode;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "canonical_email", nullable = false, length = 255)
    private String canonicalEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_state", nullable = false, length = 64)
    private ConfirmationState confirmationState;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 64)
    private BookingStatus bookingStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.confirmationState == null) {
            this.confirmationState = ConfirmationState.EMAIL_CONFIRMATION_PENDING;
        }
        if (this.bookingStatus == null) {
            this.bookingStatus = BookingStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
