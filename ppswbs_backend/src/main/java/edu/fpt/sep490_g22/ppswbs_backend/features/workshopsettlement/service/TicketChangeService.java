package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScopeQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceTicketSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeAllowanceResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GrantChangeAllowanceRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles per-ticket session changes (003.3 T101, FR-044, FR-045, FR-045a)
 * and Manager-granted change allowances (FR-054).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketChangeService {

    private final WorkshopBookingFacade booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository finalBills;
    private final SettlementTicketChangeRepository changeRepository;
    private final FolioBalanceService folioBalanceService;
    private final SettlementBusinessAuditRepository auditRepository;
    private final LoyaltyFacade loyalty;

    @Transactional
    public ChangeResultDto changeTicketSessions(String ticketCode,
                                                TicketChangeRequestDto request,
                                                String idempotencyKey,
                                                String correlationId,
                                                CurrentAccountActor actor) {
        requireIdempotencyKey(idempotencyKey);
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        String ticket = ticketCode.trim();

        // 1. Idempotent check
        Optional<SettlementTicketChange> optExisting = changeRepository.findByTicketCodeAndIdempotencyKey(ticket, idempotencyKey.trim());
        if (optExisting.isPresent()) {
            SettlementTicketChange existing = optExisting.get();
            List<UUID> targetUuids = existing.getToSessionReferences() == null || existing.getToSessionReferences().isBlank()
                    ? List.of()
                    : Arrays.stream(existing.getToSessionReferences().split(","))
                            .map(UUID::fromString)
                            .toList();
            return new ChangeResultDto(
                    existing.getTicketCode(),
                    existing.isApplied(),
                    targetUuids,
                    String.valueOf(existing.getPriceIncreaseAmountVnd()),
                    !existing.isApplied() && existing.getPriceIncreaseAmountVnd() > 0,
                    String.valueOf(existing.getBalanceDueSnapshot()),
                    existing.isCounted()
            );
        }

        // 2. Request validation
        if (request == null || request.type() == null) {
            throw new WorkshopSettlementValidationException("TYPE_REQUIRED");
        }
        if (request.targetSessionReferences() == null || request.targetSessionReferences().isEmpty()) {
            throw new WorkshopSettlementValidationException("TARGET_SESSIONS_REQUIRED");
        }

        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.trim();

        // 3. Load ticket context & folio
        TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));

        SettlementFinalBill folio = finalBills.findByBookingReference(ticket)
                .orElseThrow(() -> new WorkshopSettlementConflictException("TICKET_FOLIO_NOT_FOUND"));
        if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("TICKET_FOLIO_NOT_OPEN");
        }

        // 4. Buyer verification authorization check (FR-041 / FR-055)
        Long requesterAccountId = null;
        String requesterRole;
        String channel;
        String buyerProof;

        if (actor != null && actor.role() == ActorRole.MEMBER) {
            if (ctx.buyerMemberAccountId() == null || !ctx.buyerMemberAccountId().equals(actor.accountId())) {
                throw new WorkshopSettlementAuthorizationException("NOT_BUYER");
            }
            requesterAccountId = actor.accountId();
            requesterRole = "MEMBER";
            channel = "ONLINE";
            buyerProof = "MEMBER_OWNER";
        } else if (actor != null && (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER)) {
            // Staff acting on buyer's behalf at counter (FR-055)
            if (request.reason() == null || request.reason().isBlank()) {
                throw new WorkshopSettlementValidationException("REASON_REQUIRED");
            }
            if (request.buyerVerificationReference() == null || request.buyerVerificationReference().isBlank()) {
                throw new WorkshopSettlementConflictException("BUYER_VERIFICATION_REQUIRED");
            }
            requesterAccountId = actor.accountId();
            requesterRole = actor.role().name();
            channel = "STAFF";
            buyerProof = request.buyerVerificationReference().trim();
        } else {
            // Guest buyer
            if (request.buyerVerificationReference() == null || request.buyerVerificationReference().isBlank()) {
                throw new WorkshopSettlementConflictException("BUYER_VERIFICATION_REQUIRED");
            }
            requesterRole = "GUEST";
            channel = "ONLINE";
            buyerProof = request.buyerVerificationReference().trim();
        }

        // 5. Customer allowance check (FR-044, FR-054)
        long countedUsed = changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket);
        long managerGranted = changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(ticket, "MANAGER_GRANTED");
        long remainingAllowance = 1 + managerGranted - countedUsed;
        if (remainingAllowance <= 0) {
            throw new WorkshopSettlementConflictException("CHANGE_ALLOWANCE_EXHAUSTED");
        }

        // 6. Timing & window checks (FR-044, FR-045a)
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (request.type() == TicketChangeType.ADVANCE) {
            if (ctx.firstSessionStart() != null && !now.isBefore(ctx.firstSessionStart().minus(24, ChronoUnit.HOURS))) {
                throw new WorkshopSettlementConflictException("ADVANCE_CHANGE_WINDOW_EXPIRED");
            }
        }

        // 7. Price effect evaluation (FR-045)
        long priceIncreaseAmount = lostVoucherShare(ticket, ctx, request.targetSessionReferences());

        FolioBalanceService.FolioBalance currentBalance = folioBalanceService.balanceOf(folio);

        // If price increases and buyer has not yet confirmed increase:
        if (priceIncreaseAmount > 0 && !Boolean.TRUE.equals(request.confirmIncrease())) {
            return new ChangeResultDto(
                    ticket,
                    false, // not applied
                    request.targetSessionReferences(),
                    String.valueOf(priceIncreaseAmount),
                    true,  // confirmIncreaseRequired
                    String.valueOf(currentBalance.balanceDue()),
                    false  // countedChange
            );
        }

        // 8. Call 003.2 Operations to replace sessions
        TicketReplacementType opType = request.type() == TicketChangeType.ADVANCE
                ? TicketReplacementType.CUSTOMER_ADVANCE
                : TicketReplacementType.CUSTOMER_LATE_ARRIVAL;

        String opRef = "TCHG-" + UUID.randomUUID();
        UUID opsTicketRef = UUID.fromString(ctx.operationsTicketReference());
        ReplaceTicketSessionsCommand opCmd = new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(opsTicketRef, request.targetSessionReferences())),
                opType,
                opRef,
                correlation
        );

        TicketSessionReplacementOutcome outcome = operations.replaceTicketSessions(opCmd);
        if (outcome.status() != OutcomeStatus.ACCEPTED) {
            String code = outcome.reason() != null ? outcome.reason().name() : "REPLACEMENT_REJECTED";
            throw new WorkshopSettlementConflictException(code);
        }

        // 9. Consume buyer proof (only when change commits)
        if (!"MEMBER_OWNER".equals(buyerProof)) {
            Long authMemberId = "MEMBER".equals(requesterRole) ? requesterAccountId : null;
            ConsumeBuyerProofResult proofResult = booking.consumeBuyerProof(
                    new ConsumeBuyerProofCommand(ctx.groupCode(), authMemberId, buyerProof)
            );
            if (!proofResult.isAccepted()) {
                throw new WorkshopSettlementConflictException(proofResult.errorCode());
            }
        }

        // 10. Apply price increase to folio if confirmed
        if (priceIncreaseAmount > 0) {
            // FR-045 + FR-030: the increase goes to the ticket's checkout balance only; the folio's base and
            // post-promotion amounts stay as 003.1 snapshotted them.
            folio.setRemainingAmountVnd(Math.addExact(folio.getRemainingAmountVnd(), priceIncreaseAmount));
            finalBills.save(folio);
            recordAudit(ticket, "TICKET_CHANGE_PRICE_INCREASE", correlation, requesterAccountId, requesterRole);
        }

        FolioBalanceService.FolioBalance newBalance = folioBalanceService.balanceOf(folio);

        // 11. Persist ticket change record
        SettlementTicketChange record = SettlementTicketChange.builder()
                .changeReference(opRef)
                .idempotencyKey(idempotencyKey.trim())
                .ticketCode(ticket)
                .groupCode(ctx.groupCode())
                .changeType(request.type().name())
                .fromSessionReferences(ctx.sessionReferences() != null ? String.join(",", ctx.sessionReferences()) : "")
                .toSessionReferences(request.targetSessionReferences().stream().map(UUID::toString).collect(Collectors.joining(",")))
                .counted(true)
                .priceIncreaseAmountVnd(priceIncreaseAmount)
                .buyerConfirmedIncrease(Boolean.TRUE.equals(request.confirmIncrease()))
                .applied(true)
                .requesterAccountId(requesterAccountId)
                .requesterRole(requesterRole)
                .channel(channel)
                .buyerProof(buyerProof)
                .reason(request.reason())
                .balanceDueSnapshot(newBalance.balanceDue())
                .createdAt(now)
                .build();
        changeRepository.save(record);

        recordAudit(ticket, "TICKET_CHANGE_APPLIED", correlation, requesterAccountId, requesterRole);

        return new ChangeResultDto(
                ticket,
                true,
                request.targetSessionReferences(),
                String.valueOf(priceIncreaseAmount),
                false,
                String.valueOf(newBalance.balanceDue()),
                true
        );
    }

    @Transactional
    public ChangeAllowanceResponseDto grantExtraAllowance(String ticketCode,
                                                           GrantChangeAllowanceRequestDto request,
                                                           String idempotencyKey,
                                                           String correlationId,
                                                           CurrentAccountActor actor) {
        requireIdempotencyKey(idempotencyKey);
        if (actor == null || actor.role() != ActorRole.MANAGER) {
            throw new WorkshopSettlementAuthorizationException("MANAGER_ONLY");
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        String ticket = ticketCode.trim();

        // Idempotency check
        Optional<SettlementTicketChange> optExisting = changeRepository.findByTicketCodeAndIdempotencyKey(ticket, idempotencyKey.trim());
        if (optExisting.isPresent()) {
            SettlementTicketChange existing = optExisting.get();
            return new ChangeAllowanceResponseDto(
                    existing.getChangeReference(),
                    existing.getTicketCode(),
                    existing.getReason(),
                    existing.getCreatedAt()
            );
        }

        TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String allowanceRef = "CHG-ALW-" + UUID.randomUUID();
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.trim();

        SettlementTicketChange record = SettlementTicketChange.builder()
                .changeReference(allowanceRef)
                .idempotencyKey(idempotencyKey.trim())
                .ticketCode(ticket)
                .groupCode(ctx.groupCode())
                .changeType("MANAGER_GRANTED")
                .counted(false)
                .applied(true)
                .priceIncreaseAmountVnd(0L)
                .buyerConfirmedIncrease(false)
                .requesterAccountId(actor.accountId())
                .requesterRole("MANAGER")
                .channel("STAFF")
                .reason(request.reason().trim())
                .balanceDueSnapshot(0L)
                .createdAt(now)
                .build();
        changeRepository.save(record);

        recordAudit(ticket, "MANAGER_CHANGE_ALLOWANCE_GRANTED", correlation, actor.accountId(), "MANAGER");

        return new ChangeAllowanceResponseDto(
                allowanceRef,
                ticket,
                request.reason().trim(),
                now
        );
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private void recordAudit(String ticketCode, String action, String correlation, Long actorId, String role) {
        auditRepository.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-CHG-" + UUID.randomUUID())
                .aggregateType("TICKET_CHANGE")
                .aggregateReference(ticketCode)
                .actionCode(action)
                .actorReference(actorId != null ? String.valueOf(actorId) : role)
                .correlationReference(correlation)
                .build());
    }

    /**
     * FR-045 (BE-DESIGN-0007, decided 2026-11-14): 006 decides whether the ticket with its proposed sessions leaves the
     * accepted voucher's scope (package, branch, date, session; every session must match) and returns the ticket's
     * allocated share it then loses. 003.3 neither reads voucher targets nor recomputes the share. A ticket with no
     * programme discount has nothing to lose; an automatic discount is never lost (006 answers no voucher).
     */
    private long lostVoucherShare(String ticketCode, TicketFolioContext ctx, List<UUID> targetSessions) {
        if (ctx.voucherDiscountVnd() <= 0) {
            return 0L;
        }
        List<WorkshopSessionFact> proposed = targetSessions.stream().map(reference -> {
            SaleEligibilitySnapshot snap = operations.saleEligibility(reference);
            if (snap == null || snap.locationReference() == null || snap.workshopDate() == null) {
                throw new WorkshopSettlementConflictException("INVALID_REPLACEMENT");
            }
            return WorkshopSessionFact.of(reference.toString(), snap.locationReference(), snap.workshopDate());
        }).toList();
        String partRef = ctx.partRef() != null ? ctx.partRef() : ticketCode;
        TicketVoucherScope scope = loyalty.checkTicketVoucherScope(new TicketVoucherScopeQuery(SourceKind.WORKSHOP,
                ctx.groupCode(), partRef, Long.parseLong(ctx.packageReference()), proposed));
        return scope.lostDiscount().longValueExact();
    }
}

