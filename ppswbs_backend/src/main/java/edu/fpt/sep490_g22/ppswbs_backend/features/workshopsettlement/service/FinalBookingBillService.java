package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class FinalBookingBillService {
    private final SettlementFinalBillRepository bills;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementBusinessAuditRepository audits;

    @Autowired
    public FinalBookingBillService(SettlementFinalBillRepository bills,
                                  WorkshopBookingSettlementAdapter booking,
                                  WorkshopOperationsSettlementAdapter operations,
                                  WorkshopSettlementAuthorizationService authorization,
                                  SettlementBusinessAuditRepository audits) {
        this.bills = bills;
        this.booking = booking;
        this.operations = operations;
        this.authorization = authorization;
        this.audits = audits;
    }

    /**
     * D1/D2: Direct-booking folio is created at deposit confirmation by MlandBookingConfirmedEvent consumer.
     * This method returns the existing folio; if missing, returns 409 conflict.
     */
    @Transactional
    public SettlementFinalBill create(String bookingReference, String operationReference, String correlationReference) {
        require(bookingReference, "bookingReference");
        return bills.findByBookingReference(bookingReference.trim())
                .orElseThrow(() -> new WorkshopSettlementConflictException("FINAL_BILL_NOT_FOUND"));
    }

    @Transactional
    public SettlementFinalBill finalizeAuthorized(String bookingReference, long outstandingBalance) {
        return finalizeAuthorized(bookingReference, outstandingBalance, null, null);
    }

    /** Called only after WorkshopFinalBillApiService has checked the canonical HTTP actor and current balance. */
    @Transactional
    public SettlementFinalBill finalizeAuthorized(String bookingReference, long outstandingBalance,
                                                  String operationReference, String correlationReference) {
        require(bookingReference, "bookingReference");
        var bill = bills.findByBookingReference(bookingReference.trim())
                .orElseThrow(() -> new WorkshopSettlementValidationException("FINAL_BILL_NOT_FOUND"));
        if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(bill.getLifecycleStatus())) return bill;
        var readiness = operations.readiness(bookingReference.trim());
        if (readiness == null || !readiness.ready()) {
            throw new WorkshopSettlementConflictException("SETTLEMENT_NOT_READY");
        }
        if (outstandingBalance != 0L) {
            throw new WorkshopSettlementConflictException("OUTSTANDING_BALANCE");
        }
        bill.setLifecycleStatus(SettlementFinalBillLifecycleStatus.FINALIZED.name());
        SettlementFinalBill saved = bills.saveAndFlush(bill);

        String op = operationReference != null && !operationReference.isBlank() ? operationReference : "FINALIZE-" + UUID.randomUUID();
        String corr = correlationReference != null && !correlationReference.isBlank() ? correlationReference : "CORR-" + UUID.randomUUID();

        // Send recordFinalBalanceResolved (D4, FR-008)
        try {
            booking.recordFinalBalanceResolved(saved.getBookingReference(), saved.getBillReference(),
                    op + "-BALANCE", corr);
        } catch (Exception ex) {
            log.warn("Failed to notify recordFinalBalanceResolved for bookingReference={}: {}",
                    saved.getBookingReference(), ex.getMessage());
        }

        // Send RecordBookingServiceOutcomeCommand(COMPLETED) (D4, D13, FR-001a)
        try {
            var outcomeResult = booking.recordBookingServiceOutcome(saved.getBookingReference(),
                    BookingServiceOutcome.COMPLETED, saved.getBillReference(),
                    op + "-SERVICE", corr);
            if (outcomeResult != null && outcomeResult.status() != BookingOutcomeStatus.ACCEPTED) {
                log.warn("Booking service outcome COMPLETED rejected for bookingReference={}: {}",
                        saved.getBookingReference(), outcomeResult.reason());
                audits.save(SettlementBusinessAudit.builder()
                        .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                        .aggregateType("SETTLEMENT_FINAL_BILL")
                        .aggregateReference(saved.getBillReference())
                        .actionCode("BOOKING_SERVICE_OUTCOME_REJECTED")
                        .reasonReference(outcomeResult.reason() != null ? outcomeResult.reason().name() : "REJECTED")
                        .correlationReference(corr)
                        .createdAt(Instant.now())
                        .build());
            }
        } catch (Exception ex) {
            log.warn("Failed to record booking service outcome COMPLETED for bookingReference={}: {}",
                    saved.getBookingReference(), ex.getMessage());
        }

        return saved;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
    }
}
