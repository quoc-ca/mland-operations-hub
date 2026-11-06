package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens exactly one direct-booking OPEN folio upon booking confirmation (owner decisions D1, D4, D5).
 * The frozen base line is the post-promotion amount and the 003.1 verified deposit is the first collection.
 * OTA bookings have no direct folio and are safely skipped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DirectBookingFolioOpeningService {
    private final SettlementFinalBillRepository bills;
    private final WorkshopBookingSettlementAdapter booking;

    @Transactional
    public Optional<SettlementFinalBill> openFolio(String bookingReference, Instant confirmedAt) {
        if (bookingReference == null || bookingReference.isBlank()) {
            return Optional.empty();
        }
        String ref = bookingReference.trim();
        Optional<SettlementFinalBill> existing = bills.findByBookingReference(ref);
        if (existing.isPresent()) {
            return existing;
        }

        ConfirmedBookingFinancialProjection projection = booking.confirmedProjection(ref);
        if (projection == null || projection.frozenPostPromotionAmount() == null
                || projection.acceptedDepositAmount() == null) {
            log.info("Direct booking financial projection unavailable for bookingReference={}, skipping folio creation", ref);
            return Optional.empty();
        }

        VerifiedBookingDepositPaymentsProjection depositPayments = booking.verifiedDepositPayments(ref);
        if (depositPayments == null || depositPayments.payments() == null || depositPayments.payments().isEmpty()) {
            log.info("No verified deposit payments for bookingReference={}, skipping folio creation", ref);
            return Optional.empty();
        }

        long depositPaid = depositPayments.payments().stream()
                .filter(VerifiedBookingPayment::verified)
                .mapToLong(VerifiedBookingPayment::amountVnd)
                .sum();
        if (depositPaid <= 0) {
            log.info("Verified deposit amount is zero for bookingReference={}, skipping folio creation", ref);
            return Optional.empty();
        }

        long postPromotionAmount = projection.frozenPostPromotionAmount();
        long depositAmount = depositPaid;
        long remainingAmount = Math.max(0L, Math.subtractExact(postPromotionAmount, depositAmount));

        Instant now = Instant.now();
        Instant frozenAt = confirmedAt != null ? confirmedAt : now;

        SettlementFinalBill bill = SettlementFinalBill.builder()
                .billReference("SETTLE-BILL-" + UUID.randomUUID())
                .bookingReference(ref)
                .postPromotionAmountVnd(postPromotionAmount)
                .acceptedDepositAmountVnd(depositAmount)
                .remainingAmountVnd(remainingAmount)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .frozenAt(frozenAt)
                .createdAt(now)
                .build();

        try {
            return Optional.of(bills.saveAndFlush(bill));
        } catch (DataIntegrityViolationException duplicate) {
            return bills.findByBookingReference(ref);
        }
    }
}

