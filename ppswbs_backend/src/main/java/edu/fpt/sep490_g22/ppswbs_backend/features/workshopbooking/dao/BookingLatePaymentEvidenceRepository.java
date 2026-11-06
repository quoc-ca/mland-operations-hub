package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingLatePaymentEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingLatePaymentEvidenceRepository extends JpaRepository<BookingLatePaymentEvidence, Long> {
    boolean existsByBookingCode(String bookingCode);
    boolean existsByMerchantReference(String merchantReference);
    java.util.List<BookingLatePaymentEvidence> findByBookingCode(String bookingCode);
}
