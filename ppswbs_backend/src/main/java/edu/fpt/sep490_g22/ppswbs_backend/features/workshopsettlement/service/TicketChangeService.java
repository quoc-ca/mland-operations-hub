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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceTicketSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketReplacementType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeAllowanceResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GrantChangeAllowanceRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles per-ticket session changes (003.3 T101, FR-044, FR-045, FR-045a)
 * and Manager-granted change allowances (FR-054). T117: a change that fails only on a rule a Manager may lift
 * (reschedule quota, used allowance, 24 h window) is stored in the Staff/Manager queue instead of being refused.
 * T118 reuses the package-private helpers for several tickets of one group.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketChangeService {

    static final String RULE_ALLOWANCE = "CHANGE_ALLOWANCE_EXHAUSTED";
    static final String RULE_WINDOW = "ADVANCE_CHANGE_WINDOW_EXPIRED";
    static final String RULE_QUOTA = "RESCHEDULE_QUOTA_EXHAUSTED";

    private final WorkshopBookingFacade booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final SettlementFinalBillRepository finalBills;
    private final SettlementTicketChangeRepository changeRepository;
    private final FolioBalanceService folioBalanceService;
    private final SettlementBusinessAuditRepository auditRepository;
    private final LoyaltyFacade loyalty;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementTicketChangeRequestRepository requestRepository;

    /** Who asks for the change and how the buyer's authority was shown (FR-041 / FR-055). */
    record Requester(Long accountId, String role, String channel, String proof) {
        boolean needsProofConsumption() {
            return !"MEMBER_OWNER".equals(proof);
        }
    }

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
        String key = idempotencyKey.trim();

        // 1. Idempotent check
        Optional<SettlementTicketChange> optExisting = changeRepository.findByTicketCodeAndIdempotencyKey(ticket, key);
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
        Optional<SettlementTicketChangeRequest> optQueued = requestRepository.findByTicketCodeAndIdempotencyKey(ticket, key);
        if (optQueued.isPresent()) {
            return queuedResult(optQueued.get());
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
        Requester requester = authorizeRequester(actor, ctx, request.reason(), request.buyerVerificationReference());

        // 5. Rules a Manager may lift (FR-044, FR-045a): allowance and window. Collected, not thrown, so that a
        // request that fails only on them can wait in the queue (T117).
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<String> blocked = new ArrayList<>();
        long countedUsed = changeRepository.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket);
        long managerGranted = changeRepository.countByTicketCodeAndChangeTypeAndAppliedTrue(ticket, "MANAGER_GRANTED");
        if (1 + managerGranted - countedUsed <= 0) {
            blocked.add(RULE_ALLOWANCE);
        }
        if (request.type() == TicketChangeType.ADVANCE
                && ctx.firstSessionStart() != null && !now.isBefore(ctx.firstSessionStart().minus(24, ChronoUnit.HOURS))) {
            blocked.add(RULE_WINDOW);
        }

        // 6. Price effect evaluation (FR-045); the buyer confirms an increase before anything is applied or queued.
        long priceIncreaseAmount = lostVoucherShare(ticket, ctx, request.targetSessionReferences());
        FolioBalanceService.FolioBalance currentBalance = folioBalanceService.balanceOf(folio);
        boolean confirmed = Boolean.TRUE.equals(request.confirmIncrease());
        if (priceIncreaseAmount > 0 && !confirmed) {
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

        if (!blocked.isEmpty()) {
            return queueOrRefuse(ticket, ctx, request.type(), request.targetSessionReferences(), blocked,
                    priceIncreaseAmount, confirmed, requester, request.reason(), key, correlation, now, currentBalance);
        }

        // 7. Call 003.2 Operations to replace sessions
        String opRef = "TCHG-" + UUID.randomUUID();
        TicketSessionReplacementOutcome outcome = replaceSessions(ctx, request.type(), request.targetSessionReferences(),
                opRef, correlation);
        if (outcome.status() != OutcomeStatus.ACCEPTED) {
            if (outcome.reason() == RejectionReason.RESCHEDULE_QUOTA_EXHAUSTED) {
                // Only a Manager can lift a full quota, so the request waits in the queue (FR-045a).
                return queueOrRefuse(ticket, ctx, request.type(), request.targetSessionReferences(),
                        List.of(RULE_QUOTA), priceIncreaseAmount, confirmed, requester, request.reason(), key,
                        correlation, now, currentBalance);
            }
            String code = outcome.reason() != null ? outcome.reason().name() : "REPLACEMENT_REJECTED";
            throw new WorkshopSettlementConflictException(code);
        }

        // 8. Consume buyer proof (only when the change commits)
        consumeProof(ctx, requester);

        // 9-10. Apply the price increase and record the change
        return recordAppliedChange(ticket, ctx, folio, request.type(), request.targetSessionReferences(),
                priceIncreaseAmount, confirmed, requester, request.reason(), key, opRef, correlation, now);
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
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.trim();

        SettlementTicketChange record = saveGrant(ticket, ctx, idempotencyKey.trim(), actor.accountId(),
                request.reason().trim(), now);
        recordAudit(ticket, "MANAGER_CHANGE_ALLOWANCE_GRANTED", correlation, actor.accountId(), "MANAGER");

        return new ChangeAllowanceResponseDto(
                record.getChangeReference(),
                ticket,
                request.reason().trim(),
                now
        );
    }

    // ------------------------------------------------------------------------------------------------------
    // T117: the queue
    // ------------------------------------------------------------------------------------------------------

    private ChangeResultDto queueOrRefuse(String ticket, TicketFolioContext ctx, TicketChangeType type,
                                          List<UUID> targets, List<String> blocked, long priceIncrease,
                                          boolean confirmed, Requester requester, String reason, String key,
                                          String correlation, Instant now,
                                          FolioBalanceService.FolioBalance balance) {
        Instant expiresAt = requestExpiry(ctx, targets, now);
        if (expiresAt == null) {
            // Nothing left to wait for: the old and the new sessions have started, so refuse as before.
            throw new WorkshopSettlementConflictException(blocked.get(0));
        }
        expireStale(ticket, now);
        if (requestRepository.existsByTicketCodeAndStatus(ticket, SettlementTicketChangeRequest.PENDING)) {
            throw new WorkshopSettlementConflictException("CHANGE_REQUEST_PENDING");
        }
        // The buyer's authority covers this request; the Manager's approval needs no proof of the buyer's.
        consumeProof(ctx, requester);

        SettlementTicketChangeRequest saved = requestRepository.save(SettlementTicketChangeRequest.builder()
                .requestReference("CHG-REQ-" + UUID.randomUUID())
                .idempotencyKey(key)
                .ticketCode(ticket)
                .groupCode(ctx.groupCode())
                .locationReference(ctx.locationReference())
                .changeType(type.name())
                .targetSessionReferences(joinUuids(targets))
                .blockedRules(String.join(",", blocked))
                .status(SettlementTicketChangeRequest.PENDING)
                .priceIncreaseAmountVnd(priceIncrease)
                .buyerConfirmedIncrease(confirmed)
                .requesterAccountId(requester.accountId())
                .requesterRole(requester.role())
                .channel(requester.channel())
                .buyerProof(requester.proof())
                .reason(reason)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build());
        recordAudit(ticket, "TICKET_CHANGE_REQUEST_QUEUED", correlation, requester.accountId(), requester.role());
        log.info("Queued ticket change request {} for ticket {} (blocked by {})", saved.getRequestReference(),
                ticket, saved.getBlockedRules());
        return new ChangeResultDto(ticket, false, targets, String.valueOf(priceIncrease), false,
                String.valueOf(balance.balanceDue()), false, saved.getRequestReference());
    }

    private ChangeResultDto queuedResult(SettlementTicketChangeRequest request) {
        List<UUID> targets = Arrays.stream(request.getTargetSessionReferences().split(","))
                .map(UUID::fromString).toList();
        return new ChangeResultDto(request.getTicketCode(),
                SettlementTicketChangeRequest.APPROVED.equals(request.getStatus()), targets,
                String.valueOf(request.getPriceIncreaseAmountVnd()), false, "0",
                SettlementTicketChangeRequest.APPROVED.equals(request.getStatus()), request.getRequestReference());
    }

    /**
     * T117: the request waits until it can no longer be carried out, i.e. until the earlier of the old first session
     * and the earliest target session starts. For a late arrival the old session has already started, so the target
     * decides. Null when both lie in the past.
     */
    private Instant requestExpiry(TicketFolioContext ctx, List<UUID> targets, Instant now) {
        List<Instant> limits = new ArrayList<>();
        if (ctx.firstSessionStart() != null && ctx.firstSessionStart().isAfter(now)) {
            limits.add(ctx.firstSessionStart());
        }
        for (UUID target : targets) {
            SaleEligibilitySnapshot snapshot = operations.saleEligibility(target);
            if (snapshot != null && snapshot.scheduledStart() != null && snapshot.scheduledStart().isAfter(now)) {
                limits.add(snapshot.scheduledStart());
            }
        }
        return limits.stream().min(Comparator.naturalOrder()).orElse(null);
    }

    /** Marks this ticket's pending requests whose time has run out as EXPIRED. */
    void expireStale(String ticket, Instant now) {
        for (SettlementTicketChangeRequest pending
                : requestRepository.findByTicketCodeAndStatus(ticket, SettlementTicketChangeRequest.PENDING)) {
            if (!pending.getExpiresAt().isAfter(now)) {
                expire(pending, now);
            }
        }
    }

    void expire(SettlementTicketChangeRequest request, Instant now) {
        request.setStatus(SettlementTicketChangeRequest.EXPIRED);
        request.setUpdatedAt(now);
        requestRepository.save(request);
        recordAudit(request.getTicketCode(), "TICKET_CHANGE_REQUEST_EXPIRED", request.getRequestReference(), null, "SYSTEM");
    }

    /**
     * T117: approval is a Manager-granted allowance (FR-054) followed by one counted change, applied now. The 24 h
     * window and the allowance are what the Manager lifted; capacity, the package rules and a still-full quota are
     * still 003.2's call and refuse the approval (nothing is saved then).
     */
    String applyApproved(SettlementTicketChangeRequest request, CurrentAccountActor manager,
                                  String decisionReason, String correlation, Instant now) {
        String ticket = request.getTicketCode();
        TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));
        SettlementFinalBill folio = finalBills.findByBookingReference(ticket)
                .orElseThrow(() -> new WorkshopSettlementConflictException("TICKET_FOLIO_NOT_FOUND"));
        if (!SettlementFinalBillLifecycleStatus.OPEN.name().equals(folio.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("TICKET_FOLIO_NOT_OPEN");
        }
        List<UUID> targets = Arrays.stream(request.getTargetSessionReferences().split(","))
                .map(UUID::fromString).toList();
        TicketChangeType type = TicketChangeType.valueOf(request.getChangeType());

        saveGrant(ticket, ctx, "REQ-GRANT-" + request.getRequestReference(), manager.accountId(),
                decisionReason, now);
        recordAudit(ticket, "MANAGER_CHANGE_ALLOWANCE_GRANTED", correlation, manager.accountId(), "MANAGER");

        long priceIncrease = lostVoucherShare(ticket, ctx, targets);
        if (priceIncrease > 0 && !request.isBuyerConfirmedIncrease()) {
            // The scope loss appeared after the buyer asked; the buyer must confirm the new amount first.
            throw new WorkshopSettlementConflictException("CONFIRM_INCREASE_REQUIRED");
        }
        String opRef = "TCHG-" + UUID.randomUUID();
        TicketSessionReplacementOutcome outcome = replaceSessions(ctx, type, targets, opRef, correlation);
        if (outcome.status() != OutcomeStatus.ACCEPTED) {
            throw new WorkshopSettlementConflictException(
                    outcome.reason() != null ? outcome.reason().name() : "REPLACEMENT_REJECTED");
        }
        Requester original = new Requester(request.getRequesterAccountId(), request.getRequesterRole(),
                request.getChannel(), request.getBuyerProof());
        recordAppliedChange(ticket, ctx, folio, type, targets, priceIncrease,
                request.isBuyerConfirmedIncrease(), original, request.getReason(),
                "REQ-" + request.getRequestReference(), opRef, correlation, now);
        return opRef;
    }

    // ------------------------------------------------------------------------------------------------------
    // Shared steps (also used by the group change, T118)
    // ------------------------------------------------------------------------------------------------------

    /** Step 4: who is asking and how the buyer's authority is shown. */
    Requester authorizeRequester(CurrentAccountActor actor, TicketFolioContext ctx, String reason,
                                 String buyerVerificationReference) {
        if (actor != null && actor.role() == ActorRole.MEMBER) {
            if (ctx.buyerMemberAccountId() == null || !ctx.buyerMemberAccountId().equals(actor.accountId())) {
                throw new WorkshopSettlementAuthorizationException("NOT_BUYER");
            }
            return new Requester(actor.accountId(), "MEMBER", "ONLINE", "MEMBER_OWNER");
        }
        if (actor != null && (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER)) {
            // Staff acting on buyer's behalf at counter (FR-055); T116, FR-002/FR-051: active account and,
            // for Staff, the ticket's location in scope (Manager is global).
            authorization.requireManagerOrStaff(actor, ctx.locationReference());
            if (reason == null || reason.isBlank()) {
                throw new WorkshopSettlementValidationException("REASON_REQUIRED");
            }
            if (buyerVerificationReference == null || buyerVerificationReference.isBlank()) {
                throw new WorkshopSettlementConflictException("BUYER_VERIFICATION_REQUIRED");
            }
            return new Requester(actor.accountId(), actor.role().name(), "STAFF", buyerVerificationReference.trim());
        }
        // Guest buyer
        if (buyerVerificationReference == null || buyerVerificationReference.isBlank()) {
            throw new WorkshopSettlementConflictException("BUYER_VERIFICATION_REQUIRED");
        }
        return new Requester(null, "GUEST", "ONLINE", buyerVerificationReference.trim());
    }

    /** The buyer proof is consumed once, only when the request commits (queued or applied). */
    void consumeProof(TicketFolioContext ctx, Requester requester) {
        if (!requester.needsProofConsumption()) {
            return;
        }
        Long authMemberId = "MEMBER".equals(requester.role()) ? requester.accountId() : null;
        ConsumeBuyerProofResult proofResult = booking.consumeBuyerProof(
                new ConsumeBuyerProofCommand(ctx.groupCode(), authMemberId, requester.proof()));
        if (!proofResult.isAccepted()) {
            throw new WorkshopSettlementConflictException(proofResult.errorCode());
        }
    }

    TicketSessionReplacementOutcome replaceSessions(TicketFolioContext ctx, TicketChangeType type, List<UUID> targets,
                                                    String opRef, String correlation) {
        TicketReplacementType opType = type == TicketChangeType.ADVANCE
                ? TicketReplacementType.CUSTOMER_ADVANCE
                : TicketReplacementType.CUSTOMER_LATE_ARRIVAL;
        UUID opsTicketRef = UUID.fromString(ctx.operationsTicketReference());
        return operations.replaceTicketSessions(new ReplaceTicketSessionsCommand(
                List.of(new ReplaceTicketSessionsCommand.Line(opsTicketRef, targets)), opType, opRef, correlation));
    }

    /** Steps 10-11: the price increase goes to the checkout balance and the counted change is recorded. */
    ChangeResultDto recordAppliedChange(String ticket, TicketFolioContext ctx, SettlementFinalBill folio,
                                        TicketChangeType type, List<UUID> targets, long priceIncrease,
                                        boolean confirmed, Requester requester, String reason, String idempotencyKey,
                                        String changeReference, String correlation, Instant now) {
        if (priceIncrease > 0) {
            // FR-045 + FR-030: the increase goes to the ticket's checkout balance only; the folio's base and
            // post-promotion amounts stay as 003.1 snapshotted them.
            folio.setRemainingAmountVnd(Math.addExact(folio.getRemainingAmountVnd(), priceIncrease));
            finalBills.save(folio);
            recordAudit(ticket, "TICKET_CHANGE_PRICE_INCREASE", correlation, requester.accountId(), requester.role());
        }
        FolioBalanceService.FolioBalance newBalance = folioBalanceService.balanceOf(folio);

        changeRepository.save(SettlementTicketChange.builder()
                .changeReference(changeReference)
                .idempotencyKey(idempotencyKey)
                .ticketCode(ticket)
                .groupCode(ctx.groupCode())
                .changeType(type.name())
                .fromSessionReferences(ctx.sessionReferences() != null ? String.join(",", ctx.sessionReferences()) : "")
                .toSessionReferences(joinUuids(targets))
                .counted(true)
                .priceIncreaseAmountVnd(priceIncrease)
                .buyerConfirmedIncrease(confirmed)
                .applied(true)
                .requesterAccountId(requester.accountId())
                .requesterRole(requester.role())
                .channel(requester.channel())
                .buyerProof(requester.proof())
                .reason(reason)
                .balanceDueSnapshot(newBalance.balanceDue())
                .createdAt(now)
                .build());

        recordAudit(ticket, "TICKET_CHANGE_APPLIED", correlation, requester.accountId(), requester.role());
        return new ChangeResultDto(ticket, true, targets, String.valueOf(priceIncrease), false,
                String.valueOf(newBalance.balanceDue()), true);
    }

    private SettlementTicketChange saveGrant(String ticket, TicketFolioContext ctx, String idempotencyKey,
                                              Long managerAccountId, String reason, Instant now) {
        SettlementTicketChange grant = SettlementTicketChange.builder()
                .changeReference("CHG-ALW-" + UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .ticketCode(ticket)
                .groupCode(ctx.groupCode())
                .changeType("MANAGER_GRANTED")
                .counted(false)
                .applied(true)
                .priceIncreaseAmountVnd(0L)
                .buyerConfirmedIncrease(false)
                .requesterAccountId(managerAccountId)
                .requesterRole("MANAGER")
                .channel("STAFF")
                .reason(reason)
                .balanceDueSnapshot(0L)
                .createdAt(now)
                .build();
        changeRepository.save(grant);
        return grant;
    }

    static String joinUuids(List<UUID> values) {
        return values.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    void requireIdempotencyKey(String key) {
        if (key == null || key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    void recordAudit(String ticketCode, String action, String correlation, Long actorId, String role) {
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
    long lostVoucherShare(String ticketCode, TicketFolioContext ctx, List<UUID> targetSessions) {
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
