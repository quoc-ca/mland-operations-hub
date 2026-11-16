package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.common.settlement.TicketRemedyPendingPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketMlandRemedyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketMlandRemedy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Answers the Booking hint "this ticket has an offer waiting" from the Mland remedy table. */
@Component
@RequiredArgsConstructor
public class TicketRemedyPendingAdapter implements TicketRemedyPendingPort {
    private final SettlementTicketMlandRemedyRepository remedies;

    @Override
    @Transactional(readOnly = true)
    public Set<String> pendingTicketCodes(Collection<String> ticketCodes) {
        if (ticketCodes == null || ticketCodes.isEmpty()) return Set.of();
        return remedies.findByTicketCodeInAndOutcome(ticketCodes, "PENDING_ANSWER").stream()
                .map(SettlementTicketMlandRemedy::getTicketCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
