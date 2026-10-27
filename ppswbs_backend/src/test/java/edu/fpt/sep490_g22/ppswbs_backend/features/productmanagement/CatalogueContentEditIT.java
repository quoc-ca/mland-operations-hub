package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.ImageInput;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaReferenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicCatalogueQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.UpdateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T043/T045/T046 on MySQL: content edit, image replacement and quantity before and after the first
 * publication (FR-005/008/011/012/019). Hold-source cases need FE-08 (T042); role checks need T007.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueContentEditIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private UpdateProductService updates;
    @Autowired private ProductImageService imageService;
    @Autowired private VariantQuantityService quantities;
    @Autowired private PublicCatalogueQueryService publicCatalogue;
    @Autowired private DeleteProductService deletion;
    @Autowired private ProductCategoryService categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private MediaReferenceRepository references;
    @Autowired private TransactionTemplate transaction;

    // ---- T043 content edit ----

    @Test
    void editingAnApprovedDraftWithdrawsTheReview() {
        long productId = approvedDraft();
        ProductReview approved = fixture.currentReview(productId);
        Product before = product(productId);

        var outcome = updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), before.getVersion(),
                new UpdateProductService.UpdateCommand("Tên mới " + UUID.randomUUID(), "Mô tả mới",
                        before.getCategoryId().toString()));

        assertThat(outcome.body().get("changed").asBoolean()).isTrue();
        assertThat(product(productId).getContentVersion()).isEqualTo(before.getContentVersion() + 1);
        ProductReview current = fixture.currentReview(productId);
        assertThat(current.getRevision()).isEqualTo(approved.getRevision() + 1);
        assertThat(current.getState()).isEqualTo(ReviewState.DRAFT);
    }

    @Test
    void afterTheFirstPublicationTheEditIsDirectAndTheProductStaysPublished() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductReview approved = fixture.currentReview(productId);
        long target = fixture.activeCategory();

        updates.update(MANAGER, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                new UpdateProductService.UpdateCommand("Đã sửa " + UUID.randomUUID(), "Mô tả", Long.toString(target)));

        Product after = product(productId);
        assertThat(after.isPublished()).isTrue();
        assertThat(after.getCategoryId()).isEqualTo(target);
        ProductReview current = fixture.currentReview(productId);
        assertThat(current.getId()).isEqualTo(approved.getId());
        assertThat(current.getState()).isEqualTo(ReviewState.APPROVED);
    }

    @Test
    void movingToAnInactiveCategoryIsRefusedAndTheSameContentIsANoOp() {
        long productId = fixture.draftProduct(STAFF);
        long inactive = fixture.activeCategory();
        categories.setActive(STAFF, UUID.randomUUID(), Long.toString(inactive), fixture.category(inactive).getVersion(),
                false);
        Product before = product(productId);

        assertCode(() -> updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), before.getVersion(),
                        new UpdateProductService.UpdateCommand(before.getName(), before.getDescription(),
                                Long.toString(inactive))),
                CatalogueErrorCode.CATEGORY_NOT_FOUND);
        assertCode(() -> updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), before.getVersion() + 1,
                        new UpdateProductService.UpdateCommand("X", "Y", before.getCategoryId().toString())),
                CatalogueErrorCode.STALE_VERSION);
        var same = updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), before.getVersion(),
                new UpdateProductService.UpdateCommand("  " + before.getName() + " ", before.getDescription(),
                        before.getCategoryId().toString()));
        assertThat(same.body().get("changed").asBoolean()).isFalse();
        assertThat(product(productId).getContentVersion()).isEqualTo(before.getContentVersion());
    }

    @Test
    void aDeletedProductCannotBeEdited() {
        long productId = fixture.draftProduct(STAFF);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));
        Product deleted = product(productId);

        assertCode(() -> updates.update(STAFF, UUID.randomUUID(), Long.toString(productId), deleted.getVersion(),
                        new UpdateProductService.UpdateCommand("X", "Y", deleted.getCategoryId().toString())),
                CatalogueErrorCode.PRODUCT_DELETED);
    }

    // ---- T045 images ----

    @Test
    void replacingImagesReordersAttachesAndReleasesWithoutLosingTheMedia() {
        long productId = fixture.draftProduct(STAFF);
        UUID first = fixture.readyMedia(STAFF, Instant.now());
        UUID second = fixture.readyMedia(STAFF, Instant.now());
        UUID third = fixture.readyMedia(STAFF, Instant.now());
        replace(productId, STAFF, List.of(new ImageInput(first, "Một", 0), new ImageInput(second, "Hai", 1)));

        // Swap positions and replace "first" by "third" in one command.
        replace(productId, STAFF, List.of(new ImageInput(second, "Hai", 0), new ImageInput(third, "Ba", 1)));

        assertThat(fixture.images(productId)).extracting(ProductImage::getMediaObjectId).containsExactly(second, third);
        MediaReference released = reference(first, productId);
        assertThat(released.getReleasedAt()).isNotNull();
        assertThat(fixture.media(first).getLastUnreferencedAt()).isNotNull();
        assertThat(reference(third, productId).getReleasedAt()).isNull();
        assertThat(product(productId).getContentVersion()).isEqualTo(2);
    }

    @Test
    void anotherUsersUploadCannotBeAttachedAndAPublishedProductKeepsAnImage() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        UUID foreign = fixture.readyMedia(MANAGER, Instant.now());
        List<ImageInput> current = fixture.images(productId).stream()
                .map(image -> new ImageInput(image.getMediaObjectId(), image.getAltText(), image.getDisplayPosition()))
                .toList();

        assertCode(() -> replace(productId, STAFF, List.of(current.get(0), new ImageInput(foreign, "Lạ", 1))),
                CatalogueErrorCode.MEDIA_NOT_FOUND);
        assertCode(() -> replace(productId, STAFF, List.of()), CatalogueErrorCode.PUBLICATION_PREREQUISITES_MISSING);
        assertThat(fixture.images(productId)).hasSize(1);
        assertThat(product(productId).isPublished()).isTrue();
    }

    @Test
    void changingImagesOfAnApprovedDraftWithdrawsTheReview() {
        long productId = approvedDraft();
        ProductReview approved = fixture.currentReview(productId);
        UUID extra = fixture.readyMedia(STAFF, Instant.now());
        List<ImageInput> next = new java.util.ArrayList<>(fixture.images(productId).stream()
                .map(image -> new ImageInput(image.getMediaObjectId(), image.getAltText(), image.getDisplayPosition()))
                .toList());
        next.add(new ImageInput(extra, "Mặt sau", 1));

        replace(productId, STAFF, next);

        assertThat(fixture.currentReview(productId).getRevision()).isEqualTo(approved.getRevision() + 1);
        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.DRAFT);
    }

    // ---- T046 quantity ----

    @Test
    void quantityOfANeverPublishedProductChangesWithoutTouchingTheReview() {
        long productId = approvedDraft();
        ProductReview approved = fixture.currentReview(productId);
        ProductVariant size5 = sizes(productId).get(0);

        var outcome = quantities.setQuantity(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(), "10");

        assertThat(outcome.body().get("changed").asBoolean()).isTrue();
        assertThat(sizes(productId).get(0).getRemainingUnsold()).isEqualTo(BigInteger.TEN);
        assertThat(fixture.currentReview(productId).getId()).isEqualTo(approved.getId());
        assertThat(fixture.currentReview(productId).getState()).isEqualTo(ReviewState.APPROVED);
        assertCode(() -> quantities.setQuantity(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(),
                "8"), CatalogueErrorCode.STALE_VERSION);
    }

    @Test
    void quantityOfAPublishedProductNeedsTheHoldSourceAndChangesNothingWithoutIt() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size5 = sizes(productId).get(0);

        assertCode(() -> quantities.setQuantity(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(),
                "1"), CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
        assertThat(sizes(productId).get(0).getRemainingUnsold()).isEqualTo(BigInteger.valueOf(3));
    }

    // ---- T067 without the FE-08 source (this context has no VariantHoldReader) ----

    @Test
    void publicReadsFailAsUnavailableInsteadOfAssumingZeroHolds() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);

        assertCode(() -> publicCatalogue.get(Long.toString(productId)),
                CatalogueErrorCode.AVAILABILITY_DEPENDENCY_UNAVAILABLE);
    }

    private long approvedDraft() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        fixture.approveAll(productId, MANAGER);
        return productId;
    }

    private void replace(long productId, String actorId, List<ImageInput> images) {
        imageService.replace(actorId, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                images);
    }

    private MediaReference reference(UUID mediaId, long productId) {
        return transaction.execute(status -> references.findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(mediaId,
                MediaReferenceService.OWNER_MODULE, MediaReferenceService.productRef(productId)).orElseThrow());
    }

    private Product product(long productId) {
        return transaction.execute(status -> products.findById(productId).orElseThrow());
    }

    private List<ProductVariant> sizes(long productId) {
        return transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId));
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable action, CatalogueErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
