package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One customer cart confirmation: the shared contact, the single booking-time payment and the voucher use. */
@Entity
@Table(name = "booking_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_code", nullable = false, length = 32, updatable = false)
    private String groupCode;

    @Column(name = "buyer_member_account_id")
    private Long buyerMemberAccountId;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_status", nullable = false, length = 32)
    private BookingStatus groupStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 32)
    private GroupPaymentMode paymentMode;

    @Column(name = "group_collection_vnd", nullable = false)
    private long groupCollectionVnd;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "payment_deadline_at")
    private Instant paymentDeadlineAt;

    @Column(name = "voucher_use_reference", length = 128)
    private String voucherUseReference;

    @Column(name = "loyalty_policy_revision")
    private Long loyaltyPolicyRevision;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

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
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (groupStatus == null) groupStatus = BookingStatus.PENDING_PAYMENT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
