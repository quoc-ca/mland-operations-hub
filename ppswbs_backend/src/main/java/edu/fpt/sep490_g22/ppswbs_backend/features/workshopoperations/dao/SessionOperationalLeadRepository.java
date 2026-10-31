package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionOperationalLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionOperationalLeadRepository extends JpaRepository<SessionOperationalLead, Long> {

    Optional<SessionOperationalLead> findBySessionIdAndActiveTrue(Long sessionId);

    boolean existsBySessionIdAndManagerAccountIdAndActiveTrue(Long sessionId, Long managerAccountId);
}
