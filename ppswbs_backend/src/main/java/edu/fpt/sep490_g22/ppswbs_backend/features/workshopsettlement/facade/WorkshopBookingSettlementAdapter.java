package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingCancellationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRefundStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRefundStatusResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingServiceOutcomeResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CancelConfirmedBookingCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordBookingRefundStatusCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordBookingServiceOutcomeCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordFinalBalanceResolvedCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingLoyaltyOutcomeSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingLoyaltyOutcomeSourceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Settlement's only Booking dependency; no Booking entity or repository crosses this boundary. */
@Component
@RequiredArgsConstructor
public class WorkshopBookingSettlementAdapter {
    private final WorkshopBookingFacade booking;

    public ConfirmedBookingFinancialProjection confirmedProjection(String bookingReference) {
        return booking.getConfirmedBookingFinancialProjection(new ConfirmedBookingFinancialProjectionQuery(bookingReference));
    }

    public VerifiedBookingDepositPaymentsProjection verifiedDepositPayments(String bookingReference) {
        return booking.getVerifiedDepositPayments(new VerifiedBookingDepositPaymentsQuery(bookingReference));
    }

    public WorkshopBookingLoyaltyOutcomeSource loyaltyOutcomeSource(String bookingReference) {
        return booking.getLoyaltyOutcomeSource(new WorkshopBookingLoyaltyOutcomeSourceQuery(bookingReference));
    }

    public CustomerBookingAccessDecision verifyCustomerAccess(String bookingReference, Long memberAccountId,
                                                               String contactEmail, String contactPhone) {
        return booking.verifyCustomerBookingAccess(new CustomerBookingAccessQuery(
                bookingReference, memberAccountId, contactEmail, contactPhone));
    }

    /*
     * The three Settlement conclusions below run in their own transaction. Booking may
     * reject or throw; with a joined transaction a thrown exception would mark the
     * Settlement transaction rollback-only and lose the folio transition even though the
     * caller catches it. Every Booking conclusion is idempotent, so a retry is safe.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FinalBalanceResolutionOutcome recordFinalBalanceResolved(String bookingReference,
            String settlementReference, String operationReference, String correlationReference) {
        return booking.recordFinalBalanceResolved(new RecordFinalBalanceResolvedCommand(
                bookingReference, settlementReference, operationReference, correlationReference));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingServiceOutcomeResult recordBookingServiceOutcome(String bookingReference,
            BookingServiceOutcome outcome, String settlementReference, String operationReference, String correlationReference) {
        return booking.recordBookingServiceOutcome(new RecordBookingServiceOutcomeCommand(
                bookingReference, outcome, settlementReference, operationReference, correlationReference));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingRefundStatusResult recordBookingRefundStatus(String bookingReference,
            BookingRefundStatus status, String settlementReference, String operationReference, String correlationReference) {
        return booking.recordBookingRefundStatus(new RecordBookingRefundStatusCommand(
                bookingReference, status, settlementReference, operationReference, correlationReference));
    }

    public BookingCancellationOutcome cancelConfirmedBooking(String bookingReference, String cancellationCaseReference,
            String operationReference, String correlationReference) {
        return booking.cancelConfirmedBooking(new CancelConfirmedBookingCommand(
                bookingReference, cancellationCaseReference, operationReference, correlationReference));
    }
}
