package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketPriceSnapshotRepository extends JpaRepository<TicketPriceSnapshot, Long> {
    Optional<TicketPriceSnapshot> findByTicketId(Long ticketId);
}
