package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestDesignSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface GuestDesignSessionRepository extends JpaRepository<GuestDesignSession, Long> {

    Optional<GuestDesignSession> findBySessionReference(String sessionReference);

    Optional<GuestDesignSession> findBySessionDigest(String sessionDigest);

    @Modifying
    @Query("UPDATE GuestDesignSession g SET g.status = :newStatus WHERE g.status = :activeStatus AND g.expiresAt < :now")
    int expireStaleSessions(
            @Param("activeStatus") GuestSessionStatus activeStatus,
            @Param("newStatus") GuestSessionStatus newStatus,
            @Param("now") Instant now
    );
}

