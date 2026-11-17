package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Tombstone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TombstoneRepository extends JpaRepository<Tombstone, Long> {

    Optional<Tombstone> findByOriginalRequestId(Long originalRequestId);
}

