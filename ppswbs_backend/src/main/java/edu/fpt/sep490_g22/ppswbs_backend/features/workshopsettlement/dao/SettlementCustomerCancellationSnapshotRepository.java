package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCustomerCancellationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementCustomerCancellationSnapshotRepository
        extends JpaRepository<SettlementCustomerCancellationSnapshot, Long> {
    Optional<SettlementCustomerCancellationSnapshot> findByBookingReference(String bookingReference);
    Optional<SettlementCustomerCancellationSnapshot> findByCaseReference(String caseReference);
}
