package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingAuditEvidenceRepository extends JpaRepository<BookingAuditEvidence, Long> {

    List<BookingAuditEvidence> findByBookingCodeOrderByOccurredAtDesc(String bookingCode);

    List<BookingAuditEvidence> findByLocationId(Long locationId);

    List<BookingAuditEvidence> findByCorrelationReference(String correlationReference);
}
