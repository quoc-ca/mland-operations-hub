package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.common.settlement.TicketBalancePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Answers Booking's "current money position" of a ticket from the folio, using the one balance rule. */
@Component
@RequiredArgsConstructor
public class TicketBalanceAdapter implements TicketBalancePort {
    private final SettlementFinalBillRepository bills;
    private final FolioBalanceService balances;

    @Override
    @Transactional(readOnly = true)
    public Map<String, TicketBalance> balancesOf(Collection<String> ticketCodes) {
        Map<String, TicketBalance> result = new HashMap<>();
        if (ticketCodes == null) return result;
        for (String code : ticketCodes) {
            SettlementFinalBill folio = bills.findByBookingReference(code).orElse(null);
            if (folio == null) continue;
            long addOn = balances.confirmedCharges(folio.getBillReference());
            long total = Math.addExact(folio.getPostPromotionAmountVnd(), addOn);
            long due = balances.balanceOf(folio).balanceDue();
            result.put(code, new TicketBalance(total, addOn, Math.max(0L, total - due), due));
        }
        return result;
    }
}
