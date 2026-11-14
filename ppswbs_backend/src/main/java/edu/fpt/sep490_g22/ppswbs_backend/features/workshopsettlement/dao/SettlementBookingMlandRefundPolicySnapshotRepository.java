package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBookingMlandRefundPolicySnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SettlementBookingMlandRefundPolicySnapshotRepository
        extends JpaRepository<SettlementBookingMlandRefundPolicySnapshot, Long> {
    Optional<SettlementBookingMlandRefundPolicySnapshot> findByBookingReference(String bookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select snapshot from SettlementBookingMlandRefundPolicySnapshot snapshot "
            + "where snapshot.bookingReference = :bookingReference")
    Optional<SettlementBookingMlandRefundPolicySnapshot> findByBookingReferenceForUpdate(
            @Param("bookingReference") String bookingReference);
}
