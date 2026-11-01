package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementNotificationHandoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementNotificationHandoffRepository extends JpaRepository<SettlementNotificationHandoff, Long> {
    Optional<SettlementNotificationHandoff> findByNotificationReference(String notificationReference);

    List<SettlementNotificationHandoff> findTop100ByDispatchedAtIsNullAndPayloadExpiresAtAfterOrderByCreatedAtAsc(
            Instant now);

    @Modifying
    @Query("delete from SettlementNotificationHandoff h where h.payloadExpiresAt <= :now")
    int deleteExpiredPayloads(@Param("now") Instant now);

}
