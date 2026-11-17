package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens one OPEN folio per confirmed ticket booking of a group (003.3 T093, FR-030).
 *
 * <p>The folio reuses {@code settlement_final_bills} with the ticket code as its reference, so every charge,
 * counter collection, refund and finalization rule of 003.3 already applies per ticket without a second
 * model. Its base line is the ticket's own final price, its first collection is what the single group payment
 * collected for that ticket, and its balance is what checkout still has to collect.
 *
 * <p>The amounts come from 003.1's immutable per-ticket snapshot, carried on the confirmation event, so this
 * service never recomputes a price or re-reads a benefit. A free ticket and a ticket of a group deferred under
 * the VNPay floor open their folio with no payment evidence at all: collected 0, balance equal to the ticket
 * price, which is 0 for a free ticket.
 *
 * <p>Opening is idempotent per ticket: a replayed confirmation returns the folio that already exists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketFolioOpeningService {

    private final SettlementFinalBillRepository bills;

    @Transactional
    public Optional<SettlementFinalBill> openTicketFolio(String groupReference, String ticketReference,
                                                         long finalPriceVnd, long collectedAtBookingVnd,
                                                         long remainingVnd, Instant confirmedAt) {
        if (ticketReference == null || ticketReference.isBlank()) {
            return Optional.empty();
        }
        String ticket = ticketReference.trim();
        Optional<SettlementFinalBill> existing = bills.findByBookingReference(ticket);
        if (existing.isPresent()) {
            return existing;
        }
        if (finalPriceVnd < 0 || collectedAtBookingVnd < 0 || remainingVnd < 0
                || collectedAtBookingVnd > finalPriceVnd) {
            log.warn("Refusing to open a ticket folio for {}: amounts are not consistent "
                    + "(final={}, collected={}, remaining={})", ticket, finalPriceVnd, collectedAtBookingVnd,
                    remainingVnd);
            return Optional.empty();
        }
        // The snapshot owns the split; a balance that does not add up means the event was built wrongly.
        if (collectedAtBookingVnd + remainingVnd != finalPriceVnd) {
            log.warn("Refusing to open a ticket folio for {}: collected {} plus remaining {} is not the final "
                    + "price {}", ticket, collectedAtBookingVnd, remainingVnd, finalPriceVnd);
            return Optional.empty();
        }
        Instant now = Instant.now();
        SettlementFinalBill folio = SettlementFinalBill.builder()
                .billReference("SETTLE-BILL-" + UUID.randomUUID())
                .bookingReference(ticket)
                .groupReference(groupReference)
                .postPromotionAmountVnd(finalPriceVnd)
                .acceptedDepositAmountVnd(collectedAtBookingVnd)
                .remainingAmountVnd(remainingVnd)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .frozenAt(confirmedAt != null ? confirmedAt : now)
                .createdAt(now)
                .build();
        try {
            return Optional.of(bills.saveAndFlush(folio));
        } catch (DataIntegrityViolationException duplicate) {
            // Two deliveries of the same confirmation raced; the winner's folio is the answer.
            return bills.findByBookingReference(ticket);
        }
    }
}
