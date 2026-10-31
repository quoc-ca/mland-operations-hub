package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.CreateProductCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.ImageInput;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.VariantInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T025: draft creation core — sizes/SKU, draft review and proposals, images, audit, no partial save. */
class CreateProductServiceTests {

    private final AtomicLong ids = new AtomicLong(122);
    private ProductRepository products;
    private ProductVariantRepository variants;
    private ProductReviewRepository reviews;
    private PriceProposalRepository proposals;
    private ProductImageRepository images;
    private CatalogueLockCoordinator locks;
    private MediaReferenceService mediaReferences;
    private CatalogueAuditWriter audit;
    private CreateProductService service;
    private ProductCategory category;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        products = mock(ProductRepository.class);
        variants = mock(ProductVariantRepository.class);
        reviews = mock(ProductReviewRepository.class);
        proposals = mock(PriceProposalRepository.class);
        images = mock(ProductImageRepository.class);
        locks = mock(CatalogueLockCoordinator.class);
        mediaReferences = mock(MediaReferenceService.class);
        audit = mock(CatalogueAuditWriter.class);
        service = new CreateProductService(products, variants, reviews, proposals, images, locks, mediaReferences,
                audit, mock(CatalogueCommandExecutor.class), mock(ManagementProductQueryService.class));

        category = ProductCategory.create("Nhẫn bạc", null);
        setId(category, 5L);
        when(locks.lock(any())).thenAnswer(call -> new CatalogueLockCoordinator.Locked(
                Map.of(5L, category), Map.of(), Map.of()));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(reviews.saveAndFlush(any(ProductReview.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(variants.saveAllAndFlush(anyList())).thenAnswer(call -> {
            List<ProductVariant> saved = new ArrayList<>();
            for (Object variant : (Iterable<Object>) call.getArgument(0)) {
                saved.add((ProductVariant) withId(variant));
            }
            return saved;
        });
        when(proposals.saveAllAndFlush(anyList())).thenAnswer(call -> call.getArgument(0));
        when(images.saveAllAndFlush(anyList())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsDraftWithOneSkuAndOneDraftProposalPerSize() {
        long productId = service.createDraft("staff-1", command(List.of(
                new VariantInput(5, "150000", "3"), new VariantInput(12, "180000", "0")), List.of()),
                CreateProductService.Source.MANUAL);

        assertThat(productId).isEqualTo(123L);
        ArgumentCaptor<List<ProductVariant>> savedVariants = ArgumentCaptor.forClass(List.class);
        verify(variants).saveAllAndFlush(savedVariants.capture());
        assertThat(savedVariants.getValue()).extracting(ProductVariant::getSku)
                .containsExactly("SP000123-S05", "SP000123-S12");
        assertThat(savedVariants.getValue()).allSatisfy(variant -> {
            assertThat(variant.getAppliedPrice()).isNull();
            assertThat(variant.isSaleEnabled()).isTrue();
        });

        ArgumentCaptor<ProductReview> review = ArgumentCaptor.forClass(ProductReview.class);
        verify(reviews).saveAndFlush(review.capture());
        assertThat(review.getValue().getState()).isEqualTo(ReviewState.DRAFT);
        assertThat(review.getValue().getRevision()).isEqualTo(1);

        ArgumentCaptor<List<PriceProposal>> drafts = ArgumentCaptor.forClass(List.class);
        verify(proposals).saveAllAndFlush(drafts.capture());
        assertThat(drafts.getValue()).hasSize(2).allSatisfy(proposal -> {
            assertThat(proposal.getState()).isEqualTo(ReviewState.DRAFT);
            assertThat(proposal.getReviewId()).isEqualTo(review.getValue().getId());
            assertThat(proposal.getTermsDigest()).hasSize(64);
        });
        assertThat(drafts.getValue()).extracting(PriceProposal::getProposedAmount)
                .extracting(Object::toString).containsExactly("150000", "180000");

        verify(mediaReferences, never()).attachOwnedMedia(anyString(), anyString(), anyString(), anyList(), any());
        ArgumentCaptor<CatalogueAuditWriter.Entry> entry = ArgumentCaptor.forClass(CatalogueAuditWriter.Entry.class);
        verify(audit).recordSuccess(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo("product.create");
        assertThat(entry.getValue().safeChanges()).containsEntry("source", "MANUAL");
    }

    @Test
    void inactiveCategoryCreatesNothing() {
        category.setActive(false);
        assertCode(() -> service.createDraft("staff-1", command(List.of(new VariantInput(5, "1", "0")), List.of()),
                CreateProductService.Source.MANUAL), CatalogueErrorCode.CATEGORY_NOT_FOUND);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void invalidFieldIsReportedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service.createDraft("staff-1", command(List.of(
                        new VariantInput(5, "100", "1"), new VariantInput(6, "0100", "1")), List.of()),
                CreateProductService.Source.MANUAL))
                .isInstanceOfSatisfying(CatalogueException.class, error ->
                        assertThat(error.fieldErrors().get(0).field()).isEqualTo("variants[1].proposedPrice"));
        assertThatThrownBy(() -> service.createDraft("staff-1", command(List.of(
                        new VariantInput(5, "100", "1"), new VariantInput(5, "100", "1")), List.of()),
                CreateProductService.Source.MANUAL)).isInstanceOf(CatalogueException.class);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void duplicateImagePositionIsRejected() {
        List<ImageInput> twoAtZero = List.of(new ImageInput(UUID.randomUUID(), "Mặt trước", 0),
                new ImageInput(UUID.randomUUID(), "Mặt sau", 0));
        assertThatThrownBy(() -> service.createDraft("staff-1",
                command(List.of(new VariantInput(5, "1", "0")), twoAtZero), CreateProductService.Source.MANUAL))
                .isInstanceOfSatisfying(CatalogueException.class, error ->
                        assertThat(error.fieldErrors().get(0).field()).isEqualTo("images[1].position"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void imagesAreAttachedAsTheActorsOwnMedia() {
        UUID media = UUID.randomUUID();
        service.createDraft("staff-1", command(List.of(new VariantInput(5, "1", "0")),
                List.of(new ImageInput(media, " Mặt trước ", 0))), CreateProductService.Source.IMPORT);

        verify(mediaReferences).attachOwnedMedia(eq("staff-1"), eq("productmanagement"), eq("product:123"),
                eq(List.of(media)), any());
        ArgumentCaptor<List<ProductImage>> saved = ArgumentCaptor.forClass(List.class);
        verify(images).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue().get(0).getAltText()).isEqualTo("Mặt trước");
    }

    private CreateProductCommand command(List<VariantInput> variantInputs, List<ImageInput> imageInputs) {
        return new CreateProductCommand(" Nhẫn bạc trơn ", "Mô tả", "5", variantInputs, imageInputs);
    }

    private Object withId(Object entity) {
        setId(entity, ids.incrementAndGet());
        return entity;
    }

    private static void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertCode(Runnable action, CatalogueErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CatalogueException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
