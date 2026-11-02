package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "retail_checkout_quotes")
@Getter
@NoArgsConstructor
public class RetailCheckoutQuote {
    @Id @Column(length = 36) private String id;
    @Column(name = "member_account_id", nullable = false) private Long memberAccountId;
    @Column(name = "cart_id", nullable = false, length = 36) private String cartId;
    @Column(name = "cart_version", nullable = false) private long cartVersion;
    @Column(name = "selection_choice", nullable = false, length = 16) private String selectionChoice;
    @Column(name = "voucher_code", length = 128) private String voucherCode;
    @Column(name = "requested_points", nullable = false, precision = 19, scale = 0) private BigInteger requestedPoints;
    @Column(name = "terms_digest", nullable = false, length = 128) private String termsDigest;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "merchandise_subtotal", nullable = false, precision = 19, scale = 0) private BigInteger merchandiseSubtotal;
    @Column(name = "shipping_total", nullable = false, precision = 19, scale = 0) private BigInteger shippingTotal;
    @Column(name = "program_discount", nullable = false, precision = 19, scale = 0) private BigInteger programDiscount;
    @Column(name = "points_discount", nullable = false, precision = 19, scale = 0) private BigInteger pointsDiscount;
    @Column(name = "payable_total", nullable = false, precision = 19, scale = 0) private BigInteger payableTotal;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "accepted_checkout_id", length = 36) private String acceptedCheckoutId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Version @Column(nullable = false) private Long version;
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<RetailCheckoutQuoteBranch> branches = new ArrayList<>();

    public RetailCheckoutQuote(Long memberAccountId, String cartId, long cartVersion, String selectionChoice,
                               String voucherCode, BigInteger requestedPoints, String termsDigest, String currency,
                               BigInteger merchandiseSubtotal, BigInteger shippingTotal, BigInteger programDiscount,
                               BigInteger pointsDiscount, BigInteger payableTotal, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID().toString(); this.memberAccountId = memberAccountId; this.cartId = cartId;
        this.cartVersion = cartVersion; this.selectionChoice = selectionChoice; this.voucherCode = voucherCode;
        this.requestedPoints = requestedPoints; this.termsDigest = termsDigest; this.currency = currency;
        this.merchandiseSubtotal = merchandiseSubtotal; this.shippingTotal = shippingTotal;
        this.programDiscount = programDiscount; this.pointsDiscount = pointsDiscount; this.payableTotal = payableTotal;
        this.createdAt = createdAt; this.expiresAt = expiresAt; this.status = "OPEN";
    }

    public void addBranch(RetailCheckoutQuoteBranch branch) { branches.add(branch); }
    /** Called after the service has locked and revalidated this quote in the acceptance transaction. */
    public void accept(String checkoutId, Instant acceptedAt) {
        if (!"OPEN".equals(status) || checkoutId == null || checkoutId.isBlank()
            || acceptedAt == null || !acceptedAt.isBefore(expiresAt)) {
            throw new IllegalStateException("Checkout quote cannot be accepted in its current state");
        }
        this.acceptedCheckoutId = checkoutId;
        this.status = "ACCEPTED";
    }
}
