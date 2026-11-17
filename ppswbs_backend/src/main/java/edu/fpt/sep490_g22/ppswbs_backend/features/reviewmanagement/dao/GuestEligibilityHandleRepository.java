package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.GuestEligibilityHandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface GuestEligibilityHandleRepository extends JpaRepository<GuestEligibilityHandle, String> {

    Optional<GuestEligibilityHandle> findByHandleHash(String handleHash);

    @Modifying
    @Query("DELETE FROM GuestEligibilityHandle g WHERE g.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}

