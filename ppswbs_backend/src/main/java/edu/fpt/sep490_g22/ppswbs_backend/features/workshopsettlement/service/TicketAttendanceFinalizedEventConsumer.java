package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceFinalizedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcome;
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
 * Consumes {@link TicketAttendanceFinalizedEvent} to evaluate terminal ticket attendance (003.2 T092, 003.3 T105).
 * Resolves {@code ticketReference} to ticketCode via 003.1 {@code findTicketBookingByOperationsReference}.
 * If terminal NO_SHOW: closes the ticket's OPEN folio as CLOSED (idempotent, no refund).
 * If PARTIAL or ATTENDED: leaves folio OPEN for standard checkout.
 * Non-terminal outcomes wait without side effects.
 * Deduplicates redelivered events by {@code eventId}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketAttendanceFinalizedEventConsumer {
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository bills;
    private final WorkshopBookingSettlementAdapter booking;
    private final SettlementBusinessAuditRepository audits;

    @ApplicationModuleListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onAttendanceFinalized(TicketAttendanceFinalizedEvent event) {
        if (event == null || event.ticketReference() == null || event.ticketReference().isBlank()) {
            return;
        }
        if (event.eventId() != null && !event.eventId().isBlank()
                && audits.existsByCorrelationReference(event.eventId())) {
            log.info("TicketAttendanceFinalizedEvent {} already processed; skipping duplicate", event.eventId());
            return;
        }

        String ticketRef = event.ticketReference().trim();
        TicketAttendanceOutcome attendance = operations.ticketAttendanceOutcome(ticketRef);
        if (attendance == null || !attendance.terminal()) {
            log.info("Ticket attendance outcome not terminal yet for ticketReference={}; skipping", ticketRef);
            return;
        }

        TicketBookingRef ticketBooking = booking.findTicketBookingByOperationsReference(ticketRef).orElse(null);
        String ticketCode = ticketBooking != null ? ticketBooking.ticketCode() : ticketRef;

        SettlementFinalBill bill = bills.findByBookingReference(ticketCode).orElse(null);
        if (bill == null) {
            log.info("No folio found for ticketCode={}", ticketCode);
            recordAudit(event.eventId(), ticketCode, "TICKET_FOLIO_NOT_FOUND");
            return;
        }

        if (attendance.outcome() == TicketAttendanceOutcome.Outcome.NO_SHOW) {
            if (SettlementFinalBillLifecycleStatus.CLOSED.name().equals(bill.getLifecycleStatus())) {
                log.info("Ticket folio already CLOSED for ticketCode={}", ticketCode);
                recordAudit(event.eventId(), bill.getBillReference(), "TICKET_FOLIO_ALREADY_CLOSED");
                return;
            }
            if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(bill.getLifecycleStatus())) {
                log.warn("Cannot close ticket folio in state {} on NO_SHOW for ticketCode={}",
                        bill.getLifecycleStatus(), ticketCode);
                recordAudit(event.eventId(), bill.getBillReference(), "TICKET_FOLIO_INVALID_STATE_FOR_NO_SHOW");
                return;
            }

            bill.setLifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name());
            bills.saveAndFlush(bill);

            recordAudit(event.eventId(), bill.getBillReference(), "TICKET_FOLIO_CLOSED_NO_SHOW");

            String operationReference = "NO-SHOW-" + UUID.randomUUID();
            String correlationReference = event.eventId() != null ? event.eventId() : "CORR-NO-SHOW-" + UUID.randomUUID();
            try {
                var outcomeResult = booking.recordBookingServiceOutcome(ticketCode,
                        BookingServiceOutcome.NO_SHOW, bill.getBillReference(),
                        operationReference, correlationReference);
                if (outcomeResult != null && outcomeResult.status() != BookingOutcomeStatus.ACCEPTED) {
                    log.warn("Booking service outcome NO_SHOW rejected for ticketCode={}: {}",
                            ticketCode, outcomeResult.reason());
                }
            } catch (Exception ex) {
                log.warn("Failed to record booking service outcome NO_SHOW for ticketCode={}: {}",
                        ticketCode, ex.getMessage());
            }
        } else {
            log.info("Ticket attendance outcome for ticketCode={} is {}; folio remains OPEN for checkout",
                    ticketCode, attendance.outcome());
            recordAudit(event.eventId(), bill.getBillReference(), "TICKET_ATTENDANCE_CHECKOUT_ELIGIBLE");
        }
    }

    private void recordAudit(String eventId, String aggregateReference, String actionCode) {
        String corr = (eventId != null && !eventId.isBlank()) ? eventId : ("CORR-" + UUID.randomUUID());
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_FINAL_BILL")
                .aggregateReference(aggregateReference)
                .actionCode(actionCode)
                .correlationReference(corr)
                .createdAt(Instant.now())
                .build());
    }
}

