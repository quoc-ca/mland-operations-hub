package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@link BookingAttendanceFinalizedEvent} to evaluate terminal booking-wide attendance (D2, D3, D4).
 * If NO_SHOW: closes the OPEN folio as CLOSED and sends RecordBookingServiceOutcomeCommand(NO_SHOW) to 003.1.
 * If PARTIAL or ATTENDED: leaves folio OPEN for standard checkout.
 * Non-terminal outcomes wait without side effects. Replay is idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingAttendanceFinalizedEventConsumer {
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository bills;
    private final WorkshopBookingSettlementAdapter booking;
    private final SettlementBusinessAuditRepository audits;

    @ApplicationModuleListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onAttendanceFinalized(BookingAttendanceFinalizedEvent event) {
        if (event == null || event.bookingReference() == null || event.bookingReference().isBlank()) {
            return;
        }
        String bookingReference = event.bookingReference().trim();
        BookingAttendanceOutcome attendance = operations.attendanceOutcome(bookingReference);
        if (attendance == null || !attendance.terminal()) {
            log.info("Attendance outcome not terminal yet for bookingReference={}; skipping", bookingReference);
            return;
        }

        if (attendance.outcome() == BookingAttendanceOutcome.Outcome.NO_SHOW) {
            SettlementFinalBill bill = bills.findByBookingReference(bookingReference).orElse(null);
            if (bill == null) {
                log.info("No direct folio found for bookingReference={}", bookingReference);
                return;
            }
            if (SettlementFinalBillLifecycleStatus.CLOSED.name().equals(bill.getLifecycleStatus())) {
                log.info("Direct folio already CLOSED for bookingReference={}", bookingReference);
                return;
            }
            if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(bill.getLifecycleStatus())) {
                log.warn("Cannot close folio in state {} on NO_SHOW for bookingReference={}",
                        bill.getLifecycleStatus(), bookingReference);
                return;
            }

            bill.setLifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name());
            bills.saveAndFlush(bill);

            String operationReference = "NO-SHOW-" + UUID.randomUUID();
            String correlationReference = "CORR-NO-SHOW-" + UUID.randomUUID();

            audits.save(SettlementBusinessAudit.builder()
                    .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                    .aggregateType("SETTLEMENT_FINAL_BILL")
                    .aggregateReference(bill.getBillReference())
                    .actionCode("DIRECT_FOLIO_CLOSED_NO_SHOW")
                    .correlationReference(correlationReference)
                    .createdAt(Instant.now())
                    .build());

            try {
                var outcomeResult = booking.recordBookingServiceOutcome(bookingReference,
                        BookingServiceOutcome.NO_SHOW, bill.getBillReference(),
                        operationReference, correlationReference);
                if (outcomeResult != null && outcomeResult.status() != BookingOutcomeStatus.ACCEPTED) {
                    log.warn("Booking service outcome NO_SHOW rejected for bookingReference={}: {}",
                            bookingReference, outcomeResult.reason());
                    audits.save(SettlementBusinessAudit.builder()
                            .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                            .aggregateType("SETTLEMENT_FINAL_BILL")
                            .aggregateReference(bill.getBillReference())
                            .actionCode("BOOKING_SERVICE_OUTCOME_REJECTED")
                            .reasonReference(outcomeResult.reason() != null ? outcomeResult.reason().name() : "REJECTED")
                            .correlationReference(correlationReference)
                            .createdAt(Instant.now())
                            .build());
                }
            } catch (Exception ex) {
                log.warn("Failed to record booking service outcome NO_SHOW for bookingReference={}: {}",
                        bookingReference, ex.getMessage());
            }
        } else {
            log.info("Attendance outcome for bookingReference={} is {}; folio remains OPEN for checkout",
                    bookingReference, attendance.outcome());
        }
    }
}

