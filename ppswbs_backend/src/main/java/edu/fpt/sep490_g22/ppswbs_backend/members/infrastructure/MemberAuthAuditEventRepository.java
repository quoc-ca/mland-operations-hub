package edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberAuthAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberAuthAuditEventRepository extends JpaRepository<MemberAuthAuditEventEntity, Long> {
}
