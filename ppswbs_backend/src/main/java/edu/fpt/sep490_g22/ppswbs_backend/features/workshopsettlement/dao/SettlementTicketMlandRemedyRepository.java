package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketMlandRemedy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementTicketMlandRemedyRepository extends JpaRepository<SettlementTicketMlandRemedy, Long> {

    Optional<SettlementTicketMlandRemedy> findByRemedyReference(String remedyReference);

    Optional<SettlementTicketMlandRemedy> findByTicketCodeAndOutcome(String ticketCode, String outcome);

    List<SettlementTicketMlandRemedy> findByTicketCodeOrderByCreatedAtDesc(String ticketCode);

    List<SettlementTicketMlandRemedy> findByOutcomeAndAnswerDeadlineAtLessThanEqual(String outcome, Instant deadline);

    Optional<SettlementTicketMlandRemedy> findFirstByTicketCodeAndCancelledSessionReferenceOrderByCreatedAtDesc(
            String ticketCode, String cancelledSessionReference);

    boolean existsByTicketCodeAndOutcome(String ticketCode, String outcome);
}

