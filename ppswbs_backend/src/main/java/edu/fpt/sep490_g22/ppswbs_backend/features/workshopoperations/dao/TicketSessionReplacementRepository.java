package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionReplacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketSessionReplacementRepository extends JpaRepository<TicketSessionReplacement, Long> {

    List<TicketSessionReplacement> findByTicketIdOrderByIdAsc(Long ticketId);
}
