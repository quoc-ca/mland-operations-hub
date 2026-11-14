package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One bank refund of Mland money for one ticket (003.3 FR-038..FR-040). Nothing here touches a gateway: the
 * transfer happens outside the system and this row records, tracks and reconciles it.
 */
@Entity
@Table(name = "settlement_refund_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_reference", nullable = false, unique = true, length = 128, updatable = false)
    private String requestReference;

    /** The ticket code of a group ticket, or the booking code of a single direct booking. */
    @Column(name = "booking_reference", nullable = false, length = 128, updatable = false)
    private String bookingReference;

    @Column(name = "group_reference", length = 128, updatable = false)
    private String groupReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 48, updatable = false)
    private RefundReason reason;

    /** What this refund is for; with the reason it keeps the same money from being asked back twice. */
    @Column(name = "source_reference", nullable = false, length = 128, updatable = false)
    private String sourceReference;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RefundRequestStatus status;

    @Column(name = "recipient_bank_code", length = 32)
    private String recipientBankCode;

    /** AES-GCM text; read only for the approving Manager and the transferring actor, then cleared. */
    @Column(name = "recipient_account_encrypted", length = 512)
    private String recipientAccountEncrypted;

    @Column(name = "recipient_account_masked", length = 16)
    private String recipientAccountMasked;

    @Column(name = "recipient_holder_name", length = 200)
    private String recipientHolderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_entry_channel", length = 16)
    private RefundAccountEntryChannel accountEntryChannel;

    @Column(name = "account_entered_by_account_id")
    private Long accountEnteredByAccountId;

    @Column(name = "account_entered_at")
    private Instant accountEnteredAt;

    @Column(name = "approved_by_account_id")
    private Long approvedByAccountId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_reason", length = 1000)
    private String approvalReason;

    @Column(name = "transferred_by_account_id")
    private Long transferredByAccountId;

    @Column(name = "transferred_at")
    private Instant transferredAt;

    @Column(name = "bank_transfer_reference", length = 128)
    private String bankTransferReference;

    @Column(name = "transfer_proof_reference", length = 255)
    private String transferProofReference;

    @Column(name = "confirmed_by_account_id")
    private Long confirmedByAccountId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "closed_by_account_id")
    private Long closedByAccountId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_reason", length = 1000)
    private String closureReason;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reminder_day3_at")
    private Instant reminderDay3At;

    @Column(name = "reminder_day7_at")
    private Instant reminderDay7At;

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
        updatedAt = now;
        if (status == null) status = RefundRequestStatus.PENDING_ACCOUNT;
        if (currencyCode == null) currencyCode = "VND";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Money still owed to the customer: anything that has not been confirmed back to them. */
    public boolean isOutstanding() {
        return status != RefundRequestStatus.REFUNDED;
    }
}
