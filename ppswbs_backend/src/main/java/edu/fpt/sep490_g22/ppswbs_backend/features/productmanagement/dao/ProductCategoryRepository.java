package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
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

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    /** Active category for selection when creating or editing a product (US1). */
    Optional<ProductCategory> findByIdAndActiveTrue(Long id);

    /** Uniqueness check over all categories, including inactive ones. */
    boolean existsByNameKey(byte[] nameKey);

    Optional<ProductCategory> findByNameKey(byte[] nameKey);

    /** Active categories in a deterministic order (selection lists, import template). */
    List<ProductCategory> findByActiveTrueOrderByNameAscIdAsc();

    /** Active categories, paged, for the public filter (US8). */
    Page<ProductCategory> findAllByActiveTrueOrderByNameAscIdAsc(Pageable pageable);

    /** Every category, active or not, for category management (US7). */
    Page<ProductCategory> findAllByOrderByNameAscIdAsc(Pageable pageable);

    /** Pessimistic category gate, always in ascending ID order (CatalogueLockCoordinator). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProductCategory c where c.id in :ids order by c.id")
    List<ProductCategory> lockAllByIdIn(@Param("ids") Collection<Long> ids);
}
