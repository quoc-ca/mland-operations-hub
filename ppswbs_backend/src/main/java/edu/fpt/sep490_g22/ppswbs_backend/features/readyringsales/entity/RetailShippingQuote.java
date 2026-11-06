package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable server-issued fee evidence; no carrier shipment is created. */
@Entity
@Table(name = "retail_shipping_quotes")
@Getter
@NoArgsConstructor
public class RetailShippingQuote {
    @Id @Column(length = 36) private String id;
    @Column(name = "member_account_id", nullable = false) private Long memberAccountId;
    @Column(name = "cart_id", nullable = false, length = 36) private String cartId;
    @Column(name = "cart_version", nullable = false) private long cartVersion;
    @Column(name = "location_id", nullable = false) private Long locationId;
    @Column(name = "binding_digest", nullable = false, length = 64) private String bindingDigest;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal charge;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    public RetailShippingQuote(Long member, String cart, long version, Long location, String digest,
                               BigDecimal charge, Instant created, Instant expires) {
        this.id = UUID.randomUUID().toString(); this.memberAccountId = member; this.cartId = cart;
        this.cartVersion = version; this.locationId = location; this.bindingDigest = digest;
        this.charge = charge; this.createdAt = created; this.expiresAt = expires;
    }
}
