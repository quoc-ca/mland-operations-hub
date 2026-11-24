package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.TabCounts;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ManagementProductQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ManagementProductQueryService.ListTab;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-064/FR-065 on MySQL (Session 2026-11-22): each product sits in exactly one tab and the counts match the tab search.
 * Every fixture product gets its own new category, so filtering by that category isolates it from other test data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueListTabsIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ManagementProductQueryService query;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private ProductReviewService reviews;
    @Autowired private ProductPublicationService publication;
    @Autowired private PriceProposalService proposals;

    @Test
    void aNewDraftIsInTheDraftTabOnly() {
        long productId = fixture.draftProduct(STAFF);
        assertOnlyIn(productId, ListTab.DRAFT);
    }

    @Test
    void aSubmittedProductIsWaitingForReview() {
        long productId = submitted();
        assertOnlyIn(productId, ListTab.PENDING);
    }

    @Test
    void aRejectedReviewIsInTheRejectedTab() {
        long productId = submitted();
        ProductReview review = fixture.currentReview(productId);
        reviews.decide(MANAGER, UUID.randomUUID(), Long.toString(productId),
                new ProductReviewService.DecisionCommand(review.getId().toString(), review.getRevision(),
                        review.getContentVersion(), ProductReviewService.Decision.REJECT, "Thiếu ảnh mặt trong", List.of()));
        assertOnlyIn(productId, ListTab.REJECTED);
    }

    @Test
    void anApprovedProductNotYetPublishedWaitsForPublication() {
        long productId = submitted();
        fixture.approveAll(productId, MANAGER);
        assertOnlyIn(productId, ListTab.APPROVED);
    }

    @Test
    void publishedAndHiddenProducts() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        assertOnlyIn(productId, ListTab.PUBLISHED);

        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId), false);
        assertOnlyIn(productId, ListTab.HIDDEN);
    }

    @Test
    void aPendingStandalonePriceIsCountedPerProposal() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        for (ProductVariant size : variants.findByProductIdOrderByRingSizeAsc(productId)) {
            long proposalId = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), size.getId().toString(),
                    size.getVersion(), "199000").resourceId());
            proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
        }
        String category = Long.toString(fixture.categoryOf(productId));

        TabCounts counts = query.tabCounts(null, category, null);
        assertThat(counts.published()).isEqualTo("1");
        assertThat(counts.pricePending()).isEqualTo("2"); // two sizes, one product
        assertThat(ids(productId, ListTab.PRICE_PENDING)).containsExactly(productId);
    }

    @Test
    void anUnsentStandalonePriceDraftIsCountedAndListedApart() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = variants.findByProductIdOrderByRingSizeAsc(productId).get(0);
        proposals.create(STAFF, UUID.randomUUID(), size.getId().toString(), size.getVersion(), "199000");
        String category = Long.toString(fixture.categoryOf(productId));

        TabCounts counts = query.tabCounts(null, category, null);
        assertThat(counts.priceDraft()).isEqualTo("1");
        assertThat(counts.pricePending()).isEqualTo("0");
        assertThat(counts.published()).isEqualTo("1"); // the product itself stays published
        assertThat(ids(productId, ListTab.PRICE_DRAFT)).containsExactly(productId);
        assertThat(ids(productId, ListTab.PRICE_PENDING)).isEmpty();
    }

    /** Sub-filter added 2026-11-23: a rejected standalone price is listed with its reason until the size gets a new proposal. */
    @Test
    void aRejectedStandalonePriceStaysUntilTheSizeGetsANewProposal() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = variants.findByProductIdOrderByRingSizeAsc(productId).get(0);
        long proposalId = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), size.getId().toString(),
                size.getVersion(), "199000").resourceId());
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
        proposals.decide(MANAGER, UUID.randomUUID(), Long.toString(proposalId), new PriceProposalService.DecisionCommand(
                fixture.proposal(proposalId).getVersion(), PriceProposalService.Decision.REJECT, null, "Giá thấp hơn giá vốn"));
        String category = Long.toString(fixture.categoryOf(productId));

        TabCounts counts = query.tabCounts(null, category, null);
        assertThat(counts.priceRejected()).isEqualTo("1");
        assertThat(counts.pricePending()).isEqualTo("0");
        assertThat(counts.rejected()).isEqualTo("0"); // the product tab stays for never-published reviews
        assertThat(counts.published()).isEqualTo("1");
        assertThat(ids(productId, ListTab.PRICE_REJECTED)).containsExactly(productId);

        ProductVariant current = variants.findById(size.getId()).orElseThrow();
        proposals.create(STAFF, UUID.randomUUID(), current.getId().toString(), current.getVersion(), "650000");
        counts = query.tabCounts(null, category, null);
        assertThat(counts.priceRejected()).isEqualTo("0");
        assertThat(counts.priceDraft()).isEqualTo("1");
        assertThat(ids(productId, ListTab.PRICE_REJECTED)).isEmpty();
    }

    /** T206: the search box narrows the tab list and every count alike (name, product number, SKU). */
    @Test
    void theKeywordNarrowsTabListsAndCounts() {
        long productId = publishedWithAPendingPrice();
        String category = Long.toString(fixture.categoryOf(productId));
        String name = products.findById(productId).orElseThrow().getName();
        String sku = variants.findByProductIdOrderByRingSizeAsc(productId).get(0).getSku();
        for (String q : List.of(name.substring("Nhẫn ".length(), "Nhẫn ".length() + 8), "SP%06d".formatted(productId), sku)) {
            TabCounts counts = query.tabCounts(q, category, null);
            assertThat(counts.all()).as(q).isEqualTo("1");
            assertThat(counts.published()).as(q).isEqualTo("1");
            assertThat(counts.pricePending()).as(q).isEqualTo("1");
            assertThat(listed(q, category, null, ListTab.PUBLISHED)).as(q).containsExactly(productId);
            assertThat(listed(q, category, null, ListTab.PRICE_PENDING)).as(q).containsExactly(productId);
        }

        String other = "không khớp " + UUID.randomUUID();
        TabCounts none = query.tabCounts(other, category, null);
        assertThat(List.of(none.all(), none.published(), none.pricePending(), none.priceDraft(), none.priceRejected()))
                .containsOnly("0");
        assertThat(listed(other, category, null, ListTab.PUBLISHED)).isEmpty();
        assertThat(listed(other, category, null, ListTab.PRICE_PENDING)).isEmpty();
    }

    /** T206 (FR-051): the branch filter narrows the tab list and every count to products sold at that branch. */
    @Test
    void theBranchFilterNarrowsTabListsAndCounts() {
        long productId = publishedWithAPendingPrice();
        String category = Long.toString(fixture.categoryOf(productId));
        String branch = Long.toString(fixture.testBranch());
        TabCounts atBranch = query.tabCounts(null, category, branch);
        assertThat(atBranch.all()).isEqualTo("1");
        assertThat(atBranch.published()).isEqualTo("1");
        assertThat(atBranch.pricePending()).isEqualTo("1");
        assertThat(listed(null, category, branch, ListTab.PUBLISHED)).containsExactly(productId);
        assertThat(listed(null, category, branch, ListTab.PRICE_PENDING)).containsExactly(productId);

        // A branch where the product is not sold (no stock row there): nothing is counted or listed.
        String elsewhere = Long.toString(fixture.testBranch() + 1_000_000L);
        TabCounts notThere = query.tabCounts(null, category, elsewhere);
        assertThat(List.of(notThere.all(), notThere.published(), notThere.pricePending())).containsOnly("0");
        assertThat(listed(null, category, elsewhere, ListTab.PUBLISHED)).isEmpty();
        assertThat(listed(null, category, elsewhere, ListTab.PRICE_PENDING)).isEmpty();
    }

    /** Published at the test branch, size 5 with a standalone price waiting for a decision. */
    private long publishedWithAPendingPrice() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        ProductVariant size = variants.findByProductIdOrderByRingSizeAsc(productId).get(0);
        long proposalId = Long.parseLong(proposals.create(STAFF, UUID.randomUUID(), size.getId().toString(),
                size.getVersion(), "199000").resourceId());
        proposals.submit(STAFF, UUID.randomUUID(), Long.toString(proposalId), fixture.proposal(proposalId).getVersion());
        return productId;
    }

    /** Ids of the tab list through the service, as {@code GET /products?tab=} reads it. */
    private List<Long> listed(String q, String category, String locationId, ListTab tab) {
        return query.list(q, category, null, 0, locationId, tab).items().stream().map(item -> Long.parseLong(item.id())).toList();
    }

    private long submitted() {
        long productId = fixture.draftProduct(STAFF);
        fixture.addReadyImage(productId, STAFF);
        fixture.submit(productId, STAFF);
        return productId;
    }

    /** The product is in {@code expected} and in no other state tab; counts agree with the tab search. */
    private void assertOnlyIn(long productId, ListTab expected) {
        TabCounts counts = query.tabCounts(null, Long.toString(fixture.categoryOf(productId)), null);
        assertThat(counts.all()).isEqualTo("1");
        assertThat(List.of(counts.draft(), counts.pending(), counts.rejected(), counts.approved(), counts.published(),
                counts.hidden())).containsExactly(
                expected == ListTab.DRAFT ? "1" : "0", expected == ListTab.PENDING ? "1" : "0",
                expected == ListTab.REJECTED ? "1" : "0", expected == ListTab.APPROVED ? "1" : "0",
                expected == ListTab.PUBLISHED ? "1" : "0", expected == ListTab.HIDDEN ? "1" : "0");
        for (ListTab tab : ListTab.values()) {
            if (tab != ListTab.PRICE_PENDING && tab != ListTab.PRICE_DRAFT && tab != ListTab.PRICE_REJECTED) {
                assertThat(ids(productId, tab)).as(tab.name()).isEqualTo(tab == expected ? List.of(productId) : List.of());
            }
        }
    }

    /** Tab search ids for the product's own category (ids only: the hold source is not needed). */
    private List<Long> ids(long productId, ListTab tab) {
        var filter = switch (tab) {
            case DRAFT -> new Object[] {null, false, ReviewState.DRAFT, true, false, ReviewState.PENDING};
            case PENDING -> new Object[] {null, false, ReviewState.PENDING, false, false, ReviewState.PENDING};
            case REJECTED -> new Object[] {null, false, ReviewState.REJECTED, false, false, ReviewState.PENDING};
            case APPROVED -> new Object[] {false, false, ReviewState.APPROVED, false, false, ReviewState.PENDING};
            case PUBLISHED -> new Object[] {true, null, null, false, false, ReviewState.PENDING};
            case HIDDEN -> new Object[] {false, true, null, false, false, ReviewState.PENDING};
            case PRICE_PENDING -> new Object[] {null, true, null, false, true, ReviewState.PENDING};
            case PRICE_DRAFT -> new Object[] {null, true, null, false, true, ReviewState.DRAFT};
            case PRICE_REJECTED -> new Object[] {null, true, null, false, true, ReviewState.REJECTED};
        };
        return products.searchManagementIdsByTab(null, fixture.categoryOf(productId), (Boolean) filter[0],
                (Boolean) filter[1], (ReviewState) filter[2], (boolean) filter[3], (boolean) filter[4],
                (ReviewState) filter[5], true, null, null, null, PageRequest.of(0, 20)).getContent();
    }
}
