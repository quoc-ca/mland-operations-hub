package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigInteger;
import java.util.UUID;

@Entity
@Table(name = "retail_checkout_quote_items")
@Getter
@NoArgsConstructor
public class RetailCheckoutQuoteItem {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quote_branch_id", nullable = false) private RetailCheckoutQuoteBranch branch;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "variant_id", nullable = false) private Long variantId;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0) private BigInteger unitPrice;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 0) private BigInteger lineTotal;

    public RetailCheckoutQuoteItem(RetailCheckoutQuoteBranch branch, Long productId, Long variantId, int quantity, BigInteger unitPrice) {
        this.id = UUID.randomUUID().toString(); this.branch = branch; this.productId = productId;
        this.variantId = variantId; this.quantity = quantity; this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigInteger.valueOf(quantity));
    }
}
