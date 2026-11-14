package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementTicketChangeRepository extends JpaRepository<SettlementTicketChange, Long> {

    Optional<SettlementTicketChange> findByTicketCodeAndIdempotencyKey(String ticketCode, String idempotencyKey);

    Optional<SettlementTicketChange> findByChangeReference(String changeReference);

    List<SettlementTicketChange> findByTicketCodeOrderByCreatedAtAsc(String ticketCode);

    long countByTicketCodeAndCountedTrueAndAppliedTrue(String ticketCode);

    long countByTicketCodeAndChangeTypeAndAppliedTrue(String ticketCode, String changeType);
}

