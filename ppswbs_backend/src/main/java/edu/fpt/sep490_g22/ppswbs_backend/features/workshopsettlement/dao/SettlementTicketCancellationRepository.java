package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementTicketCancellationRepository extends JpaRepository<SettlementTicketCancellation, Long> {

    Optional<SettlementTicketCancellation> findByCancellationReference(String cancellationReference);

    Optional<SettlementTicketCancellation> findByTicketCode(String ticketCode);

    List<SettlementTicketCancellation> findByGroupRequestReferenceOrderByTicketCodeAsc(String groupRequestReference);

    List<SettlementTicketCancellation> findByGroupCodeOrderByTicketCodeAsc(String groupCode);

    boolean existsByTicketCode(String ticketCode);
}

