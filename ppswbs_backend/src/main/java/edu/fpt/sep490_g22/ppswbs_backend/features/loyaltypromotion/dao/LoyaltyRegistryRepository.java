package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoyaltyRegistryRepository extends JpaRepository<LoyaltyRegistry, Long> {

    /** Shared lock: taken when a transaction is accepted, so the candidate programmes and policy stay stable. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from LoyaltyRegistry r where r.id = :id")
    Optional<LoyaltyRegistry> lockForRead(@Param("id") Long id);

    /** Exclusive lock: taken when a programme or the points policy changes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LoyaltyRegistry r where r.id = :id")
    Optional<LoyaltyRegistry> lockForWrite(@Param("id") Long id);
}
