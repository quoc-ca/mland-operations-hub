package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketRecipientChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRecipientChangeRepository extends JpaRepository<TicketRecipientChange, Long> {
    List<TicketRecipientChange> findByTicketIdOrderByChangedAtAsc(Long ticketId);
}
