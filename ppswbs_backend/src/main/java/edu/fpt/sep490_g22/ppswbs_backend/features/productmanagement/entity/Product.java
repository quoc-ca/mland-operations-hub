package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * Ready-made ring product (data-model {@code catalogue_products}, FR-004–007, FR-012, FR-017–018, FR-022–023).
 *
 * <ul>
 *   <li>The generated {@code id} is the stable product number used in SKUs; it is never reissued,
 *       including after a soft delete.</li>
 *   <li>{@code contentVersion} changes only when reviewable content changes (name, description,
 *       category, images); quantity, holds and sale status never change it.</li>
 *   <li>{@code firstPublishedAt} is set once and survives hiding.</li>
 *   <li>A soft-deleted product rejects every command and is never restored.</li>
 * </ul>
 */
@Entity
@Table(name = "catalogue_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "first_published_at")
    private Instant firstPublishedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 128)
    private String deletedBy;

    @Column(name = "content_version", nullable = false)
    private long contentVersion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** New products are unpublished drafts; the caller has already checked that the category is active. */
    public static Product draft(String rawName, String rawDescription, Long activeCategoryId) {
        Product product = new Product();
        product.name = CatalogueValues.productName("name", rawName);
        product.description = CatalogueValues.productDescription("description", rawDescription);
        product.categoryId = Objects.requireNonNull(activeCategoryId, "categoryId");
        product.published = false;
        product.contentVersion = 0;
        return product;
    }

    /**
     * Replaces name, description and category. Returns whether reviewable content changed, in which
     * case {@code contentVersion} is incremented (the review service then invalidates the old review
     * before the first publication).
     */
    public boolean updateContent(String rawName, String rawDescription, Long categoryId) {
        requireNotDeleted();
        String newName = CatalogueValues.productName("name", rawName);
        String newDescription = CatalogueValues.productDescription("description", rawDescription);
        Long newCategory = Objects.requireNonNull(categoryId, "categoryId");
        boolean changed = !newName.equals(name) || !newDescription.equals(description) || !newCategory.equals(this.categoryId);
        if (changed) {
            this.name = newName;
            this.description = newDescription;
            this.categoryId = newCategory;
            this.contentVersion++;
        }
        return changed;
    }

    /** Image changes are reviewable content too. */
    public void markContentChanged() {
        requireNotDeleted();
        this.contentVersion++;
    }

    /** Explicit publication; eligibility is checked by the publication service. Returns whether it changed. */
    public boolean publish(Instant at) {
        requireNotDeleted();
        if (published) {
            return false;
        }
        this.published = true;
        if (firstPublishedAt == null) {
            this.firstPublishedAt = Objects.requireNonNull(at, "at");
        }
        return true;
    }

    /** Hides the product; {@code firstPublishedAt} is kept. Returns whether it changed. */
    public boolean unpublish() {
        requireNotDeleted();
        if (!published) {
            return false;
        }
        this.published = false;
        return true;
    }

    /** Soft delete from any non-deleted state; there is no restore. Returns whether it changed. */
    public boolean softDelete(String actorId, Instant at) {
        if (isDeleted()) {
            return false;
        }
        this.deletedAt = Objects.requireNonNull(at, "at");
        this.deletedBy = Objects.requireNonNull(actorId, "actorId");
        this.published = false;
        return true;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean hasEverBeenPublished() {
        return firstPublishedAt != null;
    }

    public void requireNotDeleted() {
        if (isDeleted()) {
            throw new CatalogueException(CatalogueErrorCode.PRODUCT_DELETED);
        }
    }
}
