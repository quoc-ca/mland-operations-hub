package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CancellationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CancellationHandoffStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CancellationHandoffRepository extends JpaRepository<CancellationHandoff, Long> {

    Optional<CancellationHandoff> findByCancellationCaseReference(String cancellationCaseReference);

    List<CancellationHandoff> findBySessionId(Long sessionId);

    List<CancellationHandoff> findBySessionIdAndStatus(Long sessionId, CancellationHandoffStatus status);
}
