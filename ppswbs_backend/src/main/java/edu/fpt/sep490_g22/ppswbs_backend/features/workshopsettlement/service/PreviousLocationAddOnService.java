package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * T187 (FR-060, decided 2026-11-17, Q3/Q4): after a ticket moves to another location the Manager can see which
 * catalogue add-ons were sold at the old location. Read only: nothing about the add-ons or their charges changes.
 * The location of an add-on is read through the 003.2 facade from the {@code CATALOGUE-ADDON:<ref>} reference of the
 * charge; a variable charge has no add-on and is never listed.
 */
@Service
@RequiredArgsConstructor
public class PreviousLocationAddOnService {

    static final String CATALOGUE_PREFIX = "CATALOGUE-ADDON:";

    private final WorkshopSettlementAuthorizationService authorization;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository bills;
    private final SettlementChargeRepository charges;

    public record PreviousLocationAddOnDto(String chargeReference, String catalogueAddOnReference, String description,
                                           String amount, Long addOnLocationReference,
                                           Long ticketLocationReference) {
    }

    @Transactional(readOnly = true)
    public List<PreviousLocationAddOnDto> list(String ticketCode, CurrentAccountActor actor) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        authorization.requireManager(actor);
        String code = ticketCode.trim();
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));
        var bill = bills.findByBookingReference(code)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_FOLIO_NOT_FOUND"));
        Long current = ctx.locationReference();
        return charges.findByBillReference(bill.getBillReference()).stream()
                .filter(charge -> "CATALOGUE_ADD_ON".equals(charge.getSourceCategory()))
                .filter(charge -> charge.getReasonReference() != null
                        && charge.getReasonReference().startsWith(CATALOGUE_PREFIX))
                .map(charge -> toDto(charge, current))
                .filter(Objects::nonNull)
                .toList();
    }

    private PreviousLocationAddOnDto toDto(SettlementCharge charge, Long current) {
        String addOnReference = charge.getReasonReference().substring(CATALOGUE_PREFIX.length());
        Long addOnLocation = operations.addOnLocation(addOnReference);
        if (addOnLocation == null || current == null || addOnLocation.equals(current)) {
            return null;
        }
        return new PreviousLocationAddOnDto(charge.getChargeReference(), addOnReference, charge.getDescription(),
                String.valueOf(charge.getAmountVnd()), addOnLocation, current);
    }
}
