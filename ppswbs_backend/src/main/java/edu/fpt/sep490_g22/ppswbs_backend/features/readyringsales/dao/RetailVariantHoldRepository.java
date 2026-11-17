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
import org.springframework.data.domain.Pageable;

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

    interface BranchHeldQuantity {
        Long getVariantId();
        Long getLocationId();
        Long getHeldQuantity();
    }

    /** Valid held quantity per size x branch (FR-053); location 0 rows are legacy size-level holds. */
    @Query("select hold.variantId as variantId, hold.locationId as locationId, sum(hold.quantity) as heldQuantity "
        + "from RetailVariantHold hold where hold.variantId in :variantIds and hold.status = :status "
        + "and hold.createdAt <= :asOf group by hold.variantId, hold.locationId")
    List<BranchHeldQuantity> sumValidHeldByVariantAndLocation(@Param("variantIds") List<Long> variantIds,
                                                              @Param("status") RetailVariantHoldStatus status,
                                                              @Param("asOf") Instant asOf);

    Optional<RetailVariantHold> findByGroupedCheckoutIdAndVariantIdAndLocationId(String groupedCheckoutId, Long variantId,
                                                                                Long locationId);

    /** Legacy size-level lookup; ambiguous once a checkout holds the same size at several branches. */
    Optional<RetailVariantHold> findByGroupedCheckoutIdAndVariantId(String groupedCheckoutId, Long variantId);
    List<RetailVariantHold> findByGroupedCheckoutId(String groupedCheckoutId);

    interface ExpiryCandidate {
        String getCheckoutId();
        Long getMemberId();
    }

    @Query("select distinct hold.groupedCheckout.id as checkoutId, hold.groupedCheckout.memberAccountId as memberId "
        + "from RetailVariantHold hold where hold.status = :holdStatus and hold.expiresAt <= :now "
        + "and hold.groupedCheckout.paymentState = :paymentState and hold.groupedCheckout.paymentInitiatedAt is null "
        + "order by hold.groupedCheckout.id")
    List<ExpiryCandidate> findUnstartedExpiryCandidates(@Param("holdStatus") RetailVariantHoldStatus holdStatus,
        @Param("paymentState") edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState paymentState,
        @Param("now") Instant now, Pageable page);

    @Query("select distinct hold.groupedCheckout.id as checkoutId, hold.groupedCheckout.memberAccountId as memberId "
        + "from RetailVariantHold hold where hold.status = :holdStatus and hold.expiresAt <= :now "
        + "and hold.groupedCheckout.paymentState = :paymentState and hold.groupedCheckout.paymentInitiatedAt is not null "
        + "order by hold.groupedCheckout.id")
    List<ExpiryCandidate> findStartedExpiryCandidates(@Param("holdStatus") RetailVariantHoldStatus holdStatus,
        @Param("paymentState") edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState paymentState,
        @Param("now") Instant now, Pageable page);
}
