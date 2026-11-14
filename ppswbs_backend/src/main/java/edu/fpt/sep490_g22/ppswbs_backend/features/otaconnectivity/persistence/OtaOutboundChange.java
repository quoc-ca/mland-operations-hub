package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import com.fasterxml.jackson.databind.JsonNode;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "ota_outbound_changes", uniqueConstraints =
    @UniqueConstraint(name = "uq_ota_outbound_mapping_version_kind", columnNames = {"mapping_id", "source_version", "change_kind"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaOutboundChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false)
    private OtaChannelMapping mapping;

    @Column(name = "source_version", nullable = false)
    private Long sourceVersion;

    @Column(name = "source_change_reference", nullable = false, length = 128)
    private String sourceChangeReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_kind", nullable = false, length = 32)
    private OtaChangeKind changeKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_snapshot", nullable = false, columnDefinition = "json")
    private JsonNode canonicalSnapshot;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
