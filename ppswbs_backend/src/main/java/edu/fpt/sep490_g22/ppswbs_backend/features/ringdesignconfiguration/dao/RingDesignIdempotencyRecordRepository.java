package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RingDesignIdempotencyRecordRepository extends JpaRepository<RingDesignIdempotencyRecord, Long> {

    Optional<RingDesignIdempotencyRecord> findByIdempotencyKeyAndCommandTypeAndActorScope(
            String idempotencyKey,
            String commandType,
            String actorScope
    );
}

