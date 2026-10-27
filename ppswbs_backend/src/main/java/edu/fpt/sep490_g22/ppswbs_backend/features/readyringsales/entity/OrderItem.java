package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "retail_order_items")
@Getter
@NoArgsConstructor
public class OrderItem {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "retail_order_id", nullable = false) private RetailOrder retailOrder;
    @Column(name = "variant_id", nullable = false, length = 128) private String variantId;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2) private BigDecimal unitPrice;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2) private BigDecimal lineTotal;
    public OrderItem(RetailOrder retailOrder, String variantId, int quantity, BigDecimal unitPrice) {
        this.retailOrder = retailOrder; this.variantId = variantId; this.quantity = quantity; this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
}
