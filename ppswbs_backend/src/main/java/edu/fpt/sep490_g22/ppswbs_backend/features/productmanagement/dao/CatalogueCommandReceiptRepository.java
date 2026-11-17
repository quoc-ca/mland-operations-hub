package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueCommandReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CatalogueCommandReceiptRepository extends JpaRepository<CatalogueCommandReceipt, Long> {

    Optional<CatalogueCommandReceipt> findByActorIdAndActionAndCommandId(String actorId, String action, UUID commandId);
}
