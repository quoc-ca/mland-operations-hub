package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The one place a folio's balance is worked out (003.3 FR-031..FR-035).
 *
 * <p>Balance = the frozen base line, plus confirmed charges, minus verified final payments, minus confirmed
 * collections. A {@code PENDING_VERIFICATION} collection is deliberately not subtracted (FR-032): it is
 * reported on its own so the same balance is never assigned twice (FR-034) and so finalization can refuse
 * while any part is unproven (FR-035).
 */
@Service
@RequiredArgsConstructor
public class FolioBalanceService {

    private final SettlementChargeRepository charges;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementFolioCollectionRepository collections;

    /**
     * @param balanceDue      what is still owed, never negative
     * @param pendingVnd      recorded but unproven money; it has not reduced {@code balanceDue}
     * @param unassignedVnd   balance not covered by confirmed or pending money, which is what a new
     *                        collection may take at most
     */
    public record FolioBalance(long balanceDue, long pendingVnd, long unassignedVnd) {

        public boolean hasPendingPart() {
            return pendingVnd > 0;
        }
    }

    public FolioBalance balanceOf(SettlementFinalBill folio) {
        long chargeTotal = charges
                .findByBillReferenceAndLifecycleStatusOrderByIdAsc(folio.getBillReference(), "CONFIRMED").stream()
                .mapToLong(charge -> charge.getAmountVnd() == null ? 0L : charge.getAmountVnd())
                .sum();
        long verifiedFinalPayments = evidence
                .findByBookingReferenceAndConclusion(folio.getBookingReference(), "FINAL_PAYMENT_VERIFIED").stream()
                .mapToLong(item -> item.getAmountVnd() == null ? 0L : item.getAmountVnd())
                .sum();
        List<SettlementFolioCollection> folioCollections =
                collections.findByBillReferenceOrderByIdAsc(folio.getBillReference());
        long confirmed = sum(folioCollections, FolioCollectionStatus.CONFIRMED);
        long pending = sum(folioCollections, FolioCollectionStatus.PENDING_VERIFICATION);

        long gross = Math.addExact(folio.getRemainingAmountVnd(), chargeTotal);
        long settled = Math.addExact(verifiedFinalPayments, confirmed);
        long balanceDue = Math.max(0L, Math.subtractExact(gross, settled));
        long unassigned = Math.max(0L, Math.subtractExact(balanceDue, pending));
        return new FolioBalance(balanceDue, pending, unassigned);
    }

    /** Confirmed charges of a folio, for callers that only need the charge side. */
    public long confirmedCharges(String billReference) {
        return charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(billReference, "CONFIRMED").stream()
                .mapToLong(SettlementCharge::getAmountVnd)
                .sum();
    }

    private static long sum(List<SettlementFolioCollection> items, FolioCollectionStatus status) {
        return items.stream()
                .filter(item -> item.getStatus() == status)
                .mapToLong(SettlementFolioCollection::getAmountVnd)
                .sum();
    }
}
