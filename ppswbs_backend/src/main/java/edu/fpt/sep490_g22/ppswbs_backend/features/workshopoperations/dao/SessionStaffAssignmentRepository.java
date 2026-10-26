package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionStaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionStaffAssignmentRepository extends JpaRepository<SessionStaffAssignment, Long> {

    List<SessionStaffAssignment> findBySessionIdAndActiveTrue(Long sessionId);

    Optional<SessionStaffAssignment> findBySessionIdAndPersonnelIdAndActiveTrue(Long sessionId, Long personnelId);

    boolean existsBySessionIdAndPersonnelIdAndActiveTrue(Long sessionId, Long personnelId);
}
