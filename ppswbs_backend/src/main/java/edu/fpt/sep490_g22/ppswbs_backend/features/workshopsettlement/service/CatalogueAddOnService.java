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

/** Records a fixed Operations catalogue snapshot only after Booking confirms customer authority. */
@Service
@RequiredArgsConstructor
public class CatalogueAddOnService {
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;

    @Transactional
    public SettlementCharge add(String bookingCode, Long memberAccountId, String contactEmail, String contactPhone,
                                String addOnReference) {
        var access = booking.verifyCustomerAccess(bookingCode, memberAccountId, contactEmail, contactPhone);
        if (access == null || !access.authorized()) throw new WorkshopSettlementNotFoundException();
        var bill = bills.findByBookingReference(bookingCode)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        if (!"OPEN".equals(bill.getLifecycleStatus())) throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        var confirmed = booking.confirmedProjection(bookingCode);
        if (confirmed == null) throw new WorkshopSettlementNotFoundException();
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
                .createdAt(Instant.now())
                .build());
    }
}
