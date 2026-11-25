package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketChangeOptions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.AllowanceDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeOptionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeOptionsDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.FromSessionOptionsDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GuestChangeOptionLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.PairOptionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.SessionSeatsDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 003.3 FR-044a / T177: the sessions a ticket may move to, for the customer and for Staff. Read only: nothing is held,
 * consumed or recorded, and POST /changes re-checks every rule. 003.2 gives the sessions, seats and quota
 * ({@code TicketChangeOptions}); this service adds what 003.3 owns: who may read, the folio, the one-change allowance,
 * the 24 h window, whether a Manager is needed, and the price increase of each candidate (006 voucher scope).
 */
@Service
@RequiredArgsConstructor
public class TicketChangeOptionsApiService {

    private final WorkshopBookingFacade booking;
    private final WorkshopBookingSettlementAdapter access;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository finalBills;
    private final SettlementTicketChangeRepository changeRepository;
    private final TicketChangeService changes;
    private final WorkshopSettlementAuthorizationService authorization;

    /** Member who owns the group, Staff in the ticket's location assigned to one of its sessions, Manager. */
    @Transactional(readOnly = true)
    public ChangeOptionsDto forActor(String ticketCode, TicketChangeType type, List<UUID> from,
                                     CurrentAccountActor actor) {
        String code = requiredCode(ticketCode);
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));
        SettlementFinalBill folio = finalBills.findByBookingReference(code)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_FOLIO_NOT_FOUND"));
        if (actor == null) {
            throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
        }
        if (actor.role() == ActorRole.MEMBER) {
            if (ctx.buyerMemberAccountId() == null || !ctx.buyerMemberAccountId().equals(actor.accountId())) {
                throw new WorkshopSettlementAuthorizationException("NOT_BUYER");
            }
        } else if (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER) {
            authorization.requireManagerOrAssignedStaff(actor, ctx.locationReference(), ctx.lineReference());
        } else {
            throw new WorkshopSettlementAuthorizationException("ACTIVE_ACCOUNT_REQUIRED");
        }
        return build(code, ctx, folio, type, from);
    }

    /** Guest: one contact proof of the group, the same 404 for a wrong ticket and a wrong proof (as folio-lookups). */
    @Transactional(readOnly = true)
    public ChangeOptionsDto forGuest(String ticketCode, GuestChangeOptionLookupRequest request) {
        String code = requiredCode(ticketCode);
        if (request == null || !request.hasOneProof()) {
            throw new WorkshopSettlementValidationException("ONE_PROOF_REQUIRED");
        }
        TicketFolioContext ctx = booking.getTicketFolioContext(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        SettlementFinalBill folio = finalBills.findByBookingReference(code)
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        CustomerBookingAccessDecision decision = access.verifyCustomerAccess(code, null,
                request.contactEmail(), request.contactPhone());
        if (decision == null || !decision.authorized()) {
            throw new WorkshopSettlementNotFoundException();
        }
        return build(code, ctx, folio, request.type(), request.fromSessionReferences());
    }

    private ChangeOptionsDto build(String ticket, TicketFolioContext ctx, SettlementFinalBill folio,
                                   TicketChangeType type, List<UUID> requestedFrom) {
        if (type == null) {
            throw new WorkshopSettlementValidationException("TYPE_REQUIRED");
        }
        if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("TICKET_FOLIO_NOT_OPEN");
        }
        if (ctx.operationsTicketReference() == null || ctx.operationsTicketReference().isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        List<UUID> from = requestedFrom == null ? List.of() : requestedFrom.stream().filter(Objects::nonNull).toList();
        if (!from.isEmpty()) {
            List<String> own = ctx.sessionReferences() == null ? List.of() : ctx.sessionReferences();
            for (UUID reference : from) {
                if (own.stream().noneMatch(session -> session != null && session.equalsIgnoreCase(reference.toString()))) {
                    throw new WorkshopSettlementValidationException("FROM_SESSION_NOT_OF_TICKET");
                }
            }
        }
        TicketReplacementType opType = type == TicketChangeType.ADVANCE
                ? TicketReplacementType.CUSTOMER_ADVANCE : TicketReplacementType.CUSTOMER_LATE_ARRIVAL;
        TicketChangeOptions found = operations.ticketChangeOptions(
                UUID.fromString(ctx.operationsTicketReference()), opType, from);
        switch (found.status()) {
            case OK -> { }
            case TICKET_NOT_FOUND -> throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
            case TICKET_NOT_ACTIVE -> throw new WorkshopSettlementConflictException("TICKET_NOT_ACTIVE");
            case FROM_SESSION_NOT_OF_TICKET ->
                    throw new WorkshopSettlementValidationException("FROM_SESSION_NOT_OF_TICKET");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long used = changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket);
        long granted = changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(ticket, "MANAGER_GRANTED");
        long total = 1 + granted;
        long left = Math.max(0, total - used);
        boolean exhausted = left <= 0;

        List<TicketChangeOptions.FromSession> considered = found.perFromSession().stream()
                .filter(TicketChangeOptions.FromSession::sourceChangeable).toList();
        List<TicketChangeOptions.FromSession> basis = considered.isEmpty() ? found.perFromSession() : considered;
        Instant advanceDeadline = null;
        Instant lateDeadline = null;
        boolean windowOpen;
        if (type == TicketChangeType.ADVANCE) {
            advanceDeadline = basis.stream().map(TicketChangeOptions.FromSession::start).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).map(start -> start.minus(24, ChronoUnit.HOURS)).orElse(null);
            windowOpen = advanceDeadline != null && now.isBefore(advanceDeadline);
        } else {
            lateDeadline = basis.stream().map(TicketChangeOptions.FromSession::end).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .map(TicketChangeWindow::firstInstantAfterWindow).orElse(null);
            windowOpen = lateDeadline != null && now.isBefore(lateDeadline);
        }

        List<String> ticketRules = new ArrayList<>();
        if (exhausted) {
            ticketRules.add(TicketChangeService.RULE_ALLOWANCE);
        }
        if (type == TicketChangeType.ADVANCE && !windowOpen) {
            ticketRules.add(TicketChangeService.RULE_WINDOW);
        }

        List<FromSessionOptionsDto> perFrom = new ArrayList<>();
        for (TicketChangeOptions.FromSession item : found.perFromSession()) {
            List<ChangeOptionDto> options = new ArrayList<>();
            for (TicketChangeOptions.Option option : item.options()) {
                boolean selectable = option.reasonCode() == null;
                // A session without quota is never listed, so only the ticket's own rules can need a Manager here.
                List<String> rules = new ArrayList<>();
                if (selectable) {
                    rules.addAll(ticketRules);
                }
                options.add(new ChangeOptionDto(option.sessionReference(), option.start(), option.end(),
                        option.locationReference(), option.locationName(), option.remainingCapacity(), option.remainingRescheduleQuota(),
                        selectable, option.reasonCode(),
                        !rules.isEmpty(), List.copyOf(rules),
                        priceOf(ticket, ctx, List.of(item.sessionReference()), List.of(option.sessionReference()))));
            }
            perFrom.add(new FromSessionOptionsDto(item.sessionReference(), item.start(), item.end(),
                    item.sourceChangeable(), item.sourceReasonCode(), options));
        }

        List<PairOptionDto> pairs = new ArrayList<>();
        List<UUID> pairFrom = found.perFromSession().stream()
                .map(TicketChangeOptions.FromSession::sessionReference).toList();
        for (TicketChangeOptions.Pair pair : found.pairs()) {
            boolean selectable = pair.reasonCode() == null;
            List<String> rules = new ArrayList<>();
            if (selectable) {
                rules.addAll(ticketRules);
            }
            List<UUID> targets = List.of(pair.first().sessionReference(), pair.second().sessionReference());
            pairs.add(new PairOptionDto(targets, List.of(
                    new SessionSeatsDto(pair.first().sessionReference(), pair.first().start(), pair.first().end(),
                            pair.first().remainingCapacity(), pair.first().remainingRescheduleQuota(),
                            pair.first().locationReference(), pair.first().locationName()),
                    new SessionSeatsDto(pair.second().sessionReference(), pair.second().start(), pair.second().end(),
                            pair.second().remainingCapacity(), pair.second().remainingRescheduleQuota(),
                            pair.second().locationReference(), pair.second().locationName())), selectable, pair.reasonCode(), !rules.isEmpty(), List.copyOf(rules),
                    priceOf(ticket, ctx, pairFrom, targets)));
        }

        return new ChangeOptionsDto(ticket, type,
                new AllowanceDto(used, total, granted, left, exhausted), advanceDeadline, lateDeadline, windowOpen,
                !exhausted && windowOpen, perFrom, pairs.isEmpty() ? null : pairs);
    }

    /** The voucher discount the ticket would lose with this move (FR-045); 0 when 006 cannot tell. */
    private String priceOf(String ticket, TicketFolioContext ctx, List<UUID> from, List<UUID> targets) {
        try {
            List<UUID> newSet = changes.changeSet(ctx, from, targets).newSessions();
            return String.valueOf(changes.lostVoucherShare(ticket, ctx, newSet));
        } catch (WorkshopSettlementValidationException | WorkshopSettlementConflictException e) {
            return "0";
        }
    }

    private static String requiredCode(String ticketCode) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        return ticketCode.trim();
    }
}
