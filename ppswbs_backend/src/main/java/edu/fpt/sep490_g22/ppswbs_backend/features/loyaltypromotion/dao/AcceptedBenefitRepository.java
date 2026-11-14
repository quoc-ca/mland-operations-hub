package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigInteger;
import java.util.Optional;

public interface AcceptedBenefitRepository extends JpaRepository<AcceptedBenefit, Long> {

    Optional<AcceptedBenefit> findBySourceKindAndSourceRef(SourceKind sourceKind, String sourceRef);

    /** Immutable references of a benefit, read without loading the entity (it is locked later, in lock order). */
    interface References {
        Long getMemberId();

        Long getProgramId();

        java.math.BigInteger getRequestedPoints();
    }

    @Query("""
            select b.memberId as memberId, b.programId as programId, b.requestedPoints as requestedPoints
            from AcceptedBenefit b where b.sourceKind = :sourceKind and b.sourceRef = :sourceRef""")
    Optional<References> findReferences(@Param("sourceKind") SourceKind sourceKind, @Param("sourceRef") String sourceRef);

    /** Programme discount of uses that are consumed and not given back (validly cancelled ones are RESTORED). */
    @Query("""
            select sum(b.programDiscount) from AcceptedBenefit b
            where b.programId = :programId
              and b.voucherState = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState.CONSUMED""")
    BigInteger sumConsumedVoucherDiscount(@Param("programId") Long programId);

    /** Settled transactions of an automatic discount (it has no voucher component and no cancellation path). */
    @Query("select count(b) from AcceptedBenefit b where b.programId = :programId and b.settledAt is not null")
    long countSettled(@Param("programId") Long programId);

    @Query("""
            select sum(b.programDiscount) from AcceptedBenefit b
            where b.programId = :programId and b.settledAt is not null""")
    BigInteger sumSettledDiscount(@Param("programId") Long programId);

    /** Exclusive lock on the accepted benefit (lock order step 6, the last one). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AcceptedBenefit b where b.sourceKind = :sourceKind and b.sourceRef = :sourceRef")
    Optional<AcceptedBenefit> lockBySource(@Param("sourceKind") SourceKind sourceKind,
                                           @Param("sourceRef") String sourceRef);
}
