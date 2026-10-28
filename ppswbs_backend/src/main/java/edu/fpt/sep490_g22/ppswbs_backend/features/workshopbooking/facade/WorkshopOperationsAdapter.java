package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ApplyVerifiedRefundConsequenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingSettlementReadinessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingTicketProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityCommitOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityReleaseOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CommitBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RefundConsequenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReleaseBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceRequiredSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RequiredSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * T003: Adapter for Workshop Booking to consume the public WorkshopOperationsFacade.
 * <p>
 * Invariants:
 * - Consumes ONLY public WorkshopOperationsFacade boundary types.
 * - Does NOT import or access Operations entities, DAOs, internal services, or controllers.
 * - Does not implement booking business flow or persistence (reserved for T009–T014).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkshopOperationsAdapter {

    private final WorkshopOperationsFacade workshopOperationsFacade;

    public SaleEligibilitySnapshot getSaleEligibility(UUID sessionReference) {
        log.debug("WorkshopOperationsAdapter.getSaleEligibility: sessionReference={}", sessionReference);
        return workshopOperationsFacade.getSaleEligibility(new SaleEligibilityQuery(sessionReference));
    }

    public SaleEligibilitySnapshot getSaleEligibility(String sessionReference) {
        try {
            return getSaleEligibility(UUID.fromString(sessionReference));
        } catch (IllegalArgumentException e) {
            return getSaleEligibility(UUID.nameUUIDFromBytes(sessionReference.getBytes()));
        }
    }

    public SaleEligibilitySnapshot getSaleEligibility(Long sessionId) {
        return getSaleEligibility(new UUID(0L, sessionId));
    }

    public CapacityHoldOutcome reserveBookingCapacity(
            String bookingReference,
            List<UUID> orderedRequiredSessionReferences,
            int participantCount,
            String operationReference,
            String correlationReference) {
        log.info("WorkshopOperationsAdapter.reserveBookingCapacity: bookingRef={}, sessions={}, participants={}, opRef={}",
                bookingReference, orderedRequiredSessionReferences, participantCount, operationReference);
        return workshopOperationsFacade.reserveBookingCapacity(
                new ReserveBookingCapacityCommand(
                        bookingReference,
                        orderedRequiredSessionReferences,
                        participantCount,
                        operationReference,
                        correlationReference
                )
        );
    }

    public CapacityCommitOutcome commitBookingHolds(
            String bookingReference,
            List<UUID> expectedHoldReferences,
            String operationReference,
            String correlationReference) {
        log.info("WorkshopOperationsAdapter.commitBookingHolds: bookingRef={}, holds={}, opRef={}",
                bookingReference, expectedHoldReferences, operationReference);
        return workshopOperationsFacade.commitBookingHolds(
                new CommitBookingHoldsCommand(
                        bookingReference,
                        expectedHoldReferences,
                        operationReference,
                        correlationReference
                )
        );
    }

    public CapacityReleaseOutcome releaseBookingHolds(
            String bookingReference,
            List<UUID> expectedHoldReferences,
            String operationReference,
            String correlationReference) {
        log.info("WorkshopOperationsAdapter.releaseBookingHolds: bookingRef={}, holds={}, opRef={}",
                bookingReference, expectedHoldReferences, operationReference);
        return workshopOperationsFacade.releaseBookingHolds(
                new ReleaseBookingHoldsCommand(
                        bookingReference,
                        expectedHoldReferences,
                        operationReference,
                        correlationReference
                )
        );
    }

    public DeliverySafeTicketSet getBookingTicketProjection(String bookingReference) {
        log.debug("WorkshopOperationsAdapter.getBookingTicketProjection: bookingRef={}", bookingReference);
        return workshopOperationsFacade.getBookingTicketProjection(new BookingTicketProjectionQuery(bookingReference));
    }

    public DeliverySafeTicketSet getCurrentEntitlementProjection(String bookingReference) {
        log.debug("WorkshopOperationsAdapter.getCurrentEntitlementProjection: bookingRef={}", bookingReference);
        return workshopOperationsFacade.getBookingTicketProjection(new BookingTicketProjectionQuery(bookingReference));
    }

    public SettlementReadiness getSettlementReadiness(String bookingReference) {
        log.debug("WorkshopOperationsAdapter.getSettlementReadiness: bookingRef={}", bookingReference);
        return workshopOperationsFacade.getSettlementReadiness(new BookingSettlementReadinessQuery(bookingReference));
    }

    public RequiredSessionReplacementOutcome replaceRequiredSessions(
            String bookingReference,
            List<UUID> completeTargetSessionReferences,
            String operationReference,
            String correlationReference) {
        log.info("WorkshopOperationsAdapter.replaceRequiredSessions: bookingRef={}, targetSessions={}, opRef={}",
                bookingReference, completeTargetSessionReferences, operationReference);
        return workshopOperationsFacade.replaceRequiredSessions(
                new ReplaceRequiredSessionsCommand(
                        bookingReference,
                        completeTargetSessionReferences,
                        operationReference,
                        correlationReference
                )
        );
    }

    public RefundConsequenceOutcome applyVerifiedRefundConsequence(
            String bookingReference,
            String verifiedRefundReference,
            String operationReference,
            String correlationReference) {
        log.info("WorkshopOperationsAdapter.applyVerifiedRefundConsequence: bookingRef={}, refundRef={}, opRef={}",
                bookingReference, verifiedRefundReference, operationReference);
        return workshopOperationsFacade.applyVerifiedRefundConsequence(
                new ApplyVerifiedRefundConsequenceCommand(
                        bookingReference,
                        verifiedRefundReference,
                        operationReference,
                        correlationReference
                )
        );
    }
}
