package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "reviews", uniqueConstraints = {
        @UniqueConstraint(name = "uq_reviews_source", columnNames = {"source_type", "source_reference"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 32, nullable = false)
    private SourceType sourceType;

    @Column(name = "source_reference", length = 128, nullable = false)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32, nullable = false)
    private TargetType targetType;

    @Column(name = "target_reference", length = 128, nullable = false)
    private String targetReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", length = 16, nullable = false)
    private OwnerType ownerType;

    @Column(name = "owner_reference", length = 128, nullable = false)
    private String ownerReference;

    @Column(name = "masked_display_name", length = 128, nullable = false)
    private String maskedDisplayName;

    /** Package of the attended workshop booking (V104); null for retail reviews. Stored with the review, never changed. */
    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "package_name", length = 255)
    private String packageName;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    public java.util.List<String> getImageList() {
        if (imageUrls == null || imageUrls.isBlank()) return java.util.List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(imageUrls, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public void setImageList(java.util.List<String> list) {
        if (list == null || list.isEmpty()) {
            this.imageUrls = null;
            return;
        }
        try {
            this.imageUrls = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            this.imageUrls = null;
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ReviewStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = ReviewStatus.PUBLIC;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

