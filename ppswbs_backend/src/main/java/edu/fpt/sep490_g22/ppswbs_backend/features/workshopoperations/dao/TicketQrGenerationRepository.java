package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketQrGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketQrGenerationRepository extends JpaRepository<TicketQrGeneration, Long> {

    boolean existsByQrReference(String qrReference);
}
