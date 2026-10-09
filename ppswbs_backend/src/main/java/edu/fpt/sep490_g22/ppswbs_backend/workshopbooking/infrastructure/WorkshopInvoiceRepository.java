package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkshopInvoiceRepository extends JpaRepository<WorkshopInvoice, Long> {

    Optional<WorkshopInvoice> findByBookingId(Long bookingId);
}
