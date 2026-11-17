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

    /** T159: clears the private link of every stored notification of one kind about one subject. */
    @Modifying(flushAutomatically = true)
    @Query("update SettlementNotificationHandoff h set h.actionLink = null where h.settlementReference = :reference"
            + " and h.notificationKind = :kind and h.actionLink is not null")
    int scrubActionLinks(@Param("reference") String reference, @Param("kind") String kind);

    /** T159: clears the private link of every notification of one kind whose link expired. */
    @Modifying(flushAutomatically = true)
    @Query("update SettlementNotificationHandoff h set h.actionLink = null where h.notificationKind = :kind"
            + " and h.actionLink is not null and h.actionLinkExpiresAt <= :now")
    int scrubExpiredActionLinks(@Param("kind") String kind, @Param("now") Instant now);

    @Modifying
    @Query("delete from SettlementNotificationHandoff h where h.payloadExpiresAt <= :now")
    int deleteExpiredPayloads(@Param("now") Instant now);

}
