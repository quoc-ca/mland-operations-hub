package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;

/**
 * A quote of a whole cart (003.1 FR-030). It holds no capacity: the booking group is created from it and
 * re-reads prices and availability first. Benefit columns stay 0 until the 006 cart contract lands.
 */
@Entity
@Table(name = "workshop_cart_quotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopCartQuote {

    public static final Duration QUOTE_TTL = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_ref", nullable = false, length = 36, updatable = false)
    private String quoteRef;

    /** Null for a Guest cart; Guests have no FE-06 benefit, so a Guest quote is priced at package price. */
    @Column(name = "buyer_member_account_id", updatable = false)
    private Long buyerMemberAccountId;

    @Column(name = "lines_json", nullable = false, columnDefinition = "JSON", updatable = false)
    private String linesJson;

    @Column(name = "gross_vnd", nullable = false, updatable = false)
    private long grossVnd;

    @Column(name = "voucher_discount_vnd", nullable = false, updatable = false)
    private long voucherDiscountVnd;

    @Column(name = "points_used", nullable = false, updatable = false)
    private long pointsUsed;

    @Column(name = "point_discount_vnd", nullable = false, updatable = false)
    private long pointDiscountVnd;

    @Column(name = "final_total_vnd", nullable = false, updatable = false)
    private long finalTotalVnd;

    @Column(name = "group_collection_vnd", nullable = false, updatable = false)
    private long groupCollectionVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 32, updatable = false)
    private GroupPaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "quote_state", nullable = false, length = 16)
    private CartQuoteState quoteState;

    @Column(name = "accepted_group_code", length = 32)
    private String acceptedGroupCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (expiresAt == null) expiresAt = createdAt.plus(QUOTE_TTL);
        if (quoteState == null) quoteState = CartQuoteState.OPEN;
    }
}
