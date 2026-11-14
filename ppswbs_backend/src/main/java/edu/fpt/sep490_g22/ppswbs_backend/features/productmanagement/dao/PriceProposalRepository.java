package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceProposalRepository extends JpaRepository<PriceProposal, Long> {

    List<PriceProposal> findByVariantIdOrderByRevisionAscIdAsc(Long variantId);

    /** Variant of a proposal without loading the entity, so the following lock reads fresh state. */
    @Query("select p.variantId from PriceProposal p where p.id = :id")
    Optional<Long> findVariantIdById(@Param("id") Long id);

    List<PriceProposal> findByVariantIdInOrderByVariantIdAscRevisionAscIdAsc(Collection<Long> variantIds);

    List<PriceProposal> findByVariantIdAndStateAndEffectiveTrue(Long variantId, ReviewState state);

    List<PriceProposal> findByReviewIdOrderByIdAsc(Long reviewId);

    Optional<PriceProposal> findFirstByVariantIdOrderByRevisionDescIdDesc(Long variantId);
}
