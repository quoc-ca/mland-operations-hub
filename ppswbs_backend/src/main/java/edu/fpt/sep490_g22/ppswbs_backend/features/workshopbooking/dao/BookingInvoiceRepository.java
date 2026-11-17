package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingInvoiceRepository extends JpaRepository<BookingInvoice, Long> {

    Optional<BookingInvoice> findByBookingId(Long bookingId);

    Optional<BookingInvoice> findByInvoiceNumber(String invoiceNumber);
}
