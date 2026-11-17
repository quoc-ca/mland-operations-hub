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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A fictional GHTK shipment for one GHTK branch order. The fee that funded this shipment was already a
 * real (or real-shaped) GHTK quote collected at checkout time; everything from here on, including the
 * mock tracking number and every status transition, is a Staff-driven simulation with no real GHTK
 * shipment-creation or webhook call behind it. See {@code ghtk-integration.md}: automatic real shipment
 * creation stays behind its own go-live gate and is out of scope for this slice.
 */
@Entity
@Table(name = "retail_shipments")
@Getter
@NoArgsConstructor
public class RetailShipment {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grouped_checkout_id", nullable = false)
    private GroupedCheckout groupedCheckout;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_order_id", nullable = false, unique = true)
    private RetailOrder branchOrder;

    /** Stable identifier for Staff/Member display; equals the branch order id but kept distinct from the FK. */
    @Column(name = "branch_order_identifier", nullable = false, length = 36)
    private String branchOrderIdentifier;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "fulfilment_method", nullable = false, length = 16)
    private String fulfilmentMethod;

    @Column(name = "mock_tracking_number", nullable = false, length = 32, unique = true)
    private String mockTrackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RetailShipmentStatus status;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Column(name = "recipient_address", length = 512)
    private String recipientAddress;

    @Column(name = "province_name", length = 128)
    private String provinceName;

    @Column(name = "district_name", length = 128)
    private String districtName;

    @Column(name = "ward_name", length = 128)
    private String wardName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private final List<RetailShipmentStatusHistory> history = new ArrayList<>();

    public RetailShipment(GroupedCheckout groupedCheckout, RetailOrder branchOrder, String mockTrackingNumber,
                          String recipientName, String recipientPhone, String recipientAddress,
                          String provinceName, String districtName, String wardName) {
        this.groupedCheckout = groupedCheckout;
        this.branchOrder = branchOrder;
        this.branchOrderIdentifier = branchOrder.getId();
        this.locationId = branchOrder.getLocationId();
        this.fulfilmentMethod = branchOrder.getFulfilmentMethod();
        this.mockTrackingNumber = mockTrackingNumber;
        this.status = RetailShipmentStatus.PREPARING;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientAddress = recipientAddress;
        this.provinceName = provinceName;
        this.districtName = districtName;
        this.wardName = wardName;
    }

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    /** Appends a new, allowed transition. Rejects a skipped step, a repeat, or any move after delivery. */
    public RetailShipmentStatusHistory advanceTo(RetailShipmentStatus next, Long actorAccountId, String actorLabel, String note) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Shipment already delivered; no further transition is possible");
        }
        if (!status.canAdvanceTo(next)) {
            throw new IllegalStateException("Cannot move shipment from " + status + " to " + next);
        }
        this.status = next;
        RetailShipmentStatusHistory entry = new RetailShipmentStatusHistory(this, next, actorAccountId, actorLabel, note);
        history.add(entry);
        return entry;
    }
}
