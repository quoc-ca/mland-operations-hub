package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

/**
 * Image attached to a product at a display position (data-model {@code catalogue_product_images}).
 * Positions 0–9 are unique per product, so a product has at most 10 images. Detaching removes this
 * link only; the media object and its history are kept.
 */
@Entity
@Table(name = "catalogue_product_images",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogue_product_images_position",
                columnNames = {"product_id", "display_position"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    public static final int MAX_IMAGES_PER_PRODUCT = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "media_object_id", nullable = false, length = 36)
    private UUID mediaObjectId;

    @Column(name = "alt_text", nullable = false, length = 200)
    private String altText;

    @Column(name = "display_position", nullable = false)
    private int displayPosition;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static ProductImage attach(Long productId, UUID mediaObjectId, String rawAltText, int position) {
        ProductImage image = new ProductImage();
        image.productId = Objects.requireNonNull(productId, "productId");
        image.mediaObjectId = Objects.requireNonNull(mediaObjectId, "mediaObjectId");
        image.altText = CatalogueValues.altText("altText", rawAltText);
        image.displayPosition = requirePosition(position);
        return image;
    }

    public void update(String rawAltText, int position) {
        this.altText = CatalogueValues.altText("altText", rawAltText);
        this.displayPosition = requirePosition(position);
    }

    private static int requirePosition(int position) {
        if (position < 0 || position >= MAX_IMAGES_PER_PRODUCT) {
            throw CatalogueException.invalidField("position", "Position must be from 0 to 9.");
        }
        return position;
    }
}
