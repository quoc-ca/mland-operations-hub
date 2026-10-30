package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ota_reconciliation_work_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaReconciliationWorkItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, length = 36, updatable = false)
    private String publicReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_id")
    private OtaChannelMapping mapping;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_event_id")
    private OtaInboundEvent inboundEvent;

    // These OTA-owned foreign-key IDs are scalar until their persistence records are introduced.
    @Column(name = "channel_booking_id")
    private Long channelBookingId;

    @Column(name = "payout_position_id")
    private Long payoutPositionId;

    @Column(name = "location_reference", nullable = false, length = 128)
    private String locationReference;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "assigned_manager_reference", length = 128)
    private String assignedManagerReference;

    @Column(name = "decision", length = 64)
    private String decision;

    @Column(name = "resolution_reference", length = 255)
    private String resolutionReference;

    @Column(name = "audit_reference", length = 128)
    private String auditReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicReference == null) publicReference = UUID.randomUUID().toString();
        if (mapping != null && (auditReference == null || auditReference.isBlank())) {
            auditReference = UUID.randomUUID().toString();
        }
        validateMappedCorrelation();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        validateMappedCorrelation();
        updatedAt = Instant.now();
    }

    private void validateMappedCorrelation() {
        if (mapping == null) return;
        if (auditReference == null || !auditReference.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("A mapping-linked work item requires a valid audit correlation UUID");
        }
    }
}
