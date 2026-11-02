package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
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

    /** Category of a product without loading the entity, so the following lock reads fresh state. */
    @Query("select p.categoryId from Product p where p.id = :id")
    Optional<Long> findCategoryIdById(@Param("id") Long id);

    /** Pessimistic product gate, always in ascending ID order (CatalogueLockCoordinator). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllByIdIn(@Param("ids") Collection<Long> ids);
}
