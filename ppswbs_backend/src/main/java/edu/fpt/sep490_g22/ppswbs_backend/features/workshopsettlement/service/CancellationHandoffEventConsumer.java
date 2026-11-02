package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.workshop.CancellationHandoffRequestedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.CancellationHandoffOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.OpenCancellationHandoffCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the durable Operations cancellation handoff event.
 * The event carries the original operation/correlation references, so the
 * Settlement facade's idempotency ledger makes delivery retries safe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CancellationHandoffEventConsumer {
    private final WorkshopSettlementFacade settlementFacade;

    @EventListener
    public void onCancellationHandoffRequested(CancellationHandoffRequestedEvent event) {
        CancellationHandoffOutcome outcome = settlementFacade.openCancellationHandoff(
                new OpenCancellationHandoffCommand(
                        event.sessionReference(),
                        event.affectedBookingReferences(),
                        event.cancellationReasonReference(),
                        event.operationReference(),
                        event.correlationReference()));
        if (outcome == null) {
            throw new IllegalStateException("Settlement returned no cancellation handoff outcome");
        }
        log.info("Cancellation handoff {} processed with status {} and case {}",
                event.eventReference(), outcome.status(), outcome.cancellationCaseReference());
    }
}
