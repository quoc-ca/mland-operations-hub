package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingCapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityReleaseOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Releases a pending booking's Operations holds before changing its local state. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingHoldReleaseService {
    private final WorkshopBookingRepository bookingRepository;
    private final BookingCapacityHoldRepository holdRepository;
    private final BookingSessionCommitmentRepository commitmentRepository;
    private final BookingAuditEvidenceRepository auditEvidenceRepository;
    private final WorkshopOperationsAdapter operationsAdapter;

    @Transactional
    public void releasePendingHold(String bookingCode, String operationReference, String correlationReference) {
        releasePendingHold(bookingCode, operationReference, correlationReference, "SYSTEM", null, null);
    }

    @Transactional
    public void expirePendingHold(String bookingCode, String operationReference) {
        release(bookingCode, operationReference, operationReference, "SYSTEM", null, null, BookingStatus.EXPIRED);
    }

    @Transactional
    public void releasePendingHold(String bookingCode, String operationReference, String correlationReference,
                                  String actorType, Long actorId, String actionReason) {
        release(bookingCode, operationReference, correlationReference, actorType, actorId, actionReason,
                BookingStatus.CANCELLED);
    }

    private void release(String bookingCode, String operationReference, String correlationReference,
                         String actorType, Long actorId, String actionReason, BookingStatus terminalStatus) {
        log.info("releasePendingHold: bookingCode={}, opRef={}", bookingCode, operationReference);
        WorkshopBooking booking = bookingRepository.findLockedByBookingCode(bookingCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }

        List<BookingCapacityHold> holds = holdRepository.findByBookingId(booking.getId());
        List<UUID> activeReferences = new ArrayList<>();
        for (BookingCapacityHold hold : holds) {
            if ("RESERVED".equalsIgnoreCase(hold.getHoldStatus())) {
                try {
                    activeReferences.add(UUID.fromString(hold.getHoldReference()));
                } catch (RuntimeException ex) {
                    throw new BookingConflictException("A stored capacity hold reference is invalid");
                }
            }
        }

        if (activeReferences.isEmpty()) {
            throw new BookingConflictException("No active capacity hold is available to release");
        }
        CapacityReleaseOutcome outcome = operationsAdapter.releaseBookingHolds(
                bookingCode, activeReferences, operationReference, correlationReference);
        if (outcome == null || outcome.status() != OutcomeStatus.ACCEPTED) {
            String reason = outcome == null ? "UNKNOWN" : String.valueOf(outcome.reason());
            throw new BookingConflictException("Capacity release was not accepted by Workshop Operations: " + reason);
        }
        for (BookingCapacityHold hold : holds) {
            if ("RESERVED".equalsIgnoreCase(hold.getHoldStatus())) {
                hold.setHoldStatus("RELEASED");
                holdRepository.save(hold);
            }
        }

        List<BookingSessionCommitment> commitments = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        if (commitments.isEmpty()) {
            throw new BookingConflictException("Booking has no session commitments to release");
        }
        booking.setBookingStatus(terminalStatus);
        booking.setCancelledAt(Instant.now());
        bookingRepository.save(booking);

        auditEvidenceRepository.save(BookingAuditEvidence.builder()
                .bookingCode(bookingCode).locationId(commitments.get(0).getLocationId())
                .actorType(actorType).actorId(actorId).action(terminalStatus == BookingStatus.EXPIRED
                        ? "EXPIRE_HOLD" : "RELEASE_HOLD").outcome("ACCEPTED")
                .actionReason(actionReason)
                .correlationReference(correlationReference).occurredAt(Instant.now()).build());
    }
}
