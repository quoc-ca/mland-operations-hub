package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * T002: Named implementation of WorkshopBookingFacade.
 * Implements the cross-feature contract boundary.
 * Business flow and persistent entity state are implemented in T009–T014.
 */
@Service
@Slf4j
public class WorkshopBookingFacadeImpl implements WorkshopBookingFacade {

    @Override
    public ConfirmedBookingFinancialProjection getConfirmedBookingFinancialProjection(ConfirmedBookingFinancialProjectionQuery query) {
        log.info("WorkshopBookingFacade.getConfirmedBookingFinancialProjection: bookingReference={}", query.bookingReference());
        // Persistence and aggregate state are built in T009–T014
        return null;
    }

    @Override
    public FinalBalanceResolutionOutcome recordFinalBalanceResolved(RecordFinalBalanceResolvedCommand command) {
        log.info("WorkshopBookingFacade.recordFinalBalanceResolved: bookingReference={}, settlementReference={}, opRef={}",
                command.bookingReference(), command.settlementReference(), command.operationReference());
        // Lifecycle transitions and state updates are implemented in T009–T014
        return FinalBalanceResolutionOutcome.rejected(BookingRejectionReason.NOT_READY);
    }

    @Override
    public DepositPaymentEvidenceOutcome applyVerifiedDepositPaymentEvidence(ApplyVerifiedDepositPaymentEvidenceCommand command) {
        log.info("WorkshopBookingFacade.applyVerifiedDepositPaymentEvidence: bookingReference={}, evidenceRef={}, opRef={}",
                command.bookingReference(), command.verifiedEvidenceReference(), command.operationReference());
        // Payment processing and capacity commit orchestration are implemented in T009–T014
        return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
    }

    @Override
    public TicketRedeliveryOutcome requestBookingTicketRedelivery(RequestBookingTicketRedeliveryCommand command) {
        log.info("WorkshopBookingFacade.requestBookingTicketRedelivery: bookingReference={}, ticketsCount={}, opRef={}",
                command.bookingReference(), command.activeUnusedTicketReferences().size(), command.operationReference());
        // Delivery queuing and contact dispatch are implemented in T009–T014
        return TicketRedeliveryOutcome.rejected(BookingRejectionReason.NOT_READY);
    }

    @Override
    public BookingNotificationDeliveryPayload getBookingNotificationDeliveryPayload(BookingNotificationDeliveryPayloadQuery query) {
        log.info("WorkshopBookingFacade.getBookingNotificationDeliveryPayload: notificationReference={}", query.notificationReference());
        // Notification snapshot retrieval is implemented in T007 / T009–T014
        return null;
    }
}
