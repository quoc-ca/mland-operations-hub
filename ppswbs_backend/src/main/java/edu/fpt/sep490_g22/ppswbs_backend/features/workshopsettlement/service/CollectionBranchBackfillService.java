package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One-off back-fill of {@code settlement_folio_collections.branch_id} for the transfer and VNPay rows created before
 * every collection carried its branch (003.3 T133, A12). The branch is the ticket's canonical
 * {@code workshop_location.id} read per ticket through the 003.1 facade {@code getTicketFolioContext}; there is no SQL
 * across modules. A row whose ticket cannot be resolved stays NULL and is returned so the caller can list it in the
 * evidence. Idempotent: it only touches rows whose branch is still NULL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionBranchBackfillService {

    private final SettlementFolioCollectionRepository collections;
    private final WorkshopBookingSettlementAdapter booking;

    /**
     * @param filled     rows that received a branch
     * @param unresolved collection references still NULL, with the ticket they belong to
     */
    public record Result(int filled, List<String> unresolved) {
    }

    @Transactional
    public Result backfill() {
        List<SettlementFolioCollection> missing = collections.findByBranchIdIsNullAndMethodIn(
                List.of(FolioCollectionMethod.BANK_TRANSFER, FolioCollectionMethod.VNPAY));
        Map<String, Optional<Long>> branchByTicket = new LinkedHashMap<>();
        int filled = 0;
        List<String> unresolved = new ArrayList<>();
        for (SettlementFolioCollection collection : missing) {
            Long branch = branchByTicket
                    .computeIfAbsent(collection.getBookingReference(), this::resolve)
                    .orElse(null);
            if (branch == null) {
                unresolved.add(collection.getCollectionReference() + " (ticket " + collection.getBookingReference() + ")");
                continue;
            }
            collection.setBranchId(branch);
            collections.save(collection);
            filled++;
        }
        if (!missing.isEmpty()) {
            log.info("Collection branch back-fill: {} filled, {} left NULL", filled, unresolved.size());
        }
        if (!unresolved.isEmpty()) {
            log.warn("Collection branch back-fill left {} row(s) NULL: {}", unresolved.size(), unresolved);
        }
        return new Result(filled, List.copyOf(unresolved));
    }

    private Optional<Long> resolve(String ticketCode) {
        try {
            return booking.getTicketFolioContext(ticketCode)
                    .map(TicketFolioContext::locationReference);
        } catch (RuntimeException e) {
            log.warn("Ticket {} could not be resolved for the branch back-fill: {}", ticketCode, e.toString());
            return Optional.empty();
        }
    }
}
