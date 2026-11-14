package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.entity.PickupEvidence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupEvidenceRepository extends JpaRepository<PickupEvidence, String> {
    Optional<PickupEvidence> findByOrderIdAndMilestone(String orderId, String milestone);
}
