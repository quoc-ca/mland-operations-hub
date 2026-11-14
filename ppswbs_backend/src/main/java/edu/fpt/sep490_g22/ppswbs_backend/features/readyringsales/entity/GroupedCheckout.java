package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "retail_grouped_checkouts")
@Getter
@NoArgsConstructor
public class GroupedCheckout {

    @Id @Column(length = 36) private String id;
    @Column(name = "member_account_id", nullable = false) private Long memberAccountId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "cart_version", nullable = false) private long cartVersion;
    @Column(nullable = false, length = 3) private String currency = "VND";
    @Column(name = "merchandise_subtotal", nullable = false, precision = 19, scale = 2) private BigDecimal merchandiseSubtotal;
    @Column(name = "shipping_charge", nullable = false, precision = 19, scale = 2) private BigDecimal shippingCharge;
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2) private BigDecimal discountAmount;
    @Column(name = "payable_amount", nullable = false, precision = 19, scale = 2) private BigDecimal payableAmount;
    @Enumerated(EnumType.STRING) @Column(name = "payment_state", nullable = false, length = 32) private PaymentState paymentState;
    @Column(name = "payment_initiated_at") private Instant paymentInitiatedAt;
    @Column(name = "cancellation_unrestored_quantity") private Integer cancellationUnrestoredQuantity;
    @Column(name = "payment_proof_ref", length = 128) private String paymentProofRef;
    /** VNPAY or CASH_AT_COUNTER (FR-08-041); see {@link RetailPaymentMethod}. */
    @Column(name = "payment_method", nullable = false, length = 24) private String paymentMethod = RetailPaymentMethod.VNPAY.name();
    /** Pickup/payment deadline of a cash checkout (72 hours after acceptance); null for VNPay. */
    @Column(name = "counter_payment_deadline") private Instant counterPaymentDeadline;
    @Version @Column(nullable = false) private Long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @OneToMany(mappedBy = "groupedCheckout", cascade = CascadeType.ALL, orphanRemoval = true) private final List<RetailOrder> orders = new ArrayList<>();

    public GroupedCheckout(Long memberAccountId, String idempotencyKey, long cartVersion,
                           BigDecimal merchandiseSubtotal, BigDecimal shippingCharge, BigDecimal discountAmount) {
        this.memberAccountId = memberAccountId;
        this.idempotencyKey = idempotencyKey;
        this.cartVersion = cartVersion;
        this.merchandiseSubtotal = merchandiseSubtotal;
        this.shippingCharge = shippingCharge;
        this.discountAmount = discountAmount;
        this.payableAmount = merchandiseSubtotal.add(shippingCharge).subtract(discountAmount);
        this.paymentState = PaymentState.ACCEPTED_UNPAID;
    }

    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
    public void addOrder(RetailOrder order) { orders.add(order); }
    /** Marks this accepted checkout as paid in cash at the branch counter before {@code deadline}. */
    public void useCounterCash(Instant deadline) {
        this.paymentMethod = RetailPaymentMethod.CASH_AT_COUNTER.name();
        this.counterPaymentDeadline = deadline;
    }
    public boolean isCounterCash() { return RetailPaymentMethod.CASH_AT_COUNTER.name().equals(paymentMethod); }
    public void beginPayment() { this.paymentState = PaymentState.PAYMENT_INITIATED; this.paymentInitiatedAt = Instant.now(); }
    public void markPaid() { this.paymentState = PaymentState.PAID; orders.forEach(RetailOrder::markPaid); }
    public void markPaidWithProof(String proofRef) { this.paymentProofRef = proofRef; markPaid(); }
    public void cancel() { this.paymentState = PaymentState.CANCELLED; }
    public void cancelWithRestoration(int unrestoredQuantity) {
        this.cancellationUnrestoredQuantity = unrestoredQuantity;
        this.paymentState = PaymentState.CANCELLED;
        orders.forEach(RetailOrder::cancel);
    }
    public void expireLocalDemo() { this.paymentState = PaymentState.EXPIRED; orders.forEach(RetailOrder::expireLocalDemo); }
    public void expireUnpaid() { this.paymentState = PaymentState.EXPIRED; orders.forEach(RetailOrder::expireUnpaid); }
    /**
     * Cash checkout with branch orders settled one by one (Session 2026-11-13, D-011): stays accepted while any branch
     * order is unpaid, then becomes paid when at least one was paid, cancelled when all were cancelled, else expired.
     */
    public void resolveFromOrders() {
        if (orders.stream().anyMatch(order -> order.getPaymentState() == PaymentState.ACCEPTED_UNPAID)) return;
        if (orders.stream().anyMatch(order -> order.getPaymentState() == PaymentState.PAID)) this.paymentState = PaymentState.PAID;
        else if (orders.stream().allMatch(order -> order.getPaymentState() == PaymentState.CANCELLED)) this.paymentState = PaymentState.CANCELLED;
        else this.paymentState = PaymentState.EXPIRED;
    }
}
