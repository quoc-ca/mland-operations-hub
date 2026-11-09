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
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueBranchScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.SkuGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
    private static final Logger log = LoggerFactory.getLogger(ProductImportService.class);
    private static final TypeReference<List<PayloadRow>> PAYLOAD = new TypeReference<>() {
    };
    private static final TypeReference<List<ProductImportDtos.ImportError>> ERRORS = new TypeReference<>() {
    };
    private static final TypeReference<List<ProductImportDtos.Created>> CREATED = new TypeReference<>() {
    };

    /**
     * One normalized data row of a VALIDATED batch (data-model {@code parsed_payload}). {@code branchQuantities}
     * (US10, Q-B2): branch id → quantity, in the order of the chosen branches; null for a file without branches.
     */
    record PayloadRow(int row, String groupKey, String name, String description, String categoryId,
                      String categoryName, int size, String proposedPrice, String quantity,
                      Map<String, String> branchQuantities) {
    }

    private final ProductImportBatchRepository batches;
    private final ProductCategoryRepository categories;
    private final CatalogueLockCoordinator locks;
    private final CreateProductService createProducts;
    private final CatalogueAuditWriter audit;
    private final CatalogueCommandExecutor executor;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;
    private final ProductBranchStockService branchStock;
    private final CatalogueBranchScope branchScope;

    public ProductImportService(ProductImportBatchRepository batches, ProductCategoryRepository categories,
                                CatalogueLockCoordinator locks, CreateProductService createProducts,
                                CatalogueAuditWriter audit, CatalogueCommandExecutor executor,
                                TransactionTemplate transaction, ObjectMapper objectMapper,
                                ProductBranchStockService branchStock, CatalogueBranchScope branchScope) {
        this.branchStock = branchStock;
        this.branchScope = branchScope;
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
        return validate(actorId, commandId, fileName, file, List.of());
    }

    /**
     * {@code locationIds} (US10, Q-B2): the branches chosen on the import screen, in column order; each must be active
     * and in the actor's scope. Empty = the original template without branches (products start with no branch).
     */
    public CatalogueCommandExecutor.Outcome validate(String actorId, UUID commandId, String fileName, byte[] file,
                                                     List<String> locationIds) {
        List<String> branchIds = chosenBranches(locationIds);
        List<String> branchHeaders = branchHeaders(branchIds);
        List<ProductImportParser.RawRow> rows;
        try {
            rows = ProductImportParser.parse(fileName, file, branchHeaders);
        } catch (CatalogueException rejected) {
            recordRejectedFile(actorId, commandId, fileName, file, rejected);
            throw rejected;
        }
        String sha256 = sha256(file);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("fileSha256", sha256);
        if (!branchIds.isEmpty()) {
            request.put("locationIds", branchIds);
        }
        var key = new CatalogueCommandExecutor.CommandKey(actorId, VALIDATE_ACTION, commandId,
                CatalogueCommandExecutor.digest(request));
        return executor.execute(key, () -> {
            ProductImportValidator.Result result = ProductImportValidator.validate(rows,
                    categories.findByActiveTrueOrderByNameAscIdAsc(), branchHeaders);
            Instant now = CatalogueValues.toStoredInstant(Instant.now());
            String storedName = storedFileName(fileName);
            ProductImportBatch batch = batches.saveAndFlush(ProductImportBatch.validated(UUID.randomUUID(), actorId,
                    storedName, sha256, commandId, result.rowCount(), result.products().size(),
                    result.variantCount(), result.errors().size(),
                    toJson(result.errors().stream().map(ProductImportService::view).toList()),
                    result.valid() ? toJson(payload(result, branchIds)) : null, now));
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("state", batch.getState().name());
            changes.put("fileName", batch.getFileName());
            changes.put("rowCount", batch.getRowCount());
            changes.put("productCount", batch.getProductCount());
            changes.put("variantCount", batch.getVariantCount());
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
            // A file row with several sizes gives several payload rows; its stale category is reported once.
            Set<Integer> staleRows = new HashSet<>();
            for (PayloadRow row : rows) {
                ProductCategory category = lockedCategories.get(Long.parseLong(row.categoryId()));
                if ((category == null || !category.isActive()) && staleRows.add(row.row())) {
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
                        .toList(), List.of(), branchInputs(group));
                long productId = createProducts.createDraft(actorId, command, CreateProductService.Source.IMPORT);
                created.add(new ProductImportDtos.Created(first.groupKey(), Long.toString(productId),
                        group.stream().map(row -> SkuGenerator.sku(productId, row.size())).toList()));
            }
            batch.markImported(commandId, toJson(created), now);
            ProductImportBatch saved = batches.saveAndFlush(batch);
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("state", saved.getState().name());
            changes.put("fileName", saved.getFileName());
            changes.put("rowCount", saved.getRowCount());
            changes.put("productCount", saved.getProductCount());
            changes.put("variantCount", saved.getVariantCount());
            changes.put("productIds", created.stream().map(ProductImportDtos.Created::productId).toList());
            audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, CONFIRM_ACTION, "import-batch",
                    saved.getId().toString(), saved.getVersion(), null, List.of("state"), changes));
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
                    Map<String, Object> changes = new LinkedHashMap<>();
                    changes.put("state", saved.getState().name());
                    changes.put("reason", "IMPORT_PREVIEW_STALE");
                    changes.put("fileName", saved.getFileName());
                    changes.put("rowCount", saved.getRowCount());
                    changes.put("productCount", saved.getProductCount());
                    changes.put("variantCount", saved.getVariantCount());
                    changes.put("errorCount", saved.getErrorCount());
                    audit.recordSuccess(new CatalogueAuditWriter.Entry(actorId, CONFIRM_ACTION, "import-batch",
                            saved.getId().toString(), saved.getVersion(), null, List.of("state"), changes));
                }));
    }

    /**
     * FR-045: a file refused before any batch exists (wrong type, broken, macro, header, row limit) is audited on its
     * own — actor, file name, byte count and error code; no cell value or file content is stored.
     */
    private void recordRejectedFile(String actorId, UUID commandId, String fileName, byte[] file,
                                    CatalogueException rejected) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("code", rejected.code().name());
        changes.put("fileName", storedFileName(fileName));
        changes.put("byteCount", file == null ? 0 : file.length);
        try {
            audit.recordRejection(new CatalogueAuditWriter.Entry(actorId, VALIDATE_ACTION, "import-file",
                    commandId == null ? null : commandId.toString(), null, null, null, changes));
        } catch (RuntimeException auditFailure) {
            log.warn("Could not audit rejected import file code={}", rejected.code().name(), auditFailure);
        }
    }

    /** File name as stored on the batch: trimmed, at most 255 code points; empty when the upload has none. */
    private static String storedFileName(String fileName) {
        String name = fileName == null ? "" : fileName.strip();
        if (name.codePointCount(0, name.length()) > 255) {
            name = name.substring(0, name.offsetByCodePoints(0, 255));
        }
        return name;
    }

    private static final class StalePreview extends RuntimeException {
        private final transient List<ProductImportDtos.ImportError> errors;

        private StalePreview(List<ProductImportDtos.ImportError> errors) {
            super(null, null, false, false);
            this.errors = List.copyOf(errors);
        }
    }

    /**
     * Branches of one product from its payload rows (US10). createDraft checks again that each branch is active and in
     * the actor's scope at confirmation time. A batch without branches gives an empty list (no branch yet).
     */
    private static List<ProductBranchStockService.BranchInput> branchInputs(List<PayloadRow> group) {
        if (group.get(0).branchQuantities() == null) {
            return List.of();
        }
        List<ProductBranchStockService.BranchInput> branches = new ArrayList<>();
        for (String locationId : group.get(0).branchQuantities().keySet()) {
            branches.add(new ProductBranchStockService.BranchInput(locationId, group.stream()
                    .map(row -> new ProductBranchStockService.SizeQuantity(row.size(),
                            row.branchQuantities().getOrDefault(locationId, "0")))
                    .toList()));
        }
        return branches;
    }

    /** Rows grouped by group key, in file order. */
    private static Map<String, List<PayloadRow>> groups(List<PayloadRow> rows) {
        Map<String, List<PayloadRow>> groups = new LinkedHashMap<>();
        rows.forEach(row -> groups.computeIfAbsent(row.groupKey(), key -> new ArrayList<>()).add(row));
        return groups;
    }

    private static List<PayloadRow> payload(ProductImportValidator.Result result, List<String> branchIds) {
        List<PayloadRow> rows = new ArrayList<>();
        for (ProductImportValidator.PreviewProduct product : result.products()) {
            for (ProductImportValidator.PreviewVariant variant : product.variants()) {
                Map<String, String> branchQuantities = null;
                if (variant.branchQuantities() != null) {
                    branchQuantities = new LinkedHashMap<>();
                    for (int i = 0; i < branchIds.size(); i++) {
                        branchQuantities.put(branchIds.get(i), variant.branchQuantities().get(i).toString());
                    }
                }
                rows.add(new PayloadRow(variant.row(), product.groupKey(), product.name(), product.description(),
                        Long.toString(product.categoryId()), product.categoryName(), variant.size(),
                        variant.proposedPrice().toString(), variant.quantity().toString(), branchQuantities));
            }
        }
        return rows;
    }

    /** Distinct branch ids, each a positive number, active and in the actor's scope (Q-B2, FR-050). */
    private List<String> chosenBranches(List<String> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        List<Long> numbers = new ArrayList<>();
        for (int i = 0; i < locationIds.size(); i++) {
            long id = CatalogueValues.parseId("locationIds[" + i + "]", locationIds.get(i));
            if (numbers.contains(id)) {
                throw CatalogueException.invalidField("locationIds[" + i + "]", "Each branch may be chosen once.");
            }
            numbers.add(id);
            ids.add(Long.toString(id));
        }
        Map<Long, SellingLocation> active = branchStock.activeBranches();
        if (!active.keySet().containsAll(numbers)) {
            throw new CatalogueException(CatalogueErrorCode.BRANCH_NOT_FOUND);
        }
        branchScope.requireBranches(numbers);
        return List.copyOf(ids);
    }

    /** Header of each branch quantity column: "Số lượng " + the branch name (the canonical list has no code here). */
    private List<String> branchHeaders(List<String> branchIds) {
        if (branchIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SellingLocation> active = branchStock.activeBranches();
        return branchIds.stream()
                .map(id -> ImportColumn.QUANTITY.header() + " " + active.get(Long.parseLong(id)).displayName().strip())
                .toList();
    }

    /** Template headers for the chosen branches (validated like an upload). */
    public List<String> templateBranchHeaders(List<String> locationIds) {
        return branchHeaders(chosenBranches(locationIds));
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
