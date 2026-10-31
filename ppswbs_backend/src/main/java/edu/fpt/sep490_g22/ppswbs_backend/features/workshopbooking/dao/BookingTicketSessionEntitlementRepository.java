package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingTicketSessionEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingTicketSessionEntitlementRepository extends JpaRepository<BookingTicketSessionEntitlement, Long> {

    List<BookingTicketSessionEntitlement> findByBookingTicketId(Long bookingTicketId);

    List<BookingTicketSessionEntitlement> findBySessionReference(String sessionReference);
}
