package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RetailVariantHoldRepository extends JpaRepository<RetailVariantHold, String> {

    interface HeldQuantity {
        Long getVariantId();
        Long getHeldQuantity();
    }

    @Query("select hold.variantId as variantId, sum(hold.quantity) as heldQuantity "
        + "from RetailVariantHold hold where hold.variantId in :variantIds and hold.status = :status "
        + "and hold.createdAt <= :asOf group by hold.variantId")
    List<HeldQuantity> sumValidHeldByVariantIds(@Param("variantIds") List<Long> variantIds,
                                                 @Param("status") RetailVariantHoldStatus status,
                                                 @Param("asOf") Instant asOf);

    Optional<RetailVariantHold> findByGroupedCheckoutIdAndVariantId(String groupedCheckoutId, Long variantId);
}
