package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Applies final Member Loyalty outcomes from a locked bill and locked Operations attendance snapshot. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopLoyaltyOutcomeProcessor {
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final SettlementEvidenceConclusionRepository evidence;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final LoyaltyFacade loyalty;

    @Transactional
    public void process(String bookingReference) {
        SettlementFinalBill bill = bills.findByBookingReferenceForUpdate(bookingReference).orElse(null);
        if (bill == null || !("OPEN".equals(bill.getLifecycleStatus()) || "FINALIZED".equals(bill.getLifecycleStatus()))) {
            return;
        }

        SettlementReadiness readiness = operations.readiness(bookingReference);
        if (readiness == null || !readiness.ready() || !readiness.attendanceCorrectionWindowClosed()
                || readiness.lastRequiredSessionFinalizedAt() == null) {
            return;
        }

        var source = booking.loyaltyOutcomeSource(bookingReference);
        if (source == null || !source.memberOrigin() || source.snapshotRef() == null || source.snapshotRef().isBlank()) {
            return;
        }

        var existing = loyalty.findBenefitResult(SourceKind.WORKSHOP, bookingReference);
        if (existing == null || existing.earnState() != EarnState.PENDING) {
            return;
        }

        Instant now = Instant.now();
        String proofReference = "attendance-finalized:" + readiness.lastRequiredSessionFinalizedAt().toEpochMilli();
        if (readiness.hasNoShow()) {
            loyalty.finishIneligibleEarn(new AuthoritativeOutcome(
                    SourceKind.WORKSHOP, bookingReference, "workshopsettlement",
                    "no-show-" + bill.getBillReference(), source.nextOutcomeVersion(),
                    OutcomeKind.WORKSHOP_NO_SHOW_FINAL, now, source.originalDeadline(), source.snapshotRef(),
                    true, proofReference));
            return;
        }

        if (!"FINALIZED".equals(bill.getLifecycleStatus()) || outstanding(bill) != 0L) {
            return;
        }
        loyalty.grantEarnedPoints(new AuthoritativeOutcome(
                SourceKind.WORKSHOP, bookingReference, "workshopsettlement",
                "completed-" + bill.getBillReference(), source.nextOutcomeVersion(),
                OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, now, source.originalDeadline(), source.snapshotRef(),
                true, "final-bill:" + bill.getBillReference()));
    }

    private long outstanding(SettlementFinalBill bill) {
        long confirmedCharges = charges.findByBillReferenceAndLifecycleStatusOrderByIdAsc(
                        bill.getBillReference(), "CONFIRMED").stream()
                .mapToLong(charge -> charge.getAmountVnd() == null ? 0L : charge.getAmountVnd()).sum();
        long finalPayments = evidence.findByBookingReferenceAndConclusion(
                        bill.getBookingReference(), "FINAL_PAYMENT_VERIFIED").stream()
                .mapToLong(item -> item.getAmountVnd() == null ? 0L : item.getAmountVnd()).sum();
        return Math.max(0L, Math.subtractExact(Math.addExact(bill.getRemainingAmountVnd(), confirmedCharges), finalPayments));
    }
}
