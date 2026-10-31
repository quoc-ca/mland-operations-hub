package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Policy revisions are immutable; only inserts and reads. */
public interface LoyaltyPolicyRepository extends JpaRepository<LoyaltyPolicy, Long> {

    @Query("select coalesce(max(p.revisionNo), 0) from LoyaltyPolicy p")
    long findMaxRevisionNo();
}
