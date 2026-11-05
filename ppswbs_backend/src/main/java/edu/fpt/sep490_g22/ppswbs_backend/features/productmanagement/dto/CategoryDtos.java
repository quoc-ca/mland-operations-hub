package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto;

import java.util.List;

/**
 * Response shapes of the category management operations (OpenAPI {@code CategoryPage},
 * {@code CategoryImpact}). The category itself is {@link ManagementProductDtos.Category}.
 */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryPage(List<ManagementProductDtos.Category> items, int page, int pageSize,
                               String totalElements) {
    }

    /** Affected products and the category version the activation must be confirmed against. */
    public record CategoryImpact(String categoryId, long version, boolean active,
                                 ManagementProductDtos.ManagementProductPage affectedProducts) {
    }
}
