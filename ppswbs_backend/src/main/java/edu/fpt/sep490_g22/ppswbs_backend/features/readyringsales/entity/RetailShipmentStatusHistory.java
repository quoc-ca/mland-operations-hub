package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retail_shipment_status_history")
@Getter
@NoArgsConstructor
public class RetailShipmentStatusHistory {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private RetailShipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RetailShipmentStatus status;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_label", nullable = false, length = 128)
    private String actorLabel;

    @Column(length = 512)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RetailShipmentStatusHistory(RetailShipment shipment, RetailShipmentStatus status,
                                       Long actorId, String actorLabel, String note) {
        this.shipment = shipment;
        this.status = status;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.note = note;
    }

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
