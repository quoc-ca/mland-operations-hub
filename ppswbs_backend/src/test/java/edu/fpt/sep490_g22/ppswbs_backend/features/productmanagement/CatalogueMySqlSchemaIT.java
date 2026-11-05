package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueCommandReceipt;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ManagementProductQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T017 evidence on the external MySQL 8.4 test database: Flyway applies 002's V15 (renumbered from V9 on develop, e143b52) after 001's V5 (upgrade
 * path), every catalogue entity round-trips, constraints are enforced by the database, and the create
 * (T025) and management query (T026) flows run against real tables. Rows use unique names so reruns on
 * the disposable test database never collide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
class CatalogueMySqlSchemaIT {

    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00.123456Z");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;
    @Autowired private EntityManager entityManager;
    @Autowired private ProductCategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private ProductReviewRepository reviews;
    @Autowired private PriceProposalRepository proposals;
    @Autowired private CatalogueMediaRepository media;
    @Autowired private ProductImageRepository images;
    @Autowired private MediaReferenceRepository references;
    @Autowired private CatalogueCommandReceiptRepository receipts;
    @Autowired private CatalogueAuditRepository audits;
    @Autowired private CreateProductService createProducts;
    @Autowired private ManagementProductQueryService query;

    @Test
    void migrationV15IsAppliedAfterV5WithAllTwelveTables() {
        CatalogueTestSupport.requireMySql84(dataSource);
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertThat(versions).containsSubsequence("5", "15");
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name LIKE 'catalogue\\_%' ORDER BY table_name", String.class);
        assertThat(tables).containsExactlyInAnyOrder("catalogue_audit_events", "catalogue_categories",
                "catalogue_command_receipts", "catalogue_import_batches", "catalogue_media_objects",
                "catalogue_media_references", "catalogue_price_applications", "catalogue_price_proposals",
                "catalogue_product_images", "catalogue_product_reviews", "catalogue_products", "catalogue_variants");
    }

    @Test
    void everyEntityRoundTripsWithExactValues() {
        String suffix = UUID.randomUUID().toString();
        Map<String, Object> ids = transaction.execute(status -> {
            ProductCategory category = categories.saveAndFlush(ProductCategory.create("Nhẫn " + suffix, "Mô tả"));
            Product product = products.saveAndFlush(Product.draft("Nhẫn bạc", "Mô tả", category.getId()));
            ProductVariant variant = variants.saveAndFlush(
                    ProductVariant.create(product.getId(), 12, new BigInteger("9999999999999999999")));
            ProductReview review = reviews.saveAndFlush(ProductReview.draft(product.getId(), 1, 0));
            review.submit("staff-1", NOW, "{\"name\":\"Nhẫn bạc\"}");
            PriceProposal proposal = PriceProposal.draft(variant.getId(), review.getId(), 1,
                    new BigInteger("1234567890123456789"), 0, "d".repeat(64), null);
            proposal.submit("staff-1", NOW);
            proposal.approve("manager-1", NOW, BigInteger.valueOf(150_000));
            proposals.saveAndFlush(proposal);
            variant.applyApprovedPrice(proposal.getId(), proposal.getApprovedAmount(), 0);

            UUID mediaId = UUID.randomUUID();
            CatalogueMediaObject object = CatalogueMediaObject.stage(mediaId, "test/" + mediaId, "c".repeat(64),
                    "image/webp", 1234, "staff-1", UUID.randomUUID(), NOW);
            object.markReady("asset-1", "17", "webp", NOW);
            media.saveAndFlush(object);
            images.saveAndFlush(ProductImage.attach(product.getId(), mediaId, "Mặt trước", 0));
            references.saveAndFlush(MediaReference.link(mediaId, "productmanagement", "product:" + product.getId(), NOW));
            receipts.saveAndFlush(new CatalogueCommandReceipt("staff-1", "product.create", UUID.randomUUID(),
                    "e".repeat(64), "product", product.getId().toString(), 201, 0L, "{\"productId\":\"1\"}", NOW));
            audits.saveAndFlush(new CatalogueAuditEvent("staff-1", "product.create", "product",
                    product.getId().toString(), 0L, 1L, List.of("name"), Map.of("source", "MANUAL"),
                    CatalogueAuditEvent.Outcome.SUCCESS, "corr-" + suffix, NOW));
            return Map.of("product", product.getId(), "variant", variant.getId(), "review", review.getId(),
                    "media", mediaId);
        });
        entityManager.clear();

        transaction.executeWithoutResult(status -> {
            ProductVariant variant = variants.findById((Long) ids.get("variant")).orElseThrow();
            assertThat(variant.getSku()).isEqualTo(String.format("SP%06d-S12", (Long) ids.get("product")));
            assertThat(variant.getRemainingUnsold()).isEqualTo(new BigInteger("9999999999999999999"));
            assertThat(variant.getAppliedPrice()).isEqualTo(BigInteger.valueOf(150_000));
            assertThat(variant.getAppliedSequence()).isEqualTo(1);

            ProductReview review = reviews.findById((Long) ids.get("review")).orElseThrow();
            assertThat(review.getState()).isEqualTo(ReviewState.PENDING);
            assertThat(review.getSubmittedAt()).isEqualTo(NOW);
            assertThat(review.getSnapshot()).contains("Nhẫn bạc");

            PriceProposal proposal = proposals.findByVariantIdOrderByRevisionAscIdAsc((Long) ids.get("variant")).get(0);
            assertThat(proposal.getProposedAmount()).isEqualTo(new BigInteger("1234567890123456789"));
            assertThat(proposal.getCurrency()).isEqualTo("VND");

            CatalogueMediaObject object = media.findById((UUID) ids.get("media")).orElseThrow();
            assertThat(object.getState()).isEqualTo(CatalogueMediaObject.State.READY);
            assertThat(images.findByProductIdOrderByDisplayPositionAsc((Long) ids.get("product")))
                    .singleElement().satisfies(image -> assertThat(image.getAltText()).isEqualTo("Mặt trước"));

            CatalogueAuditEvent event = audits.findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc(
                    "product", ids.get("product").toString()).get(0);
            assertThat(event.getSafeChanges()).containsEntry("source", "MANUAL");
            assertThat(event.getChangedFields()).containsExactly("name");
        });
    }

