package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** One collection against one folio (003.3 FR-031..FR-034). Several may exist, in mixed methods. */
@Entity
@Table(name = "settlement_folio_collections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementFolioCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_reference", nullable = false, unique = true, length = 128, updatable = false)
    private String collectionReference;

    /** The folio this collection belongs to, keyed like {@code SettlementCharge} does. */
    @Column(name = "bill_reference", nullable = false, length = 128, updatable = false)
    private String billReference;

    /** The ticket code of a group ticket, or the booking code of a single direct booking. */
    @Column(name = "booking_reference", nullable = false, length = 128, updatable = false)
    private String bookingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 16, updatable = false)
    private FolioCollectionMethod method;

    /** What is applied to the folio. For cash this is not what the customer handed over. */
    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "cash_received_vnd", updatable = false)
    private Long cashReceivedVnd;

    @Column(name = "cash_change_vnd", updatable = false)
    private Long cashChangeVnd;

    /** How much of this collection exceeded the balance; it is kept in full and owed back (FR-034). */
    @Column(name = "excess_vnd", nullable = false)
    private long excessVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private FolioCollectionStatus status;

    /** The VNPay merchant reference, the customer's bank transaction reference, or the cash receipt number. */
    @Column(name = "external_reference", length = 128)
    private String externalReference;

    @Column(name = "payer_label", length = 200)
    private String payerLabel;

    @Column(name = "branch_id")
    private Long branchId;

    /** Vietnam working day this cash belongs to; only cash carries one (FR-036). */
    @Column(name = "cash_business_day")
    private LocalDate cashBusinessDay;

    @Column(name = "recorded_by_account_id")
    private Long recordedByAccountId;

    @Column(name = "confirmed_by_account_id")
    private Long confirmedByAccountId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** VNPay checkout URL of a pending VNPAY part; cleared once the part is confirmed or failed (T108). */
    @Column(name = "payment_url_snapshot", length = 2048)
    private String paymentUrlSnapshot;

    /** When the VNPay checkout URL stops accepting payment (T108). */
    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    /** Immutable VNPay {@code vnp_CreateDate} of the checkout, used by QueryDR (T109). */
    @Column(name = "provider_create_date", length = 14, updatable = false)
    private String providerCreateDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = FolioCollectionStatus.PENDING_VERIFICATION;
        if (currencyCode == null) currencyCode = "VND";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isPending() {
        return status == FolioCollectionStatus.PENDING_VERIFICATION;
    }
}
