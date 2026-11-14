package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketEntitlementRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketEntitlementReleaseRepository extends JpaRepository<TicketEntitlementRelease, Long> {

    List<TicketEntitlementRelease> findByTicketIdOrderByIdAsc(Long ticketId);
}