    @Test
    void categoryNameIsUniqueIgnoringCaseAndIncludingInactive() {
        String name = "Danh mục " + UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            ProductCategory category = categories.saveAndFlush(ProductCategory.create(name, null));
            category.setActive(false);
        });
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                categories.saveAndFlush(ProductCategory.create("  " + name.toUpperCase() + " ", null))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseChecksRejectInvalidRowsEvenWithoutTheApplication() {
        Long categoryId = transaction.execute(status ->
                categories.saveAndFlush(ProductCategory.create("Check " + UUID.randomUUID(), null)).getId());
        Long productId = transaction.execute(status ->
                products.saveAndFlush(Product.draft("Nhẫn", "Mô tả", categoryId)).getId());

        // MySQL reports CHECK violations as error 3819, which Spring does not classify as an integrity
        // violation, so the assertion checks the constraint name.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO catalogue_variants (product_id, ring_size, sku, "
                + "remaining_unsold, sale_enabled, applied_sequence, version) VALUES (?, 4, ?, 1, 1, 0, 0)",
                productId, "SPX-" + UUID.randomUUID().toString().substring(0, 8)))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chk_catalogue_variants_size");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO catalogue_variants (product_id, ring_size, sku, "
                + "remaining_unsold, sale_enabled, applied_sequence, version) VALUES (?, 12, ?, -1, 1, 0, 0)",
                productId, "SPY-" + UUID.randomUUID().toString().substring(0, 8)))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chk_catalogue_variants_quantity");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM catalogue_categories WHERE id = ?", categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createProductAndManagementQueryRunOnRealTables() {
        Long categoryId = transaction.execute(status ->
                categories.saveAndFlush(ProductCategory.create("Tạo " + UUID.randomUUID(), null)).getId());
        UUID commandId = UUID.randomUUID();
        var command = new CreateProductService.CreateProductCommand("Nhẫn tạo thật " + commandId, "Mô tả",
                categoryId.toString(), List.of(new CreateProductService.VariantInput(5, "150000", "3"),
                new CreateProductService.VariantInput(12, "180000", "0")), List.of());

        CatalogueCommandExecutor.Outcome first = createProducts.create(CatalogueTestSupport.STAFF, commandId, command);
        CatalogueCommandExecutor.Outcome replay = createProducts.create(CatalogueTestSupport.STAFF, commandId, command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.resourceId()).isEqualTo(first.resourceId());
        long productId = Long.parseLong(first.resourceId());
        assertThat(variants.findByProductIdOrderByRingSizeAsc(productId)).extracting(ProductVariant::getSku)
                .containsExactly(String.format("SP%06d-S05", productId), String.format("SP%06d-S12", productId));

        var page = query.list(String.format("SP%06d-S12", productId), null, null, 0);
        assertThat(page.items()).extracting(item -> item.id()).contains(Long.toString(productId));
        var detail = query.get(Long.toString(productId));
        assertThat(detail.reviews()).singleElement().satisfies(review -> assertThat(review.state()).isEqualTo("DRAFT"));
        assertThat(detail.variants()).allSatisfy(variant -> {
            assertThat(variant.validHeldQuantity()).isEqualTo("0");
            assertThat(variant.appliedPrice()).isNull();
            assertThat(variant.proposals()).singleElement()
                    .satisfies(proposal -> assertThat(proposal.state()).isEqualTo("DRAFT"));
        });
        assertThat(audits.findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc("product", first.resourceId()))
                .hasSize(1);
    }

    @Test
    void failedCreationLeavesNothingBehind() {
        Long categoryId = transaction.execute(status -> {
            ProductCategory category = categories.saveAndFlush(ProductCategory.create("Ngừng " + UUID.randomUUID(), null));
            category.setActive(false);
            return category.getId();
        });
        long before = products.count();
        var command = new CreateProductService.CreateProductCommand("Nhẫn", "Mô tả", categoryId.toString(),
                List.of(new CreateProductService.VariantInput(5, "1", "0")), List.of());

        assertThatThrownBy(() -> createProducts.create(CatalogueTestSupport.STAFF, UUID.randomUUID(), command))
                .isInstanceOf(RuntimeException.class);
        assertThat(products.count()).isEqualTo(before);
    }
}
