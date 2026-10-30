package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VariableChargeService {
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;

    @Transactional
    public SettlementCharge add(String bookingReference, String description, Long amountVnd, String reason,
                                String operationReference) {
        require(bookingReference, "bookingReference");
        require(description, "description");
        require(reason, "reason");
        require(operationReference, "operationReference");
        if (amountVnd == null || amountVnd <= 0) {
            throw new WorkshopSettlementValidationException("amount must be a positive whole-VND amount");
        }
        var bill = bills.findByBookingReference(bookingReference)
                .orElseThrow(() -> new WorkshopSettlementValidationException("FINAL_BILL_NOT_FOUND"));
        if (!"OPEN".equals(bill.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }
        Instant now = Instant.now();
        return charges.saveAndFlush(SettlementCharge.builder()
                .chargeReference("SETTLE-CHARGE-" + UUID.randomUUID())
                .billReference(bill.getBillReference())
                .sourceCategory("VARIABLE_INTERNAL")
                .description(description.trim())
                .amountVnd(amountVnd)
                .currencyCode("VND")
                .lifecycleStatus("CONFIRMED")
                .reasonReference(reason.trim())
                .createdAt(now)
                .build());
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new WorkshopSettlementValidationException(name + " is required");
    }
}
