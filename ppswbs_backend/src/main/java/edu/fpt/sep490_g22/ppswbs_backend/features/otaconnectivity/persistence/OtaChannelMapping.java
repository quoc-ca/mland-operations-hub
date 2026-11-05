package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ota_channel_mappings", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ota_mapping_external_option", columnNames = {"connection_id", "external_product_id", "external_option_id"}),
    @UniqueConstraint(name = "uq_ota_mapping_session", columnNames = {"connection_id", "session_reference"})
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaChannelMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, length = 36, updatable = false)
    private String publicReference;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private OtaChannelConnection connection;

    @Column(name = "offer_reference", nullable = false, length = 128)
    private String offerReference;

    @Column(name = "session_reference", nullable = false, length = 128)
    private String sessionReference;

    @Column(name = "location_reference", nullable = false, length = 128)
    private String locationReference;

    @Column(name = "external_product_id", nullable = false, length = 255)
    private String externalProductId;

    @Column(name = "external_option_id", nullable = false, length = 255)
    private String externalOptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OtaMappingStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicReference == null) publicReference = UUID.randomUUID().toString();
        if (version == null) version = 1L;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void startPreparationSync() {
        if (status != OtaMappingStatus.DRAFT && status != OtaMappingStatus.SALES_CLOSED) {
            throw new IllegalStateException("Mapping cannot enter preparation sync from its current state");
        }
        status = OtaMappingStatus.PREPARATION_SYNC;
    }

    public void completePreparationSync() {
        requireStatus(OtaMappingStatus.PREPARATION_SYNC);
        status = OtaMappingStatus.SALES_CLOSED;
    }

    public void enableBooking() {
        requireStatus(OtaMappingStatus.SALES_CLOSED);
        status = OtaMappingStatus.BOOKING_ENABLED;
    }

    public void closeSales() {
        if (status == OtaMappingStatus.SALES_CLOSED) return;
        requireStatus(OtaMappingStatus.BOOKING_ENABLED);
        status = OtaMappingStatus.SALES_CLOSED;
    }

    public void suspendAfterSyncFailure() {
        if (status == OtaMappingStatus.DISABLED || status == OtaMappingStatus.DRAFT) {
            throw new IllegalStateException("Mapping cannot be suspended from its current state");
        }
        status = OtaMappingStatus.SYNC_SUSPENDED;
    }

    public void returnToSalesClosedAfterConfirmedFullResync() {
        requireStatus(OtaMappingStatus.SYNC_SUSPENDED);
        status = OtaMappingStatus.SALES_CLOSED;
    }

    public void disable() {
        status = OtaMappingStatus.DISABLED;
    }

    public void updateDetails(String offerReference, String sessionReference, String locationReference,
                              String externalProductId, String externalOptionId) {
        if (status != OtaMappingStatus.DRAFT && status != OtaMappingStatus.SALES_CLOSED) {
            throw new IllegalStateException("Only a draft or sales-closed mapping can be updated");
        }
        this.offerReference = required(offerReference, 128, "Workshop offer reference is required");
        this.sessionReference = required(sessionReference, 128, "Workshop session reference is required");
        this.locationReference = required(locationReference, 128, "Workshop location reference is required");
        this.externalProductId = required(externalProductId, 255, "Provider product reference is required");
        this.externalOptionId = required(externalOptionId, 255, "Provider option reference is required");
    }

    private static String required(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void requireStatus(OtaMappingStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Mapping is not in the required lifecycle state");
        }
    }
}
