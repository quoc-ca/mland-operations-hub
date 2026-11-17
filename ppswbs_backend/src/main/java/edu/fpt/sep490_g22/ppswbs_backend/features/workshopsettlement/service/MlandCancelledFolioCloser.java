package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketMlandRemedyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-043 / FR-034 (T157, T158): the one place that closes a ticket folio after Mland cancelled the ticket.
 *
 * <p>The folio moves to {@code CLOSED} (from {@code OPEN} or {@code FINALIZED}) only when the Mland-cancel refund is
 * {@code REFUNDED} (FR-039) and no collection is still {@code PENDING_VERIFICATION}. A pending collection does not
 * block the cancellation or its refund: it is confirmed or failed later, and either outcome calls this again.
 * Nothing else closes a {@code FINALIZED} folio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MlandCancelledFolioCloser {

    private static final String REFUND_OUTCOME = "REFUND";

    private final SettlementTicketMlandRemedyRepository remedies;
    private final SettlementRefundRequestRepository refundRequests;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementFinalBillRepository bills;
    private final SettlementBusinessAuditRepository audits;

    /** Whether Mland cancelled this ticket and owes the customer the 100% refund (so late money is excess). */
    @Transactional(readOnly = true)
    public boolean isMlandCancelled(String ticketCode) {
        return remedies.findByTicketCodeOrderByCreatedAtDesc(ticketCode).stream()
                .anyMatch(remedy -> REFUND_OUTCOME.equals(remedy.getOutcome()));
    }

    /**
     * Closes the folio when the refund is settled and nothing is pending. {@code refundSettled} is true when the
     * caller knows it (the bank-refund confirmation itself, or a cancellation that collected nothing) and null when
     * it must be read: a ticket without a Mland-cancel refund is not closed here, a refund opened for money is
     * settled only when {@code REFUNDED}, and a cancellation that owed nothing is settled.
     *
     * @return whether the folio is {@code CLOSED} after the call
     */
    @Transactional
    public boolean closeIfSettled(String ticketCode, Boolean refundSettled, String actorReference,
                                  String correlation, Instant now) {
        var folio = bills.findByBookingReferenceForUpdate(ticketCode).orElse(null);
        if (folio == null) {
            return false;
        }
        if (SettlementFinalBillLifecycleStatus.CLOSED.name().equals(folio.getLifecycleStatus())) {
            return true;
        }
        boolean refunded;
        if (refundSettled != null) {
            refunded = refundSettled;
        } else {
            var remedy = remedies.findByTicketCodeOrderByCreatedAtDesc(ticketCode).stream()
                    .filter(item -> REFUND_OUTCOME.equals(item.getOutcome())).findFirst().orElse(null);
            if (remedy == null) {
                return false;
            }
            refunded = remedy.getRefundRequestReference() == null
                    || refundRequests.findByRequestReference(remedy.getRefundRequestReference())
                            .map(request -> request.getStatus() == RefundRequestStatus.REFUNDED).orElse(false);
        }
        if (!refunded) {
            return false;
        }
        boolean pending = !collections.findByBillReferenceAndStatusOrderByIdAsc(folio.getBillReference(),
                FolioCollectionStatus.PENDING_VERIFICATION).isEmpty();
        if (pending) {
            log.info("Folio {} stays open: a collection is still pending verification", folio.getBillReference());
            audit(folio.getBillReference(), "TICKET_FOLIO_CLOSE_DEFERRED_PENDING_COLLECTION", actorReference,
                    correlation, now);
            return false;
        }
        folio.setLifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name());
        bills.save(folio);
        audit(folio.getBillReference(), "TICKET_FOLIO_CLOSED_MLAND_REMEDY", actorReference, correlation, now);
        return true;
    }

    private void audit(String billReference, String action, String actorReference, String correlation, Instant now) {
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_FINAL_BILL")
                .aggregateReference(billReference)
                .actionCode(action)
                .actorReference(actorReference)
                .correlationReference(correlation)
                .createdAt(now)
                .build());
    }
}
