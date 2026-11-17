package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "retail_pickup_evidence")
@Getter
@NoArgsConstructor
public class PickupEvidence {
    @Id @Column(length = 36) private String id;
    @Column(name = "retail_order_id", nullable = false, length = 36) private String orderId;
    @Column(name = "location_id", nullable = false) private Long locationId;
    @Column(name = "actor_account_id", nullable = false) private Long actorAccountId;
    @Column(nullable = false, length = 16) private String milestone;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    public PickupEvidence(String orderId, Long locationId, Long actorAccountId, String milestone, Instant occurredAt) {
        this.id = UUID.randomUUID().toString(); this.orderId = orderId; this.locationId = locationId;
        this.actorAccountId = actorAccountId; this.milestone = milestone; this.occurredAt = occurredAt;
    }
}
