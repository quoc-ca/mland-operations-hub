package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    /** Voucher plus points share of this branch order; null for quotes made before V124. */
    @Column(name = "discount_amount", precision = 19, scale = 0) private BigInteger discountAmount;
    @Column(name = "recipient_name", length = 255) private String recipientName;
    @Column(name = "recipient_phone", length = 32) private String recipientPhone;
    @Column(name = "recipient_address", length = 512) private String recipientAddress;
    @Column(name = "recipient_province", length = 128) private String recipientProvince;
    @Column(name = "recipient_district", length = 128) private String recipientDistrict;
    @Column(name = "recipient_ward", length = 128) private String recipientWard;
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<RetailCheckoutQuoteItem> items = new ArrayList<>();

    public RetailCheckoutQuoteBranch(RetailCheckoutQuote quote, Long locationId, String fulfilmentMethod,
                                     String shippingQuoteId, BigInteger shippingCharge, BigInteger subtotal) {
        this.id = UUID.randomUUID().toString(); this.quote = quote; this.locationId = locationId;
        this.fulfilmentMethod = fulfilmentMethod; this.shippingQuoteId = shippingQuoteId;
        this.shippingCharge = shippingCharge; this.subtotal = subtotal;
    }
    public void setRecipientSnapshot(String name, String phone, String address, String province, String district, String ward) {
        this.recipientName = name; this.recipientPhone = phone; this.recipientAddress = address;
        this.recipientProvince = province; this.recipientDistrict = district; this.recipientWard = ward;
    }
    public boolean matchesRecipient(edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos.RecipientAddressInput input) {
        if ("PICKUP".equals(fulfilmentMethod)) return input == null;
        return input != null && Objects.equals(recipientName, input.recipientName())
            && Objects.equals(recipientPhone, input.recipientPhone())
            && Objects.equals(recipientAddress, input.address())
            && Objects.equals(recipientProvince, input.province())
            && Objects.equals(recipientDistrict, input.district())
            && Objects.equals(recipientWard, input.ward());
    }
    public void addItem(RetailCheckoutQuoteItem item) { items.add(item); }
    public void allocateDiscount(BigInteger amount) { this.discountAmount = amount; }
}
