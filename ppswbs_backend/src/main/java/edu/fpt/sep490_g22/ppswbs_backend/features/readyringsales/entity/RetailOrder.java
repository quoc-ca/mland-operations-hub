package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "retail_orders")
@Getter
@NoArgsConstructor
public class RetailOrder {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "grouped_checkout_id", nullable = false) private GroupedCheckout groupedCheckout;
    @Column(name = "location_id", nullable = false) private Long locationId;
    @Column(name = "fulfilment_method", nullable = false, length = 16) private String fulfilmentMethod;
    @Enumerated(EnumType.STRING) @Column(name = "payment_state", nullable = false, length = 32) private PaymentState paymentState = PaymentState.ACCEPTED_UNPAID;
    @Column(name = "fulfilment_state", nullable = false, length = 32) private String fulfilmentState;
    @Column(name = "pickup_ready_confirmed", nullable = false) private boolean pickupReadyConfirmed;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal subtotal;
    @Column(name = "shipping_charge", nullable = false, precision = 19, scale = 2) private BigDecimal shippingCharge;
    @Column(name = "payable_amount", nullable = false, precision = 19, scale = 2) private BigDecimal payableAmount;
    /** GHTK recipient snapshot at acceptance time; null for PICKUP. Later profile address edits do not change it. */
    @Column(name = "recipient_name", length = 255) private String recipientName;
    @Column(name = "recipient_phone", length = 32) private String recipientPhone;
    @Column(name = "recipient_address", length = 512) private String recipientAddress;
    @Column(name = "recipient_province", length = 128) private String recipientProvince;
    @Column(name = "recipient_district", length = 128) private String recipientDistrict;
    @Column(name = "recipient_ward", length = 128) private String recipientWard;
    @OneToMany(mappedBy = "retailOrder", cascade = CascadeType.ALL, orphanRemoval = true) private final List<OrderItem> items = new ArrayList<>();

    public RetailOrder(GroupedCheckout groupedCheckout, Long locationId, String fulfilmentMethod,
                       BigDecimal subtotal, BigDecimal shippingCharge, BigDecimal discountAmount) {
        this.groupedCheckout = groupedCheckout; this.locationId = locationId; this.fulfilmentMethod = fulfilmentMethod;
        this.subtotal = subtotal; this.shippingCharge = shippingCharge;
        this.payableAmount = subtotal.add(shippingCharge).subtract(discountAmount);
        this.fulfilmentState = "PICKUP".equals(fulfilmentMethod) ? "READY_FOR_PICKUP" : "SHIPMENT_PENDING";
    }
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
    public void addItem(OrderItem item) { items.add(item); }
    public void setRecipientSnapshot(String name, String phone, String address, String province, String district, String ward) {
        this.recipientName = name; this.recipientPhone = phone; this.recipientAddress = address;
        this.recipientProvince = province; this.recipientDistrict = district; this.recipientWard = ward;
    }
    public void markPaid() { paymentState = PaymentState.PAID; }
    public void markPickupReady() { fulfilmentState = "READY_FOR_PICKUP"; pickupReadyConfirmed = true; }
    public void markPickedUp() { fulfilmentState = "PICKED_UP"; }
    /**
     * Mirrors the fictional GHTK shipment status onto this branch order's fulfilment state.
     * Called by Staff shipment transitions; only meaningful for GHTK branch orders and a no-op for PICKUP.
     * Keeps the shipment-lifecycle vocabulary off arbitrary strings by binding to the enum directly.
     */
    public void syncShipmentFulfilmentState(RetailShipmentStatus shipmentStatus) {
        if (!"GHTK".equalsIgnoreCase(fulfilmentMethod) || shipmentStatus == null) {
            return;
        }
        switch (shipmentStatus) {
            case PREPARING -> fulfilmentState = "SHIPMENT_PENDING";
            case HANDED_TO_CARRIER -> fulfilmentState = "HANDED_TO_CARRIER";
            case IN_TRANSIT -> fulfilmentState = "IN_TRANSIT";
            case DELIVERED -> fulfilmentState = "DELIVERED";
        }
    }
    public void cancel() { paymentState = PaymentState.CANCELLED; }
    public void expireLocalDemo() { paymentState = PaymentState.EXPIRED; }
    public void expireUnpaid() { paymentState = PaymentState.EXPIRED; }
}
