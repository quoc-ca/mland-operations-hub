package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingAttendanceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Applies the final Member Loyalty outcome of a direct-booking folio (owner decisions D6, D15).
 *
 * <ul>
 *   <li>Terminal {@code NO_SHOW} attendance (every ticket on every required session) finishes the
 *       earn with {@code WORKSHOP_NO_SHOW_FINAL}, whether the folio is still OPEN or already CLOSED.</li>
 *   <li>Terminal {@code ATTENDED} or {@code PARTIAL} grants {@code WORKSHOP_COMPLETED_AND_FULLY_PAID}
 *       only once the folio is FINALIZED with a zero balance. A CLOSED folio never grants points.</li>
 *   <li>A CLOSED folio produced by an accepted customer cancellation or a Mland-caused remedy of a
 *       Member booking restores the benefits with {@code VALID_WORKSHOP_CANCELLATION}.</li>
 * </ul>
 *
 * The decision uses only the authoritative attendance outcome. It runs in one transaction under the
 * folio row lock; a Loyalty failure rolls the whole run back and the scheduler retries it, so a
 * Loyalty error can never leave Settlement or Loyalty half-applied. Once a folio's outcome is decided
 * it is marked processed and is no longer scanned.
 */
@Service
@RequiredArgsConstructor
public class WorkshopLoyaltyOutcomeProcessor {
    private static final List<String> CANCELLATION_CASE_TYPES = List.of("CUSTOMER_CANCELLATION", "MLAND_CANCELLATION_REMEDY");

    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementCaseRepository cases;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopCancellationLoyaltyService cancellationLoyalty;
    private final LoyaltyFacade loyalty;

    @Transactional
    public void process(String bookingReference) {
        SettlementFinalBill bill = bills.findByBookingReferenceForUpdate(bookingReference).orElse(null);
        if (bill == null || bill.getLoyaltyProcessedAt() != null || !isKnownFolioState(bill.getLifecycleStatus())) {
            return;
        }

        var source = booking.loyaltyOutcomeSource(bookingReference);
        if (source == null || !source.memberOrigin()) {
            markProcessed(bill); // Guests have no Loyalty outcome.
            return;
        }
        if (source.snapshotRef() == null || source.snapshotRef().isBlank()) {
            return;
        }

        var existing = loyalty.findBenefitResult(SourceKind.WORKSHOP, bookingReference);
        if (existing == null) {
            return;
        }
        if (existing.earnState() != EarnState.PENDING) {
            markProcessed(bill); // Already decided (for example by the verified-refund path).
            return;
        }

        boolean closed = SettlementFinalBillLifecycleStatus.CLOSED.name().equals(bill.getLifecycleStatus());
        BookingAttendanceOutcome attendance = operations.attendanceOutcome(bookingReference);
        BookingAttendanceOutcome.Outcome outcome = attendance != null && attendance.terminal() ? attendance.outcome() : null;
        Instant now = Instant.now();

        if (outcome == BookingAttendanceOutcome.Outcome.NO_SHOW) {
            loyalty.finishIneligibleEarn(new AuthoritativeOutcome(
                    SourceKind.WORKSHOP, bookingReference, "workshopsettlement",
                    "no-show-" + bill.getBillReference(), source.nextOutcomeVersion(),
                    OutcomeKind.WORKSHOP_NO_SHOW_FINAL, now, source.originalDeadline(), source.snapshotRef(),
                    true, "attendance-outcome:" + bill.getBillReference()));
            markProcessed(bill);
            return;
        }

        if (closed) {
            // A CLOSED folio never grants points; it is closed by a cancellation or a remedy.
            var cancellations = cases.findByBookingReferenceAndCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
                    bookingReference, CANCELLATION_CASE_TYPES, "RESOLVED");
            if (!cancellations.isEmpty()) {
                var settled = cancellations.get(0);
                cancellationLoyalty.restoreVerifiedCancellation(settled, "case:" + settled.getCaseReference());
                markProcessed(bill);
            }
            return;
        }

        if ((outcome == BookingAttendanceOutcome.Outcome.ATTENDED || outcome == BookingAttendanceOutcome.Outcome.PARTIAL)
                && SettlementFinalBillLifecycleStatus.FINALIZED.name().equals(bill.getLifecycleStatus())
                && outstanding(bill) == 0L) {
            loyalty.grantEarnedPoints(new AuthoritativeOutcome(
                    SourceKind.WORKSHOP, bookingReference, "workshopsettlement",
                    "completed-" + bill.getBillReference(), source.nextOutcomeVersion(),
                    OutcomeKind.WORKSHOP_COMPLETED_AND_FULLY_PAID, now, source.originalDeadline(), source.snapshotRef(),
                    true, "final-bill:" + bill.getBillReference()));
            markProcessed(bill);
        }
    }

    private void markProcessed(SettlementFinalBill bill) {
        bill.setLoyaltyProcessedAt(Instant.now());
        bills.save(bill);
    }

    private static boolean isKnownFolioState(String status) {
        return SettlementFinalBillLifecycleStatus.isKnown(status);
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
