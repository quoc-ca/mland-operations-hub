package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CancellationHandoffOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CancellationHandoffOutboxRepository extends JpaRepository<CancellationHandoffOutboxEvent, Long> {
    List<CancellationHandoffOutboxEvent> findTop50ByPublishedAtIsNullOrderByIdAsc();
}
