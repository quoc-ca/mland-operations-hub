package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordFinalBalanceResolvedCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Settlement's only Booking dependency; no Booking entity or repository crosses this boundary. */
@Component
@RequiredArgsConstructor
public class WorkshopBookingSettlementAdapter {
    private final WorkshopBookingFacade booking;

    public ConfirmedBookingFinancialProjection confirmedProjection(String bookingReference) {
        return booking.getConfirmedBookingFinancialProjection(new ConfirmedBookingFinancialProjectionQuery(bookingReference));
    }

    public CustomerBookingAccessDecision verifyCustomerAccess(String bookingReference, Long memberAccountId,
                                                               String contactEmail, String contactPhone) {
        return booking.verifyCustomerBookingAccess(new CustomerBookingAccessQuery(
                bookingReference, memberAccountId, contactEmail, contactPhone));
    }

    public FinalBalanceResolutionOutcome recordFinalBalanceResolved(String bookingReference,
            String settlementReference, String operationReference, String correlationReference) {
        return booking.recordFinalBalanceResolved(new RecordFinalBalanceResolvedCommand(
                bookingReference, settlementReference, operationReference, correlationReference));
    }
}
