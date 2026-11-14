package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountAuditEventRepository extends JpaRepository<AccountAuditEvent, Long> {

    List<AccountAuditEvent> findByTargetAccountIdOrderByOccurredAtDesc(Long targetAccountId);
}
