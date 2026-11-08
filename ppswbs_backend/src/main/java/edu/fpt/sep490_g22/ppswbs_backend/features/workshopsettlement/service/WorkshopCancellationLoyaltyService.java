package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Applies a verified customer cancellation or Mland-caused remedy in the same transaction that resolves its case. */
@Service
@RequiredArgsConstructor
public class WorkshopCancellationLoyaltyService {
    private final WorkshopBookingSettlementAdapter booking;
    private final LoyaltyFacade loyalty;

    public void restoreVerifiedCancellation(SettlementCase settlementCase, String proofReference) {
        if (settlementCase == null) return;
        if (!"CUSTOMER_CANCELLATION".equals(settlementCase.getCaseType())
                && !"MLAND_CANCELLATION_REMEDY".equals(settlementCase.getCaseType())) return;
        var source = booking.loyaltyOutcomeSource(settlementCase.getBookingReference());
        if (source == null || !source.memberOrigin() || source.snapshotRef() == null || source.snapshotRef().isBlank()) return;
        var existing = loyalty.findBenefitResult(SourceKind.WORKSHOP, settlementCase.getBookingReference());
        if (existing == null || existing.earnState() != EarnState.PENDING) return;
        Instant now = Instant.now();
        String key = "valid-cancellation-" + settlementCase.getCaseReference();
        loyalty.restoreCancelledWorkshopBenefits(new AuthoritativeOutcome(SourceKind.WORKSHOP,
                settlementCase.getBookingReference(), "workshopsettlement", key,
                source.nextOutcomeVersion(), OutcomeKind.VALID_WORKSHOP_CANCELLATION,
                now, source.originalDeadline(), source.snapshotRef(), true, proofReference));
        loyalty.finishIneligibleEarn(new AuthoritativeOutcome(SourceKind.WORKSHOP,
                settlementCase.getBookingReference(), "workshopsettlement", key + "-earn",
                source.nextOutcomeVersion() + 1, OutcomeKind.VALID_WORKSHOP_CANCELLATION,
                now, source.originalDeadline(), source.snapshotRef(), true, proofReference));
    }
}
