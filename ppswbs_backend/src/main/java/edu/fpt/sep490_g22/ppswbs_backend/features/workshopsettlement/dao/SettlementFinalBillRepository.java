package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SettlementFinalBillRepository extends JpaRepository<SettlementFinalBill, Long> {
 Optional<SettlementFinalBill> findByBookingReference(String bookingReference);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select b from SettlementFinalBill b where b.bookingReference = :bookingReference")
 Optional<SettlementFinalBill> findByBookingReferenceForUpdate(@Param("bookingReference") String bookingReference);
}
