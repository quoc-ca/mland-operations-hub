package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceTicketSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeItemDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GroupTicketChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 003.3 T118 (FR-044, FR-045, decided 2026-11-14): changes the sessions of several tickets of one booking group
 * together, all or none. Every ticket is checked first; one 003.2 {@code replaceTicketSessions} call carries every
 * line; each ticket spends its own allowance; the voucher-scope losses (asked of 006 per ticket) are summed and the
 * buyer confirms the total once. A ticket that a Manager rule blocks refuses the whole change (no queue here).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupTicketChangeService {

    private final WorkshopBookingFacade booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository finalBills;
    private final SettlementTicketChangeRepository changeRepository;
    private final FolioBalanceService folioBalanceService;
    private final TicketChangeService changes;

    private record Prepared(String ticketCode, TicketFolioContext ctx, SettlementFinalBill folio,
                            List<UUID> targets, long priceIncrease) {
    }

    @Transactional
    public GroupTicketChangeResultDto changeGroupTickets(String groupCode,
                                                         GroupTicketChangeRequestDto request,
                                                         String idempotencyKey,
                                                         String correlationId,
                                                         CurrentAccountActor actor) {
        changes.requireIdempotencyKey(idempotencyKey);
        if (groupCode == null || groupCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("GROUP_NOT_FOUND");
        }
        if (request == null || request.type() == null) {
            throw new WorkshopSettlementValidationException("TYPE_REQUIRED");
        }
        if (request.changes() == null || request.changes().isEmpty()) {
            throw new WorkshopSettlementValidationException("CHANGES_REQUIRED");
        }
        Set<String> seen = new HashSet<>();
        for (GroupTicketChangeItemDto item : request.changes()) {
            if (item == null || item.ticketCode() == null || item.ticketCode().isBlank()
                    || item.targetSessionReferences() == null || item.targetSessionReferences().isEmpty()) {
                throw new WorkshopSettlementValidationException("TARGET_SESSIONS_REQUIRED");
            }
            if (!seen.add(item.ticketCode().trim())) {
                throw new WorkshopSettlementValidationException("DUPLICATE_TICKET");
            }
        }
        String group = groupCode.trim();
        String key = idempotencyKey.trim();

        // Replay: every ticket of a committed group change carries its record under this key.
        List<ChangeResultDto> replay = replayOf(request, key);
        if (replay != null) {
            return new GroupTicketChangeResultDto(group, true, String.valueOf(totalOf(replay)), false, replay);
        }

        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // 1. Authority (FR-041, FR-055) for every ticket, then the checks of FR-044 for every ticket.
        List<Prepared> prepared = new ArrayList<>();
        List<String> failing = new ArrayList<>();
        String failureCode = null;
        TicketChangeService.Requester requester = null;
        for (GroupTicketChangeItemDto item : request.changes()) {
            String ticket = item.ticketCode().trim();
            TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                    .filter(found -> group.equals(found.groupCode()))
                    .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));
            requester = changes.authorizeRequester(actor, ctx, request.reason(), request.buyerVerificationReference());

            SettlementFinalBill folio = finalBills.findByBookingReference(ticket).orElse(null);
            String code = null;
            if (folio == null || !SettlementFinalBillLifecycleStatus.OPEN.name().equals(folio.getLifecycleStatus())) {
                code = "TICKET_FOLIO_NOT_OPEN";
            } else if (1 + changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(ticket, "MANAGER_GRANTED")
                    - changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket) <= 0) {
                code = TicketChangeService.RULE_ALLOWANCE;
            } else if (request.type() == TicketChangeType.ADVANCE && ctx.firstSessionStart() != null
                    && !now.isBefore(ctx.firstSessionStart().minus(24, ChronoUnit.HOURS))) {
                code = TicketChangeService.RULE_WINDOW;
            } else if (ctx.operationsTicketReference() == null || ctx.operationsTicketReference().isBlank()) {
                code = "TICKET_NOT_FOUND";
            }
            if (code != null) {
                failing.add(ticket);
                failureCode = failureCode == null ? code : failureCode;
                continue;
            }
            prepared.add(new Prepared(ticket, ctx, folio, item.targetSessionReferences(), 0L));
        }
        if (!failing.isEmpty()) {
            throw new GroupCancellationConflictException(failureCode, "One or more tickets cannot be changed", failing);
        }

        // 2. Price effect per ticket (FR-045); the buyer confirms the sum once.
        List<Prepared> priced = new ArrayList<>();
        long total = 0L;
        for (Prepared item : prepared) {
            long increase = changes.lostVoucherShare(item.ticketCode(), item.ctx(), item.targets());
            total = Math.addExact(total, increase);
            priced.add(new Prepared(item.ticketCode(), item.ctx(), item.folio(), item.targets(), increase));
        }
        boolean confirmed = Boolean.TRUE.equals(request.confirmIncrease());
        if (total > 0 && !confirmed) {
            List<ChangeResultDto> preview = new ArrayList<>();
            for (Prepared item : priced) {
                preview.add(new ChangeResultDto(item.ticketCode(), false, item.targets(),
                        String.valueOf(item.priceIncrease()), item.priceIncrease() > 0,
                        String.valueOf(folioBalanceService.balanceOf(item.folio()).balanceDue()), false));
            }
            return new GroupTicketChangeResultDto(group, false, String.valueOf(total), true, preview);
        }

        // 3. One 003.2 call carries every line, all or none.
        String opRef = "TGCHG-" + UUID.randomUUID();
        TicketReplacementType opType = request.type() == TicketChangeType.ADVANCE
                ? TicketReplacementType.CUSTOMER_ADVANCE : TicketReplacementType.CUSTOMER_LATE_ARRIVAL;
        List<ReplaceTicketSessionsCommand.Line> lines = priced.stream()
                .map(item -> new ReplaceTicketSessionsCommand.Line(
                        UUID.fromString(item.ctx().operationsTicketReference()), item.targets()))
                .toList();
        TicketSessionReplacementOutcome outcome = operations.replaceTicketSessions(
                new ReplaceTicketSessionsCommand(lines, opType, opRef, correlation));
        if (outcome.status() != OutcomeStatus.ACCEPTED) {
            List<String> blocking = outcome.failingLineIndexes().stream()
                    .filter(index -> index >= 0 && index < priced.size())
                    .map(index -> priced.get(index).ticketCode()).toList();
            throw new GroupCancellationConflictException(
                    outcome.reason() != null ? outcome.reason().name() : "REPLACEMENT_REJECTED",
                    "One or more tickets cannot be changed", blocking);
        }

        // 4. The buyer's proof is consumed once, then each ticket spends its own allowance.
        changes.consumeProof(priced.get(0).ctx(), requester);
        List<ChangeResultDto> results = new ArrayList<>();
        for (Prepared item : priced) {
            results.add(changes.recordAppliedChange(item.ticketCode(), item.ctx(), item.folio(), request.type(),
                    item.targets(), item.priceIncrease(), confirmed, requester, request.reason(), key,
                    "TCHG-" + UUID.randomUUID(), correlation, now));
        }
        changes.recordAudit(group, "GROUP_TICKET_CHANGE_APPLIED", correlation, requester.accountId(), requester.role());
        log.info("Changed {} ticket(s) of group {} together", results.size(), group);
        return new GroupTicketChangeResultDto(group, true, String.valueOf(total), false, results);
    }

    /** The stored results when every ticket already carries a change under this key, else null. */
    private List<ChangeResultDto> replayOf(GroupTicketChangeRequestDto request, String key) {
        Map<String, SettlementTicketChange> stored = new LinkedHashMap<>();
        for (GroupTicketChangeItemDto item : request.changes()) {
            Optional<SettlementTicketChange> found =
                    changeRepository.findByTicketCodeAndIdempotencyKey(item.ticketCode().trim(), key);
            if (found.isEmpty()) {
                return null;
            }
            stored.put(item.ticketCode().trim(), found.get());
        }
        return stored.values().stream().map(record -> new ChangeResultDto(
                record.getTicketCode(), record.isApplied(),
                Arrays.stream(record.getToSessionReferences().split(",")).map(UUID::fromString).toList(),
                String.valueOf(record.getPriceIncreaseAmountVnd()), false,
                String.valueOf(record.getBalanceDueSnapshot()), record.isCounted())).toList();
    }

    private static long totalOf(List<ChangeResultDto> results) {
        return results.stream().mapToLong(result -> Long.parseLong(result.priceIncreaseAmount())).sum();
    }
}
