package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ring_designs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingDesign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "design_reference", nullable = false, length = 64)
    private String designReference;

    @Column(name = "design_version", nullable = false)
    @Builder.Default
    private Integer designVersion = 1;

    @Column(name = "design_name", length = 255)
    private String designName;

    @Enumerated(EnumType.STRING)
    @Column(name = "design_type", nullable = false, length = 30)
    @Builder.Default
    private RingDesignType designType = RingDesignType.configured_design;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    private RingDesignOwnerType ownerType;

    @Column(name = "owner_reference", nullable = false, length = 128)
    private String ownerReference;

    @Column(name = "package_reference", nullable = false, length = 128)
    private String packageReference;

    @Column(name = "source_model_reference", length = 128)
    private String sourceModelReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_design_id")
    private RingDesign sourceDesign;

    @Column(name = "material_reference", length = 64)
    private String materialReference;

    @Column(name = "shape_reference", length = 64)
    private String shapeReference;

    @Column(name = "ring_size", length = 30)
    private String ringSize;

    @Column(name = "component_snapshot", columnDefinition = "JSON")
    private String componentSnapshot;

    @Column(name = "estimated_price", precision = 15, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "difficulty_score", precision = 10, scale = 4)
    private BigDecimal difficultyScore;

    @Column(name = "rules_version", length = 64)
    private String rulesVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RingDesignStatus status = RingDesignStatus.DRAFT;

    @Column(name = "booking_reference", length = 128)
    private String bookingReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

