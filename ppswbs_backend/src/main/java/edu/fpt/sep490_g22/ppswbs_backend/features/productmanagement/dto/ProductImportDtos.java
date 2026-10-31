package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response shapes of the import operations (OpenAPI {@code ProductImportBatch} and parts). IDs are decimal
 * strings. {@code preview} is present only for VALIDATED, {@code created} and {@code importedAt} only for
 * IMPORTED; {@code errors} is always present and empty once the batch has expired.
 */
public final class ProductImportDtos {

    private ProductImportDtos() {
    }

    public record ImportBatch(UUID batchId, String fileName, String state, long version, int rowCount,
                              int productCount, int variantCount, int errorCount, List<ImportError> errors,
                              @JsonInclude(JsonInclude.Include.NON_NULL) List<PreviewProduct> preview,
                              @JsonInclude(JsonInclude.Include.NON_NULL) List<Created> created,
                              Instant expiresAt,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Instant importedAt) {
    }

    public record ImportError(int row, String column, @JsonInclude(JsonInclude.Include.NON_NULL) String value,
                              String code, String message) {
    }

    /** No product number or SKU exists before confirmation. */
    public record PreviewProduct(String groupKey, String name, String categoryId, List<Integer> sizes) {
    }

    public record Created(String groupKey, String productId, List<String> skus) {
    }
}
