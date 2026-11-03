package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImportBatchRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ProductImportDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImportBatch;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuditWriter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueLockCoordinator;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.SkuGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Two-step Excel import (T098, US9, FR-039/042/044/045, FR-026/028).
 *
 * <ul>
 *   <li><b>Validate</b>: parses the first sheet (no database work), validates against the active categories and
 *       stores one batch: VALIDATED with the normalized rows, or REJECTED with every row error. The receipt
 *       digest is the file's SHA-256, so the same key and file return the original batch. Nothing is created
 *       and no product number or SKU is allocated. The file itself is not kept.</li>
 *   <li><b>Confirm</b>: under the batch lock checks owner, state, expiry (60 minutes) and version, locks the
 *       categories in ascending order, re-checks the payload against the latest data and creates every product
 *       through {@link CreateProductService#createDraft} (source IMPORT) in one transaction with one audit
 *       entry per product, a batch audit entry and one receipt. If the data changed, nothing is created, the
 *       batch becomes REJECTED and the caller gets {@code IMPORT_PREVIEW_STALE}.</li>
 * </ul>
 * Imported products are drafts without images: nothing is submitted, applied or published. No remote call is
 * made while locks are held. Receipts keep only the batch ID, state and created product IDs, not cell values.
 * The caller authorizes {@code product.create} first (T007); a batch of another actor is not found.
 */
@Service
public class ProductImportService {

    public static final String VALIDATE_ACTION = "product.import.validate";
    public static final String CONFIRM_ACTION = "product.import.confirm";
    private static final TypeReference<List<PayloadRow>> PAYLOAD = new TypeReference<>() {
    };
    private static final TypeReference<List<ProductImportDtos.ImportError>> ERRORS = new TypeReference<>() {
    };
    private static final TypeReference<List<ProductImportDtos.Created>> CREATED = new TypeReference<>() {
    };

    /** One normalized data row of a VALIDATED batch (data-model {@code parsed_payload}). */
    record PayloadRow(int row, String groupKey, String name, String description, String categoryId,
                      String categoryName, int size, String proposedPrice, String quantity) {
    }

    private final ProductImportBatchRepository batches;
    private final ProductCategoryRepository categories;
    private final CatalogueLockCoordinator locks;
    private final CreateProductService createProducts;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public ProductImportService(ProductImportBatchRepository batches, ProductCategoryRepository categories,
                                CatalogueLockCoordinator locks, CreateProductService createProducts,
                                CatalogueAuditWriter audit, CatalogueCommandExecutor executor,
                                TransactionTemplate transaction, ObjectMapper objectMapper) {
        this.batches = batches;
        this.categories = categories;
        this.locks = locks;
        this.createProducts = createProducts;
        this.audit = audit;
        this.executor = executor;
        this.transaction = transaction;
        this.objectMapper = objectMapper;
    }

    public CatalogueCommandExecutor.Outcome validate(String actorId, UUID commandId, String fileName, byte[] file) {
        List<ProductImportParser.RawRow> rows = ProductImportParser.parse(fileName, file);
        String sha256 = sha256(file);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, VALIDATE_ACTION, commandId,
                CatalogueCommandExecutor.digest(Map.of("fileSha256", sha256)));
        return executor.execute(key, () -> {
            ProductImportValidator.Result result = ProductImportValidator.validate(rows,
                    categories.findByActiveTrueOrderByNameAscIdAsc());
            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            String storedName = fileName.strip();
            if (storedName.codePointCount(0, storedName.length()) > 255) {
                storedName = storedName.substring(0, storedName.offsetByCodePoints(0, 255));
            }
            ProductImportBatch batch = batches.saveAndFlush(ProductImportBatch.validated(UUID.randomUUID(), actorId,
                    storedName, sha256, commandId, result.rowCount(), result.products().size(),
                    result.variantCount(), result.errors().size(),
                    toJson(result.errors().stream().map(ProductImportService::view).toList()),
                    result.valid() ? toJson(payload(result)) : null, now));
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("state", batch.getState().name());
            changes.put("rowCount", batch.getRowCount());
            changes.put("errorCount", batch.getErrorCount());
            changes.put("fileSha256", sha256);
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, VALIDATE_ACTION, "import-batch",
                    batch.getId().toString(), batch.getVersion(), null, List.of("state"), changes));
            return receiptResult(201, batch, List.of());
        });
    }

    public CatalogueCommandExecutor.Outcome confirm(String actorId, UUID commandId, String batchId,
                                                    long expectedVersion) {
        UUID id = parseBatchId(batchId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("batchId", id.toString());
        request.put("expectedVersion", expectedVersion);
        var key = new CatalogueCommandExecutor.CommandKey(actorId, CONFIRM_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        try {
            return executor.execute(key, confirmWork(actorId, commandId, id, expectedVersion));
        } catch (StalePreview stale) {
            recordStale(actorId, id, stale.errors);
            throw new CatalogueException(CatalogueErrorCode.IMPORT_PREVIEW_STALE);
        }
    }

    /** Current state for the batch owner; payload and errors are not shown once the batch has expired. */
    @Transactional(readOnly = true)
    public ProductImportDtos.ImportBatch get(String actorId, String batchId) {
        ProductImportBatch batch = owned(actorId, batches.findById(parseBatchId(batchId)));
        return view(batch, CatalogueValues.toStoredInstant(Instant.now()));
    }

    /** Error report of the owner's batch; refused once expired because the errors are removed. */
    @Transactional(readOnly = true)
    public byte[] errorReport(String actorId, String batchId) {
        ProductImportBatch batch = owned(actorId, batches.findById(parseBatchId(batchId)));
        if (batch.isExpiredAt(Instant.now()) || batch.getErrors() == null && batch.getErrorCount() > 0) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_BATCH_EXPIRED);
        }
        List<ProductImportValidator.ImportError> errors = batch.getErrors() == null ? List.of()
                : fromJson(batch.getErrors(), ERRORS).stream()
                .map(error -> new ProductImportValidator.ImportError(error.row(), error.column(), error.value(),
                        error.code(), error.message()))
                .toList();
        return ImportErrorReportWriter.write(errors);
    }

    private Supplier<CatalogueCommandExecutor.CommandResult> confirmWork(String actorId, UUID commandId, UUID id,
                                                                        long expectedVersion) {
        return () -> {
            ProductImportBatch batch = owned(actorId, batches.lockById(id));
            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            if (batch.getState() == ProductImportBatch.State.IMPORTED) {
                return receiptResult(200, batch, fromJson(batch.getCreatedProducts(), CREATED));
            }
            if (batch.getState() == ProductImportBatch.State.REJECTED) {
                throw new CatalogueException(CatalogueErrorCode.IMPORT_HAS_ERRORS);
            }
            if (batch.isExpiredAt(now) || batch.getParsedPayload() == null) {
                throw new CatalogueException(CatalogueErrorCode.IMPORT_BATCH_EXPIRED);
            }
            CatalogueLockCoordinator.requireVersion(batch.getVersion(), expectedVersion);

            List<PayloadRow> rows = fromJson(batch.getParsedPayload(), PAYLOAD);
            Set<Long> categoryIds = new TreeSet<>();
            rows.forEach(row -> categoryIds.add(Long.parseLong(row.categoryId())));
            var lockedCategories = locks.lock(new CatalogueLockCoordinator.LockRequest(categoryIds, Set.of(), Set.of()))
                    .categories();
            List<ProductImportDtos.ImportError> stale = new ArrayList<>();
            for (PayloadRow row : rows) {
                ProductCategory category = lockedCategories.get(Long.parseLong(row.categoryId()));
                if (category == null || !category.isActive()) {
                    stale.add(new ProductImportDtos.ImportError(row.row(), ImportColumn.CATEGORY.header(),
                            row.categoryName(), "CATEGORY_NOT_ACTIVE_OR_UNKNOWN",
                            "The category does not exist or is not active."));
                }
            }
            if (!stale.isEmpty()) {
                throw new StalePreview(stale);
            }

            List<ProductImportDtos.Created> created = new ArrayList<>();
            for (List<PayloadRow> group : groups(rows).values()) {
                PayloadRow first = group.get(0);
                var command = new CreateProductService.CreateProductCommand(first.name(), first.description(),
                        first.categoryId(), group.stream()
                        .map(row -> new CreateProductService.VariantInput(row.size(), row.proposedPrice(), row.quantity()))
                        .toList(), List.of());
                long productId = createProducts.createDraft(actorId, command, CreateProductService.Source.IMPORT);
                created.add(new ProductImportDtos.Created(first.groupKey(), Long.toString(productId),
                        group.stream().map(row -> SkuGenerator.sku(productId, row.size())).toList()));
            }
            batch.markImported(commandId, toJson(created), now);
            ProductImportBatch saved = batches.saveAndFlush(batch);
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, CONFIRM_ACTION, "import-batch",
                    saved.getId().toString(), saved.getVersion(), null, List.of("state"),
                    Map.of("state", saved.getState().name(), "productIds",
                            created.stream().map(ProductImportDtos.Created::productId).toList())));
            return receiptResult(200, saved, created);
        };
    }

    /** The confirmation rolled back; record the rejection in a new transaction if nothing else changed it. */
    private void recordStale(String actorId, UUID id, List<ProductImportDtos.ImportError> errors) {
        transaction.executeWithoutResult(status -> batches.lockById(id)
                .filter(batch -> batch.getState() == ProductImportBatch.State.VALIDATED)
                .ifPresent(batch -> {
                    Instant now = CatalogueValues.toStoredInstant(Instant.now());
                    batch.markStale(errors.size(), toJson(errors), now);
                    ProductImportBatch saved = batches.saveAndFlush(batch);
                    audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, CONFIRM_ACTION, "import-batch",
                            saved.getId().toString(), saved.getVersion(), null, List.of("state"),
                            Map.of("state", saved.getState().name(), "reason", "IMPORT_PREVIEW_STALE",
                                    "errorCount", saved.getErrorCount())));
                }));
    }

    private static final class StalePreview extends RuntimeException {
        private final transient List<ProductImportDtos.ImportError> errors;

        private StalePreview(List<ProductImportDtos.ImportError> errors) {
            super(null, null, false, false);
            this.errors = List.copyOf(errors);
        }
    }

    /** Rows grouped by group key, in file order. */
    private static Map<String, List<PayloadRow>> groups(List<PayloadRow> rows) {
        Map<String, List<PayloadRow>> groups = new LinkedHashMap<>();
        rows.forEach(row -> groups.computeIfAbsent(row.groupKey(), key -> new ArrayList<>()).add(row));
        return groups;
    }

    private static List<PayloadRow> payload(ProductImportValidator.Result result) {
        List<PayloadRow> rows = new ArrayList<>();
        for (ProductImportValidator.PreviewProduct product : result.products()) {
            for (ProductImportValidator.PreviewVariant variant : product.variants()) {
                rows.add(new PayloadRow(variant.row(), product.groupKey(), product.name(), product.description(),
                        Long.toString(product.categoryId()), product.categoryName(), variant.size(),
                        variant.proposedPrice().toString(), variant.quantity().toString()));
            }
        }
        return rows;
    }

    private static ProductImportDtos.ImportError view(ProductImportValidator.ImportError error) {
        return new ProductImportDtos.ImportError(error.row(), error.column(), error.value(), error.code(),
                error.message());
    }

    private ProductImportDtos.ImportBatch view(ProductImportBatch batch, Instant now) {
        boolean expired = batch.isExpiredAt(now);
        String state = batch.getState() == ProductImportBatch.State.VALIDATED && expired
                ? ProductImportBatch.State.EXPIRED.name() : batch.getState().name();
        List<ProductImportDtos.ImportError> errors = expired || batch.getErrors() == null ? List.of()
                : fromJson(batch.getErrors(), ERRORS);
        List<ProductImportDtos.PreviewProduct> preview = null;
        if (!expired && batch.getState() == ProductImportBatch.State.VALIDATED && batch.getParsedPayload() != null) {
            preview = groups(fromJson(batch.getParsedPayload(), PAYLOAD)).values().stream()
                    .map(group -> new ProductImportDtos.PreviewProduct(group.get(0).groupKey(), group.get(0).name(),
                            group.get(0).categoryId(), group.stream().map(PayloadRow::size).toList()))
                    .toList();
        }
        List<ProductImportDtos.Created> created = batch.getState() == ProductImportBatch.State.IMPORTED
                ? fromJson(batch.getCreatedProducts(), CREATED) : null;
        return new ProductImportDtos.ImportBatch(batch.getId(), batch.getFileName(), state, batch.getVersion(),
                batch.getRowCount(), batch.getProductCount(), batch.getVariantCount(), batch.getErrorCount(), errors,
                preview, created, batch.getExpiresAt(), batch.getImportedAt());
    }

    /** Minimal receipt body: no cell values, so nothing in it outlives the batch's errors. */
    private static CatalogueCommandExecutor.CommandResult receiptResult(int status, ProductImportBatch batch,
                                                                        List<ProductImportDtos.Created> created) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("batchId", batch.getId().toString());
        body.put("state", batch.getState().name());
        body.put("version", batch.getVersion());
        body.put("productIds", created.stream().map(ProductImportDtos.Created::productId).toList());
        return new CatalogueCommandExecutor.CommandResult(status, "import-batch", batch.getId().toString(),
                batch.getVersion(), body);
    }

    private static ProductImportBatch owned(String actorId, Optional<ProductImportBatch> found) {
        return found.filter(batch -> batch.getActorId().equals(actorId))
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.IMPORT_BATCH_NOT_FOUND));
    }

    private static UUID parseBatchId(String raw) {
        try {
            UUID id = UUID.fromString(raw);
            if (!id.toString().equals(raw)) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (RuntimeException e) {
            throw new CatalogueException(CatalogueErrorCode.IMPORT_BATCH_NOT_FOUND);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize import data", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored import data is not valid JSON", e);
        }
    }
}
