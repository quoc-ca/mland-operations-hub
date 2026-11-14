package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.OperationalAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationalAuditRepository extends JpaRepository<OperationalAuditRecord, Long> {

    List<OperationalAuditRecord> findByNotificationRequestIdOrderByCreatedAtAsc(Long notificationRequestId);
}

