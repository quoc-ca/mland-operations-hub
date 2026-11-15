package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementTicketChangeRequestRepository extends JpaRepository<SettlementTicketChangeRequest, Long> {

    Optional<SettlementTicketChangeRequest> findByRequestReference(String requestReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SettlementTicketChangeRequest r WHERE r.requestReference = :requestReference")
    Optional<SettlementTicketChangeRequest> findByRequestReferenceForUpdate(
            @Param("requestReference") String requestReference);

    Optional<SettlementTicketChangeRequest> findByTicketCodeAndIdempotencyKey(String ticketCode, String idempotencyKey);

    boolean existsByTicketCodeAndStatus(String ticketCode, String status);

    List<SettlementTicketChangeRequest> findByStatusOrderByCreatedAtAsc(String status);

    List<SettlementTicketChangeRequest> findByStatusAndExpiresAtLessThanEqual(String status, Instant expiresAt);

    List<SettlementTicketChangeRequest> findByTicketCodeAndStatus(String ticketCode, String status);
}
