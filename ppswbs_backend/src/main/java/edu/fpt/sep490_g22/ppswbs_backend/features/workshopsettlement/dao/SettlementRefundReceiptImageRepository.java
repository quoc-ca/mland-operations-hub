package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundReceiptImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementRefundReceiptImageRepository extends JpaRepository<SettlementRefundReceiptImage, String> {

    Optional<SettlementRefundReceiptImage> findByUploadedByAccountIdAndUploadIdempotencyKey(Long accountId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SettlementRefundReceiptImage i where i.id = :id")
    Optional<SettlementRefundReceiptImage> findForUpdate(@Param("id") String id);

    /** The image of the latest recorded transfer of the request, if one is attached. */
    Optional<SettlementRefundReceiptImage> findFirstByRefundRequestIdAndStateOrderByAttachedAtDesc(Long refundRequestId,
                                                                                                    String state);

    boolean existsByRefundRequestIdAndAttachedBankTransferReferenceAndState(Long refundRequestId, String reference,
                                                                            String state);

    @Query("select distinct i.refundRequestId from SettlementRefundReceiptImage i "
            + "where i.refundRequestId in :ids and i.state = 'ATTACHED'")
    List<Long> findRequestIdsWithAttachedImage(@Param("ids") Collection<Long> ids);

    /** Uploads never attached, oldest first, for the 24 hour cleanup. */
    List<SettlementRefundReceiptImage> findTop100ByStateAndCreatedAtBeforeOrderByCreatedAtAsc(String state, Instant before);

    List<SettlementRefundReceiptImage> findTop100ByStateOrderByDeleteRequestedAtAsc(String state);
}
