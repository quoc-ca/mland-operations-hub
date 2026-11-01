package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ota_channel_connections", uniqueConstraints =
    @UniqueConstraint(name = "uq_ota_connection_provider_environment", columnNames = {"provider_code", "environment"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaChannelConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, length = 36, updatable = false)
    private String publicReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", nullable = false, length = 32)
    private OtaProvider providerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 16)
    private OtaEnvironment environment;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "credential_reference", length = 255)
    private String credentialReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicReference == null) publicReference = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
