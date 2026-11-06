package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Records a fixed Operations catalogue snapshot after Staff/Manager confirms it at the workshop. */
@Service
@RequiredArgsConstructor
public class CatalogueAddOnService {
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;

    @Transactional
    public SettlementCharge add(String bookingCode, String addOnReference, Long recordedByAccountId) {
        var bill = bills.findByBookingReference(bookingCode)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!"OPEN".equals(bill.getLifecycleStatus())) throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        // The API service has already authenticated the Staff/Manager and resolved the booking.
        // Keep this service focused on snapshotting the eligible catalogue price into the open bill.
        var confirmed = booking.confirmedProjection(bookingCode);
        if (confirmed == null) throw new WorkshopSettlementNotFoundException();
        // Owner decision 2026-11-06: an add-on is confirmed "at the workshop", so the booking's session must have started.
        if (!operations.serviceStarted(bookingCode)) throw new WorkshopSettlementConflictException("SESSION_NOT_STARTED");
        var snapshot = operations.addOnPrice(confirmed.packageReference(), confirmed.bookingLocationReference(), addOnReference);
        if (snapshot == null) throw new WorkshopSettlementValidationException("ADD_ON_UNAVAILABLE");
        return charges.saveAndFlush(SettlementCharge.builder()
                .chargeReference("SETTLE-CHARGE-" + UUID.randomUUID())
                .billReference(bill.getBillReference())
                .sourceCategory("CATALOGUE_ADD_ON")
                .description(snapshot.description())
                .amountVnd(snapshot.amountVnd())
                .currencyCode(snapshot.currencyCode())
                .lifecycleStatus("CONFIRMED")
                .reasonReference("CATALOGUE-ADDON:" + snapshot.addOnReference())
                .consentReference("CONSENT-" + UUID.randomUUID())
                .consentRecordedBy(recordedByAccountId)
                .consentRecordedAt(Instant.now())
                .createdAt(Instant.now())
                .build());
    }
}
