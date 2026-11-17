package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRecipientRepository extends JpaRepository<TicketRecipient, Long> {
    Optional<TicketRecipient> findByTicketId(Long ticketId);
}
