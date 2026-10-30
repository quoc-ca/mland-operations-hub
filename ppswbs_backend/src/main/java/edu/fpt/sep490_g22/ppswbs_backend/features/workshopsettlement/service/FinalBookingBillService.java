package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalBookingBillService {
    private final SettlementFinalBillRepository bills;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;

    @Transactional
    public SettlementFinalBill create(String bookingReference, String operationReference, String correlationReference) {
        require(bookingReference, "bookingReference");
        require(operationReference, "operationReference");
        require(correlationReference, "correlationReference");

        var existing = bills.findByBookingReference(bookingReference);
        if (existing.isPresent()) return existing.get();
        var readiness = operations.readiness(bookingReference);
        if (readiness == null || !readiness.ready()) {
            throw new WorkshopSettlementConflictException("SETTLEMENT_NOT_READY");
        }
        var projection = booking.confirmedProjection(bookingReference);
        if (projection == null || projection.frozenPostPromotionAmount() == null
                || projection.acceptedDepositAmount() == null || projection.frozenRemainingAmount() == null) {
            throw new WorkshopSettlementValidationException("FROZEN_FINANCIAL_PROJECTION_REQUIRED");
        }
        if (projection.frozenPostPromotionAmount() < 0 || projection.acceptedDepositAmount() < 0
                || projection.frozenRemainingAmount() < 0) {
            throw new WorkshopSettlementValidationException("FROZEN_FINANCIAL_PROJECTION_INVALID");
        }
        Instant now = Instant.now();
        return bills.saveAndFlush(SettlementFinalBill.builder()
                .billReference("SETTLE-BILL-" + UUID.randomUUID())
                .bookingReference(bookingReference)
                .postPromotionAmountVnd(projection.frozenPostPromotionAmount())
                .acceptedDepositAmountVnd(projection.acceptedDepositAmount())
                .remainingAmountVnd(projection.frozenRemainingAmount())
                .currencyCode("VND")
                .lifecycleStatus("OPEN")
                .frozenAt(now)
                .createdAt(now)
                .build());
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
    }
}
