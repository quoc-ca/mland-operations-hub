package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingPaymentReceiptRepository extends JpaRepository<BookingPaymentReceipt, Long> {

    Optional<BookingPaymentReceipt> findByProviderEventId(String providerEventId);

    List<BookingPaymentReceipt> findByBookingId(Long bookingId);
}
