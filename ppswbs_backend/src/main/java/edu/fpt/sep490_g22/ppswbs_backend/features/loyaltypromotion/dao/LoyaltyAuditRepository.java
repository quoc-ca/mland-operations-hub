package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Append-only audit and command receipts: only inserts and reads, never purged in V1. */
public interface LoyaltyAuditRepository extends JpaRepository<LoyaltyAudit, Long> {

    Optional<LoyaltyAudit> findByActorKeyAndActionAndCommandId(String actorKey, String action, String commandId);

    List<LoyaltyAudit> findByObjectRefOrderByIdAsc(String objectRef);
}
