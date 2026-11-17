package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogueAuditRepository extends JpaRepository<CatalogueAuditEvent, Long> {

    List<CatalogueAuditEvent> findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc(String resourceType,
                                                                                     String resourceId);
}
