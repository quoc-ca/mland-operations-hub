package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkshopLoyaltyOutcomeScheduler {
    private final SettlementFinalBillRepository bills;
    private final WorkshopLoyaltyOutcomeProcessor processor;

    @Scheduled(fixedDelayString = "${workshop.settlement.loyalty-outcome-delay-ms:60000}")
    public void processEligibleWorkshopOutcomes() {
        for (var bill : bills.findByLifecycleStatusInOrderByIdAsc(java.util.List.of("OPEN", "FINALIZED"))) {
            try {
                processor.process(bill.getBookingReference());
            } catch (RuntimeException exception) {
                // Exception text can contain provider/database details; keep logs to safe identifiers and type.
                log.warn("Workshop Loyalty outcome processing will retry for booking {} ({})",
                        bill.getBookingReference(), exception.getClass().getSimpleName());
            }
        }
    }
}
