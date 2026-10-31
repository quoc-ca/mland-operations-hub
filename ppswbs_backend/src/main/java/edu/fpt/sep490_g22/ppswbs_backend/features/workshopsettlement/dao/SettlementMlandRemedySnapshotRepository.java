package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementMlandRemedySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementMlandRemedySnapshotRepository extends JpaRepository<SettlementMlandRemedySnapshot, Long> {
    Optional<SettlementMlandRemedySnapshot> findByBookingReference(String bookingReference);
}
