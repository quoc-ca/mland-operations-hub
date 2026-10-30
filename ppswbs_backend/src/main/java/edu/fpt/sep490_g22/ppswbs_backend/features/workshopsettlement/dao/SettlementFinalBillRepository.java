package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SettlementFinalBillRepository extends JpaRepository<SettlementFinalBill, Long> { Optional<SettlementFinalBill> findByBookingReference(String bookingReference); }
