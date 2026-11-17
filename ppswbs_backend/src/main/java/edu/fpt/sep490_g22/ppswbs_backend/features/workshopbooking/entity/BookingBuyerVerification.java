package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One OTP challenge proving that a Guest buyer, or Staff on the buyer's behalf, controls the group's original
 * contact email (003.1 FR-041, decision 2026-11-13). Only a salted hash of the OTP is stored.
 */
@Entity
@Table(name = "booking_buyer_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingBuyerVerification {

    public static final String PROOF_MISMATCH = "PROOF_MISMATCH";
    public static final String PENDING = "PENDING";
    public static final String VERIFIED = "VERIFIED";
    public static final String CONSUMED = "CONSUMED";
    public static final String LOCKED = "LOCKED";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verification_reference", nullable = false, length = 36, updatable = false)
    private String verificationReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "otp_hash", length = 64)
    private String otpHash;

    @Column(name = "otp_salt", length = 32)
    private String otpSalt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;

    @Column(name = "buyer_verification_reference", length = 36)
    private String buyerVerificationReference;

    @Column(name = "reference_expires_at")
    private Instant referenceExpiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
