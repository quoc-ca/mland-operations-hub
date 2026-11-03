package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImportBatchRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ProductImportDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImportBatch;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ImportColumn;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportPayloadCleaner;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T094 (service part) and T100 on MySQL: validate → batch, confirm → products in one transaction, replay,
 * stale preview, expiry and cleanup (FR-039–045). HTTP, multipart and role checks belong to T007.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueImportBatchIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductImportService imports;
    @Autowired private ProductImportPayloadCleaner cleaner;
    @Autowired private ProductCategoryService categories;
    @Autowired private ProductImportBatchRepository batches;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private ProductReviewRepository reviews;
    @Autowired private TransactionTemplate transaction;

    @Test
    void validThenConfirmedCreatesEveryGroupAsADraftInOneStep() {
        String category = fixture.category(fixture.activeCategory()).getName();
        byte[] file = xlsx(row("A", "Nhẫn A", "Mô tả A", category, 5, "150000", 2),
                row("A", "Nhẫn A", "Mô tả A", category, 7, "160000", 0),
                row("B", "Nhẫn B", "Mô tả B", category, 12, "200000", 1));
        long before = productCount();

        UUID batchId = UUID.fromString(imports.validate(STAFF, UUID.randomUUID(), "san-pham.xlsx", file).resourceId());

        ProductImportDtos.ImportBatch validated = imports.get(STAFF, batchId.toString());
        assertThat(validated.state()).isEqualTo("VALIDATED");
        assertThat(validated.productCount()).isEqualTo(2);
        assertThat(validated.variantCount()).isEqualTo(3);
        assertThat(validated.preview()).extracting(ProductImportDtos.PreviewProduct::groupKey).containsExactly("A", "B");
        assertThat(validated.preview().get(0).sizes()).containsExactly(5, 7);
        assertThat(productCount()).isEqualTo(before);

        UUID key = UUID.randomUUID();
        var confirmed = imports.confirm(STAFF, key, batchId.toString(), validated.version());
        assertThat(imports.confirm(STAFF, key, batchId.toString(), validated.version()).replayed()).isTrue();

        assertThat(confirmed.body().get("state").asText()).isEqualTo("IMPORTED");
        ProductImportDtos.ImportBatch imported = imports.get(STAFF, batchId.toString());
        assertThat(imported.state()).isEqualTo("IMPORTED");
        assertThat(imported.preview()).isNull();
        assertThat(imported.importedAt()).isNotNull();
        assertThat(imported.created()).hasSize(2);
        assertThat(productCount()).isEqualTo(before + 2);
        ProductImportDtos.Created a = imported.created().get(0);
        long productA = Long.parseLong(a.productId());
        List<ProductVariant> sizes = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productA));
        assertThat(sizes).extracting(ProductVariant::getSku).containsExactlyElementsOf(a.skus());
        assertThat(sizes).extracting(variant -> variant.getRemainingUnsold().toString()).containsExactly("2", "0");
        Product product = transaction.execute(status -> products.findById(productA).orElseThrow());
        assertThat(product.isPublished()).isFalse();
        ReviewState reviewState = transaction.execute(status -> reviews.findFirstByProductIdOrderByRevisionDesc(productA)
                .orElseThrow().getState());
        assertThat(reviewState).isEqualTo(ReviewState.DRAFT);
        String payload = transaction.execute(status -> batches.findById(batchId).orElseThrow().getParsedPayload());
        assertThat(payload).isNull();
    }

    @Test
    void rowErrorsGiveARejectedBatchThatCannotBeConfirmedButHasAReport() {
        String category = fixture.category(fixture.activeCategory()).getName();
        byte[] file = xlsx(row("A", "Nhẫn A", "Mô tả", category, 4, "150000", 1),
                row("B", "Nhẫn B", "Mô tả", "Không có " + UUID.randomUUID(), 5, "1.5", 1));

        UUID batchId = UUID.fromString(imports.validate(STAFF, UUID.randomUUID(), "loi.xlsx", file).resourceId());

        ProductImportDtos.ImportBatch rejected = imports.get(STAFF, batchId.toString());
        assertThat(rejected.state()).isEqualTo("REJECTED");
        assertThat(rejected.errors()).extracting(ProductImportDtos.ImportError::code)
                .contains("OUT_OF_RANGE", "CATEGORY_NOT_ACTIVE_OR_UNKNOWN", "INVALID_NUMBER");
        assertThat(rejected.errorCount()).isEqualTo(rejected.errors().size());
        assertThat(rejected.preview()).isNull();
        assertCode(() -> imports.confirm(STAFF, UUID.randomUUID(), batchId.toString(), rejected.version()),
                CatalogueErrorCode.IMPORT_HAS_ERRORS);
        try (XSSFWorkbook report = new XSSFWorkbook(new ByteArrayInputStream(imports.errorReport(STAFF,
                batchId.toString())))) {
            assertThat(report.getSheetAt(0).getLastRowNum()).isEqualTo(rejected.errors().size());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void sameKeyAndFileReplayTheBatchAndAnotherFileUnderTheSameKeyIsRefused() {
        String category = fixture.category(fixture.activeCategory()).getName();
        byte[] file = xlsx(row("A", "Nhẫn A", "Mô tả", category, 5, "150000", 1));
        byte[] other = xlsx(row("A", "Nhẫn A", "Mô tả", category, 6, "150000", 1));
        UUID key = UUID.randomUUID();

        var first = imports.validate(STAFF, key, "a.xlsx", file);
        var replay = imports.validate(STAFF, key, "a.xlsx", file);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.resourceId()).isEqualTo(first.resourceId());
        assertCode(() -> imports.validate(STAFF, key, "a.xlsx", other), CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void aCategoryDeactivatedAfterThePreviewRejectsTheBatchAndCreatesNothing() {
        long categoryId = fixture.activeCategory();
        String category = fixture.category(categoryId).getName();
        byte[] file = xlsx(row("A", "Nhẫn A", "Mô tả", category, 5, "150000", 1));
        UUID batchId = UUID.fromString(imports.validate(STAFF, UUID.randomUUID(), "a.xlsx", file).resourceId());
        long version = imports.get(STAFF, batchId.toString()).version();
        categories.setActive(MANAGER, UUID.randomUUID(), Long.toString(categoryId),
                fixture.category(categoryId).getVersion(), false);
        long before = productCount();

        assertCode(() -> imports.confirm(STAFF, UUID.randomUUID(), batchId.toString(), version),
                CatalogueErrorCode.IMPORT_PREVIEW_STALE);

        ProductImportDtos.ImportBatch after = imports.get(STAFF, batchId.toString());
        assertThat(after.state()).isEqualTo("REJECTED");
        assertThat(after.errors()).extracting(ProductImportDtos.ImportError::code)
                .containsExactly("CATEGORY_NOT_ACTIVE_OR_UNKNOWN");
        assertThat(productCount()).isEqualTo(before);
    }

    @Test
    void anotherActorDoesNotSeeTheBatchAndAStaleVersionIsRefused() {
        String category = fixture.category(fixture.activeCategory()).getName();
        byte[] file = xlsx(row("A", "Nhẫn A", "Mô tả", category, 5, "150000", 1));
        UUID batchId = UUID.fromString(imports.validate(STAFF, UUID.randomUUID(), "a.xlsx", file).resourceId());
        long version = imports.get(STAFF, batchId.toString()).version();

        assertCode(() -> imports.get(MANAGER, batchId.toString()), CatalogueErrorCode.IMPORT_BATCH_NOT_FOUND);
        assertCode(() -> imports.confirm(MANAGER, UUID.randomUUID(), batchId.toString(), version),
                CatalogueErrorCode.IMPORT_BATCH_NOT_FOUND);
        assertCode(() -> imports.confirm(STAFF, UUID.randomUUID(), batchId.toString(), version + 1),
                CatalogueErrorCode.STALE_VERSION);
    }

    @Test
    void anExpiredBatchCannotBeConfirmedAndTheCleanerRemovesItsDataOnce() {
        Instant created = Instant.now().minus(Duration.ofMinutes(61)).truncatedTo(ChronoUnit.MICROS);
        UUID validatedId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            batches.saveAndFlush(ProductImportBatch.validated(validatedId, STAFF, "cu.xlsx", "a".repeat(64),
                    UUID.randomUUID(), 1, 1, 1, 0, "[]", "[]", created));
            batches.saveAndFlush(ProductImportBatch.validated(rejectedId, STAFF, "loi.xlsx", "b".repeat(64),
                    UUID.randomUUID(), 1, 0, 0, 1,
                    "[{\"row\":2,\"column\":\"Size\",\"value\":\"4\",\"code\":\"OUT_OF_RANGE\",\"message\":\"x\"}]",
                    null, created));
        });

        assertThat(imports.get(STAFF, validatedId.toString()).state()).isEqualTo("EXPIRED");
        assertThat(imports.get(STAFF, rejectedId.toString()).errors()).isEmpty();
        assertCode(() -> imports.confirm(STAFF, UUID.randomUUID(), validatedId.toString(), 0),
                CatalogueErrorCode.IMPORT_BATCH_EXPIRED);
        assertCode(() -> imports.errorReport(STAFF, rejectedId.toString()), CatalogueErrorCode.IMPORT_BATCH_EXPIRED);

        cleaner.cleanExpired(Instant.now());
        ProductImportBatch expired = transaction.execute(status -> batches.findById(validatedId).orElseThrow());
        ProductImportBatch cleanedRejected = transaction.execute(status -> batches.findById(rejectedId).orElseThrow());
        assertThat(expired.getState()).isEqualTo(ProductImportBatch.State.EXPIRED);
        assertThat(expired.getParsedPayload()).isNull();
        assertThat(cleanedRejected.getState()).isEqualTo(ProductImportBatch.State.REJECTED);
        assertThat(cleanedRejected.getErrors()).isNull();
        assertThat(cleanedRejected.getErrorCount()).isEqualTo(1);

        cleaner.cleanExpired(Instant.now());
        Long versionAfterSecondRun = transaction.execute(status -> batches.findById(validatedId).orElseThrow().getVersion());
        assertThat(versionAfterSecondRun).isEqualTo(expired.getVersion());
    }

    @Test
    void twoConfirmationsOfOneBatchAtTheSameTimeCreateTheProductsOnce() {
        String category = fixture.category(fixture.activeCategory()).getName();
        String name = "Đồng thời " + UUID.randomUUID();
        byte[] file = xlsx(row("A", name, "Mô tả", category, 5, "150000", 1),
                row("B", name + " B", "Mô tả", category, 6, "160000", 1));
        UUID batchId = UUID.fromString(imports.validate(STAFF, UUID.randomUUID(), "a.xlsx", file).resourceId());
        long version = imports.get(STAFF, batchId.toString()).version();

        List<Concurrently.Outcome<CatalogueCommandExecutor.Outcome>> outcomes = Concurrently.run(
                () -> imports.confirm(STAFF, UUID.randomUUID(), batchId.toString(), version),
                () -> imports.confirm(STAFF, UUID.randomUUID(), batchId.toString(), version));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome.value().body().get("state").asText()).isEqualTo("IMPORTED"));
        assertThat(productsNamedLike(name)).isEqualTo(2);
        assertThat(imports.get(STAFF, batchId.toString()).created()).hasSize(2);
    }

    @Test
    void twoDifferentBatchesConfirmedAtTheSameTimeBothSucceedWithDistinctProducts() {
        String category = fixture.category(fixture.activeCategory()).getName();
        String first = "Lô một " + UUID.randomUUID();
        String second = "Lô hai " + UUID.randomUUID();
        UUID firstBatch = UUID.fromString(imports.validate(STAFF, UUID.randomUUID(), "1.xlsx",
                xlsx(row("A", first, "Mô tả", category, 5, "150000", 1))).resourceId());
        UUID secondBatch = UUID.fromString(imports.validate(MANAGER, UUID.randomUUID(), "2.xlsx",
                xlsx(row("A", second, "Mô tả", category, 5, "150000", 1))).resourceId());

        List<Concurrently.Outcome<CatalogueCommandExecutor.Outcome>> outcomes = Concurrently.run(
                () -> imports.confirm(STAFF, UUID.randomUUID(), firstBatch.toString(),
                        imports.get(STAFF, firstBatch.toString()).version()),
                () -> imports.confirm(MANAGER, UUID.randomUUID(), secondBatch.toString(),
                        imports.get(MANAGER, secondBatch.toString()).version()));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        String firstProduct = imports.get(STAFF, firstBatch.toString()).created().get(0).productId();
        String secondProduct = imports.get(MANAGER, secondBatch.toString()).created().get(0).productId();
        assertThat(firstProduct).isNotEqualTo(secondProduct);
        assertThat(productsNamedLike(first)).isEqualTo(1);
        assertThat(productsNamedLike(second)).isEqualTo(1);
    }

    private long productsNamedLike(String prefix) {
        Long count = transaction.execute(status -> products.findAll().stream()
                .filter(product -> product.getName().startsWith(prefix)).count());
        return count == null ? 0 : count;
    }

    private long productCount() {
        Long count = transaction.execute(status -> products.count());
        return count == null ? 0 : count;
    }

    private static Object[] row(Object... values) {
        return values;
    }

    private static byte[] xlsx(Object[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sản phẩm");
            Row header = sheet.createRow(0);
            for (int i = 0; i < ImportColumn.ORDER.size(); i++) {
                header.createCell(i).setCellValue(ImportColumn.ORDER.get(i).header());
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable action, CatalogueErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
