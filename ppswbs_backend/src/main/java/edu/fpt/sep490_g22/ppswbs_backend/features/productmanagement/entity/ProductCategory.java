package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Catalogue category (data-model {@code catalogue_categories}, FR-029/030).
 *
 * <p>The name is unique system-wide, including inactive categories, through {@code nameKey}
 * (NFC, lower case, accents kept). The display name keeps the case and accents as entered after
 * trimming. A new category is active. A referenced category is deactivated, never deleted.
 */
@Entity
@Table(name = "catalogue_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogue_categories_name_key", columnNames = "name_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "name_key", nullable = false, columnDefinition = "varbinary(1600)")
    private byte[] nameKey;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Validates and normalizes the raw input; a new category is active (A-001). */
    public static ProductCategory create(String rawName, String rawDescription) {
        ProductCategory category = new ProductCategory();
        category.rename(rawName);
        category.describe(rawDescription);
        category.active = true;
        return category;
    }

    public void rename(String rawName) {
        String trimmed = CatalogueValues.categoryName("name", rawName);
        this.name = trimmed;
        this.nameKey = CatalogueValues.categoryNameKey(trimmed);
    }

    public void describe(String rawDescription) {
        this.description = CatalogueValues.categoryDescription("description", rawDescription);
    }

    /** Returns whether the state changed; repeating the same state is a no-op. */
    public boolean setActive(boolean active) {
        if (this.active == active) {
            return false;
        }
        this.active = active;
        return true;
    }
}
