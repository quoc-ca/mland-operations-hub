package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SettlementRefundPolicySnapshotRepository extends JpaRepository<SettlementRefundPolicySnapshot, Long> { Optional<SettlementRefundPolicySnapshot> findTopByBookingReferenceOrderByCapturedAtDesc(String bookingReference); }
