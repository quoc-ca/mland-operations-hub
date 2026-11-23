package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Normal (non-deleted) product; deleted products are excluded from normal reads. */
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Management search (FR-003, A-002): non-deleted products, optional category and publication filters,
     * and a keyword that matches a name substring, the product number or an exact variant SKU.
     * Newest first by creation time, then ID, so paging is deterministic. Returns IDs only; details are
     * batch-loaded afterwards so paging never runs over a fetch join.
     */
    @Query(value = """
            select p.id from Product p
            where p.deletedAt is null
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:published is null or p.published = :published)
              and (:anyKeyword = true
                   or lower(p.name) like :namePattern escape '!'
                   or p.id = :productNumber
                   or p.id in (select v.productId from ProductVariant v where v.sku = :sku))
            order by p.createdAt desc, p.id desc""",
            countQuery = """
            select count(p) from Product p
            where p.deletedAt is null
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:published is null or p.published = :published)
              and (:anyKeyword = true
                   or lower(p.name) like :namePattern escape '!'
                   or p.id = :productNumber
                   or p.id in (select v.productId from ProductVariant v where v.sku = :sku))""")
    Page<Long> searchManagementIds(@Param("categoryId") Long categoryId,
                                   @Param("published") Boolean published,
                                   @Param("anyKeyword") boolean anyKeyword,
                                   @Param("namePattern") String namePattern,
                                   @Param("productNumber") Long productNumber,
                                   @Param("sku") String sku,
                                   Pageable pageable);

    /** {@link #searchManagementIds} limited to products sold at one branch (US10, FR-051). */
    @Query(value = """
            select p.id from Product p
            where p.deletedAt is null
              and p.id in (select v.productId from ProductVariant v, VariantBranchStock s
                           where s.variantId = v.id and s.locationId = :locationId)
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:published is null or p.published = :published)
              and (:anyKeyword = true
                   or lower(p.name) like :namePattern escape '!'
                   or p.id = :productNumber
                   or p.id in (select v.productId from ProductVariant v where v.sku = :sku))
            order by p.createdAt desc, p.id desc""",
            countQuery = """
            select count(p) from Product p
            where p.deletedAt is null
              and p.id in (select v.productId from ProductVariant v, VariantBranchStock s
                           where s.variantId = v.id and s.locationId = :locationId)
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:published is null or p.published = :published)
              and (:anyKeyword = true
                   or lower(p.name) like :namePattern escape '!'
                   or p.id = :productNumber
                   or p.id in (select v.productId from ProductVariant v where v.sku = :sku))""")
    Page<Long> searchManagementIdsAtBranch(@Param("locationId") Long locationId,
                                           @Param("categoryId") Long categoryId,
                                           @Param("published") Boolean published,
                                           @Param("anyKeyword") boolean anyKeyword,
                                           @Param("namePattern") String namePattern,
                                           @Param("productNumber") Long productNumber,
                                           @Param("sku") String sku,
                                           Pageable pageable);

    /**
     * FR-064/FR-065 list filter shared by the tab search and the tab counts (Session 2026-11-22). Every parameter is
     * optional except the booleans: {@code everPublished} splits never/ever published, {@code reviewState} matches the
     * current review (the effective one with the highest revision), {@code draftIfNoReview} also matches a product with
     * no effective review, {@code pricePending} keeps products with an effective standalone price proposal in {@code priceState} (PENDING, DRAFT for the
     * Staff "not sent yet" sub-filter, REJECTED for the Staff "rejected" sub-filter; a rejected proposal stays effective
     * until the next proposal of its size replaces it).
     */
    String TAB_FILTER = """
            p.deletedAt is null
              and (:locationId is null
                   or p.id in (select sv.productId from ProductVariant sv, VariantBranchStock s
                               where s.variantId = sv.id and s.locationId = :locationId))
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:published is null or p.published = :published)
              and (:everPublished is null
                   or (:everPublished = true and p.firstPublishedAt is not null)
                   or (:everPublished = false and p.firstPublishedAt is null))
              and (:reviewState is null
                   or exists (select r.id from ProductReview r
                              where r.productId = p.id and r.effective = true and r.state = :reviewState
                                and r.revision = (select max(r2.revision) from ProductReview r2
                                                  where r2.productId = p.id and r2.effective = true))
                   or (:draftIfNoReview = true
                       and not exists (select r3.id from ProductReview r3
                                       where r3.productId = p.id and r3.effective = true)))
              and (:pricePending = false
                   or p.id in (select pv.productId from ProductVariant pv, PriceProposal pp
                               where pp.variantId = pv.id and pp.effective = true and pp.state = :priceState
                                 and pp.reviewId is null))
              and (:anyKeyword = true
                   or lower(p.name) like :namePattern escape '!'
                   or p.id = :productNumber
                   or p.id in (select kv.productId from ProductVariant kv where kv.sku = :sku))""";

    /** Management search with an FR-064 tab; same order as {@link #searchManagementIds}. */
    @Query(value = "select p.id from Product p where " + TAB_FILTER + " order by p.createdAt desc, p.id desc",
            countQuery = "select count(p) from Product p where " + TAB_FILTER)
    Page<Long> searchManagementIdsByTab(@Param("locationId") Long locationId,
                                        @Param("categoryId") Long categoryId,
                                        @Param("published") Boolean published,
                                        @Param("everPublished") Boolean everPublished,
                                        @Param("reviewState") ReviewState reviewState,
                                        @Param("draftIfNoReview") boolean draftIfNoReview,
                                        @Param("pricePending") boolean pricePending,
                                        @Param("priceState") ReviewState priceState,
                                        @Param("anyKeyword") boolean anyKeyword,
                                        @Param("namePattern") String namePattern,
                                        @Param("productNumber") Long productNumber,
                                        @Param("sku") String sku,
                                        Pageable pageable);

    /** Number of products of one FR-064 tab (no tab filter = every non-deleted product). */
    @Query("select count(p) from Product p where " + TAB_FILTER)
    long countManagementByTab(@Param("locationId") Long locationId,
                              @Param("categoryId") Long categoryId,
                              @Param("published") Boolean published,
                              @Param("everPublished") Boolean everPublished,
                              @Param("reviewState") ReviewState reviewState,
                              @Param("draftIfNoReview") boolean draftIfNoReview,
                              @Param("pricePending") boolean pricePending,
                              @Param("priceState") ReviewState priceState,
                              @Param("anyKeyword") boolean anyKeyword,
                              @Param("namePattern") String namePattern,
                              @Param("productNumber") Long productNumber,
                              @Param("sku") String sku);

    /** FR-065: effective standalone price proposals in {@code priceState} of ever-published products matching the list filters. */
    @Query("""
            select count(cp) from PriceProposal cp, ProductVariant cv, Product p
            where cp.variantId = cv.id and cv.productId = p.id
              and cp.effective = true and cp.state = :priceState and cp.reviewId is null
              and p.firstPublishedAt is not null
              and """ + " " + TAB_FILTER)
    long countPriceProposals(@Param("locationId") Long locationId,
                                    @Param("categoryId") Long categoryId,
                                    @Param("published") Boolean published,
                                    @Param("everPublished") Boolean everPublished,
                                    @Param("reviewState") ReviewState reviewState,
                                    @Param("draftIfNoReview") boolean draftIfNoReview,
                                    @Param("pricePending") boolean pricePending,
                                    @Param("priceState") ReviewState priceState,
                                    @Param("anyKeyword") boolean anyKeyword,
                                    @Param("namePattern") String namePattern,
                                    @Param("productNumber") Long productNumber,
                                    @Param("sku") String sku);

    /** Category of a product without loading the entity, so the following lock reads fresh state. */
    @Query("select p.categoryId from Product p where p.id = :id")
    Optional<Long> findCategoryIdById(@Param("id") Long id);

    /** Pessimistic product gate, always in ascending ID order (CatalogueLockCoordinator). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllByIdIn(@Param("ids") Collection<Long> ids);

    /** 006 sample programmes (PromotionSeedCataloguePort): products not deleted in the category of this name. */
    @Query("""
            select p.id from Product p, ProductCategory c
            where c.id = p.categoryId and c.name = :categoryName and p.deletedAt is null
            order by p.id""")
    List<Long> findLiveIdsByCategoryName(@Param("categoryName") String categoryName);

    /** 006 sample programmes: products not deleted with this name. */
    @Query("select p.id from Product p where p.name = :name and p.deletedAt is null order by p.id")
    List<Long> findLiveIdsByName(@Param("name") String name);

    /** 006 sample programmes: published products not deleted in the category of this name. */
    @Query("""
            select count(p) from Product p, ProductCategory c
            where c.id = p.categoryId and c.name = :categoryName and p.deletedAt is null and p.published = true""")
    long countPublishedByCategoryName(@Param("categoryName") String categoryName);
}
