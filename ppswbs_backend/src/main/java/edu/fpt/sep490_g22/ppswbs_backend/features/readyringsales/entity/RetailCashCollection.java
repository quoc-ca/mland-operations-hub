package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One confirmed counter cash payment of a store-pickup branch order (FR-08-045, FR-08-047).
 * Immutable once written: one row per branch order, keyed for Staff command idempotency.
 */
@Entity
@Table(name = "retail_cash_collections")
@Getter
@NoArgsConstructor
public class RetailCashCollection {
    @Id @Column(length = 36) private String id;
    @Column(name = "grouped_checkout_id", nullable = false, length = 36) private String groupedCheckoutId;
    @Column(name = "retail_order_id", nullable = false, length = 36) private String retailOrderId;
    @Column(name = "location_id", nullable = false) private Long locationId;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Column(name = "applied_amount", nullable = false, precision = 19, scale = 2) private BigDecimal appliedAmount;
    @Column(name = "amount_received", nullable = false, precision = 19, scale = 2) private BigDecimal amountReceived;
    @Column(name = "change_returned", nullable = false, precision = 19, scale = 2) private BigDecimal changeReturned;
    @Column(name = "confirmed_by_account_id", nullable = false) private Long confirmedByAccountId;
    @Column(name = "confirmed_at", nullable = false) private Instant confirmedAt;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;

    public RetailCashCollection(String groupedCheckoutId, String retailOrderId, Long locationId, LocalDate businessDate,
                                BigDecimal appliedAmount, BigDecimal amountReceived, Long confirmedByAccountId,
                                Instant confirmedAt, String idempotencyKey) {
        if (appliedAmount == null || appliedAmount.signum() <= 0 || amountReceived == null
            || amountReceived.compareTo(appliedAmount) < 0) {
            throw new IllegalArgumentException("Cash received must cover the positive payable amount");
        }
        this.id = UUID.randomUUID().toString();
        this.groupedCheckoutId = groupedCheckoutId;
        this.retailOrderId = retailOrderId;
        this.locationId = locationId;
        this.businessDate = businessDate;
        this.appliedAmount = appliedAmount;
        this.amountReceived = amountReceived;
        this.changeReturned = amountReceived.subtract(appliedAmount);
        this.confirmedByAccountId = confirmedByAccountId;
        this.confirmedAt = confirmedAt;
        this.idempotencyKey = idempotencyKey;
    }
}
