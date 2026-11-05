package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "retail_checkout_quote_branches")
@Getter
@NoArgsConstructor
public class RetailCheckoutQuoteBranch {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quote_id", nullable = false) private RetailCheckoutQuote quote;
    @Column(name = "location_id", nullable = false) private Long locationId;
    @Column(name = "fulfilment_method", nullable = false, length = 16) private String fulfilmentMethod;
    @Column(name = "shipping_quote_id", length = 128) private String shippingQuoteId;
    @Column(name = "shipping_charge", nullable = false, precision = 19, scale = 0) private BigInteger shippingCharge;
    @Column(nullable = false, precision = 19, scale = 0) private BigInteger subtotal;
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<RetailCheckoutQuoteItem> items = new ArrayList<>();

    public RetailCheckoutQuoteBranch(RetailCheckoutQuote quote, Long locationId, String fulfilmentMethod,
                                     String shippingQuoteId, BigInteger shippingCharge, BigInteger subtotal) {
        this.id = UUID.randomUUID().toString(); this.quote = quote; this.locationId = locationId;
        this.fulfilmentMethod = fulfilmentMethod; this.shippingQuoteId = shippingQuoteId;
        this.shippingCharge = shippingCharge; this.subtotal = subtotal;
    }
    public void addItem(RetailCheckoutQuoteItem item) { items.add(item); }
}
