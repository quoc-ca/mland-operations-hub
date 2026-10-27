package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceActionRepository extends JpaRepository<AttendanceAction, Long> {

    List<AttendanceAction> findByTicketIdAndSessionIdOrderByOccurredAtDesc(Long ticketId, Long sessionId);

    List<AttendanceAction> findBySessionIdOrderByOccurredAtDesc(Long sessionId);
}
