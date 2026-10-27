package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderByRingSizeAsc(Long productId);

    List<ProductVariant> findByProductIdInOrderByProductIdAscRingSizeAsc(Collection<Long> productIds);

    Optional<ProductVariant> findBySku(String sku);

    /** Product of a variant without loading the entity, so the following lock reads fresh state. */
    @Query("select v.productId from ProductVariant v where v.id = :id")
    Optional<Long> findProductIdById(@Param("id") Long id);

    /** Pessimistic variant gate, always in ascending ID order (CatalogueLockCoordinator). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id in :ids order by v.id")
    List<ProductVariant> lockAllByIdIn(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.productId in :productIds order by v.id")
    List<ProductVariant> lockAllByProductIdIn(@Param("productIds") Collection<Long> productIds);
}
