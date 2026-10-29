package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtaReconciliationWorkItemRepository extends JpaRepository<OtaReconciliationWorkItem, Long> {
    List<OtaReconciliationWorkItem> findByLocationReferenceAndStatusOrderByCreatedAtAsc(String locationReference, String status);

    List<OtaReconciliationWorkItem> findByAssignedManagerReferenceAndStatusOrderByCreatedAtAsc(String managerReference, String status);
}
