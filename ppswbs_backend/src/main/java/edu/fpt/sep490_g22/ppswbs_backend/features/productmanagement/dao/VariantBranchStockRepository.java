package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VariantBranchStockRepository extends JpaRepository<VariantBranchStock, Long> {

    /** Rows of the given sizes, by size then branch, so callers see a stable order. */
    List<VariantBranchStock> findByVariantIdInOrderByVariantIdAscLocationIdAsc(Collection<Long> variantIds);

    @Query("select distinct s.locationId from VariantBranchStock s, ProductVariant v "
            + "where s.variantId = v.id and v.productId = :productId order by s.locationId")
    List<Long> findLocationIdsByProductId(@Param("productId") long productId);

    @Query("select distinct v.productId from VariantBranchStock s, ProductVariant v "
            + "where s.variantId = v.id and s.locationId = :locationId")
    List<Long> findProductIdsByLocationId(@Param("locationId") long locationId);
}
