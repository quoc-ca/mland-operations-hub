package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ring_gemstones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingGemstone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gemstone_code", nullable = false, unique = true, length = 64)
    private String gemstoneCode;

    @Column(name = "gemstone_name", nullable = false, length = 100)
    private String gemstoneName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "gemstone_type", length = 100)
    private String gemstoneType;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "cut_name", length = 100)
    private String cutName;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "difficulty_score", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal difficultyScore = BigDecimal.ONE;

    @Column(name = "img_url", columnDefinition = "TEXT")
    private String imgUrl;

    @Column(name = "max_quantity", nullable = false)
    @Builder.Default
    private Integer maxQuantity = 10;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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

