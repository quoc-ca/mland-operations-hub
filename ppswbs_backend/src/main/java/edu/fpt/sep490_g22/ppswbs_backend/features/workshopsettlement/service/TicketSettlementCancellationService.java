package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketCancellationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketCancellationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketCancellationDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketCancellation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 003.3 T099: Per-ticket customer cancellation service (FR-041, FR-042).
 * Handles single ticket and group-level (all-or-none) cancellations in one shared transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSettlementCancellationService {

    private final SettlementTicketCancellationRepository cancellationRepository;
    private final SettlementFinalBillRepository bills;
    private final SettlementBusinessAuditRepository audits;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final LoyaltyFacade loyalty;
    private final SettlementIdempotencyService idempotency;

    @Transactional
    public TicketCancellationDto cancelTicket(String ticketCode, CancellationRequestDto request,
                                            String idempotencyKey, String correlationId,
                                            CurrentAccountActor actor) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        TicketFolioContext context = booking.getTicketFolioContext(ticketCode.trim())
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));

        GroupCancellationRequestDto groupRequest = new GroupCancellationRequestDto(
                request.reason(),
                request.buyerVerificationReference(),
                List.of(ticketCode.trim())
        );

        GroupCancellationResponseDto response = cancelGroupInternal(
                context.groupCode(),
                groupRequest,
                idempotencyKey,
                correlationId,
                actor,
                true
        );

        return response.tickets().getFirst();
    }

    @Transactional
    public GroupCancellationResponseDto cancelGroup(String groupCode, GroupCancellationRequestDto request,
                                                    String idempotencyKey, String correlationId,
                                                    CurrentAccountActor actor) {
        return cancelGroupInternal(groupCode, request, idempotencyKey, correlationId, actor, false);
    }

    private GroupCancellationResponseDto cancelGroupInternal(String groupCode,
                                                             GroupCancellationRequestDto request,
                                                             String idempotencyKey,
                                                             String correlationId,
                                                             CurrentAccountActor actor,
                                                             boolean isSingleTicketRoute) {
        requireIdempotencyKey(idempotencyKey);
        String group = groupCode != null ? groupCode.trim() : "";
        List<String> ticketCodes = request.ticketCodes() == null ? List.of() : request.ticketCodes().stream().map(String::trim).toList();

        if (ticketCodes.isEmpty()) {
            throw new WorkshopSettlementValidationException("TICKET_CODES_REQUIRED");
        }
        if (Set.copyOf(ticketCodes).size() != ticketCodes.size()) {
            throw new WorkshopSettlementValidationException("DUPLICATE_TICKET_CODES");
        }

        String opRef = "CANCEL-" + idempotencyKey.trim();
        String fingerprint = digest(group + "|" + String.join(",", ticketCodes) + "|"
                + request.reason().trim() + "|" + (request.buyerVerificationReference() == null ? "" : request.buyerVerificationReference().trim()));

        // Check idempotent replay
        Optional<String> priorGroupRef = idempotency.findOutcomeReference(opRef, fingerprint);
        if (priorGroupRef.isPresent()) {
            List<SettlementTicketCancellation> stored = cancellationRepository.findByGroupRequestReferenceOrderByTicketCodeAsc(priorGroupRef.get());
            if (!stored.isEmpty()) {
                List<TicketCancellationDto> dtoList = stored.stream()
                        .map(c -> new TicketCancellationDto(c.getTicketCode(), c.getBenefitOutcome(), c.getPointsReturned(), c.isVoucherRestored()))
                        .toList();
                return new GroupCancellationResponseDto(stored.getFirst().getGroupCode(), dtoList);
            }
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String correlation = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId.trim();

        // 1. Validation phase (all or none)
        List<String> failingTickets = new ArrayList<>();
        Map<String, TicketFolioContext> contextMap = new LinkedHashMap<>();

        for (String ticketCode : ticketCodes) {
            Optional<TicketFolioContext> optContext = booking.getTicketFolioContext(ticketCode);
            if (optContext.isEmpty()) {
                if (isSingleTicketRoute) {
                    throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
                }
                failingTickets.add(ticketCode);
                continue;
            }
            TicketFolioContext ctx = optContext.get();
            if (!group.equals(ctx.groupCode())) {
                failingTickets.add(ticketCode);
                continue;
            }
            if (cancellationRepository.existsByTicketCode(ticketCode)) {
                if (isSingleTicketRoute) {
                    throw new WorkshopSettlementConflictException("TICKET_ALREADY_CANCELLED");
                }
                failingTickets.add(ticketCode);
                continue;
            }
            SettlementFinalBill bill = bills.findByBookingReference(ticketCode).orElse(null);
            if (bill == null || !SettlementFinalBillLifecycleStatus.OPEN.name().equals(bill.getLifecycleStatus())) {
                if (isSingleTicketRoute) {
                    throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
                }
                failingTickets.add(ticketCode);
                continue;
            }
            if (ctx.firstSessionStart() != null && !now.isBefore(ctx.firstSessionStart())) {
                if (isSingleTicketRoute) {
                    throw new WorkshopSettlementConflictException("SESSION_ALREADY_STARTED");
                }
                failingTickets.add(ticketCode);
                continue;
            }
            contextMap.put(ticketCode, ctx);
        }

        if (!failingTickets.isEmpty()) {
            throw new GroupCancellationConflictException(failingTickets);
        }

        // 2. Buyer proof authorization check
        TicketFolioContext sampleContext = contextMap.values().iterator().next();
        Long buyerMemberAccountId = sampleContext.buyerMemberAccountId();

        Long requesterAccountId = null;
        String requesterRole;
        String channel;
        String buyerProof;

        if (actor != null && actor.role() == ActorRole.MEMBER) {
            // Member must be the buyer who owns the group
            if (buyerMemberAccountId == null || !buyerMemberAccountId.equals(actor.accountId())) {
                throw new WorkshopSettlementAuthorizationException("NOT_GROUP_OWNER");
            }
            requesterAccountId = actor.accountId();
            requesterRole = "MEMBER";
            channel = "ONLINE";
            buyerProof = "MEMBER_OWNER";
        } else if (actor != null && (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER)) {
            // Staff at counter acting on buyer's behalf
            if (request.buyerVerificationReference() == null || request.buyerVerificationReference().isBlank()) {
                throw new WorkshopSettlementAuthorizationException("BUYER_VERIFICATION_REQUIRED");
            }
            requesterAccountId = actor.accountId();
            requesterRole = actor.role().name();
            channel = "STAFF";
            buyerProof = request.buyerVerificationReference().trim();
        } else {
            // Guest buyer
            if (request.buyerVerificationReference() == null || request.buyerVerificationReference().isBlank()) {
                throw new WorkshopSettlementAuthorizationException("BUYER_VERIFICATION_REQUIRED");
            }
            requesterRole = "GUEST";
            channel = "ONLINE";
            buyerProof = request.buyerVerificationReference().trim();
        }

        // 3. Consume buyer proof via 003.1 Facade (MANDATORY transaction)
        Long authMemberId = "MEMBER".equals(requesterRole) ? requesterAccountId : null;
        String authRef = "MEMBER".equals(requesterRole) ? null : buyerProof;

        ConsumeBuyerProofResult proofResult = booking.consumeBuyerProof(
                new ConsumeBuyerProofCommand(group, authMemberId, authRef)
        );
        if (!proofResult.isAccepted()) {
            if (ConsumeBuyerProofResult.CODE_EXPIRED.equals(proofResult.errorCode())) {
                throw new WorkshopSettlementConflictException("BUYER_VERIFICATION_EXPIRED");
            } else if (ConsumeBuyerProofResult.CODE_FAILED.equals(proofResult.errorCode())) {
                throw new WorkshopSettlementAuthorizationException("BUYER_VERIFICATION_FAILED");
            } else {
                throw new WorkshopSettlementAuthorizationException("BUYER_VERIFICATION_REQUIRED");
            }
        }

        // 4. Execution phase: cancel each ticket
        final Long effectiveRequesterAccountId = requesterAccountId;
        final String effectiveActorRef = requesterAccountId != null ? "account:" + requesterAccountId : "GUEST";
        String groupRequestReference = "GCAN-" + UUID.randomUUID().toString();
        List<TicketCancellationDto> resultDtos = new ArrayList<>();

        var loyaltySource = booking.groupLoyaltyOutcomeSource(group);
        boolean hasLoyalty = loyaltySource != null && loyaltySource.memberOrigin() && loyaltySource.snapshotRef() != null;

        for (String ticketCode : ticketCodes) {
            TicketFolioContext ctx = contextMap.get(ticketCode);
            String cancellationReference = "TCAN-" + UUID.randomUUID().toString();

            // 4a. 003.2 Operations capacity release
            if (ctx.operationsTicketReference() != null && !ctx.operationsTicketReference().isBlank()) {
                String opsOpRef = "CANCEL-" + ticketCode + "-" + idempotencyKey.trim();
                TicketCancellationOutcome opsOutcome = operations.cancelTicket(
                        UUID.fromString(ctx.operationsTicketReference()),
                        cancellationReference,
                        opsOpRef,
                        correlation
                );
                if (opsOutcome.status() != OutcomeStatus.ACCEPTED) {
                    throw new WorkshopSettlementConflictException("TICKET_CANCELLATION_REJECTED");
                }
            }

            // 4b. 24-hour evaluation (FR-042)
            boolean isValid = ctx.firstSessionStart() == null
                    || !now.isAfter(ctx.firstSessionStart().minus(24, ChronoUnit.HOURS));
            String benefitOutcome = isValid ? "VALID_CANCELLATION" : "LATE_CANCELLATION";
            long pointsReturned = isValid ? ctx.pointsUsed() : 0L;

            // 4c. Loyalty benefits
            boolean voucherRestoredOnTicket = false;
            if (hasLoyalty) {
                var beforeResult = findBenefitResultSafe(group);
                boolean voucherAlreadyRestored = beforeResult != null
                        && (beforeResult.voucherState() == ComponentState.RESTORED
                            || beforeResult.voucherState() == ComponentState.RELEASED);

                AuthoritativeOutcome authOutcome;
                BenefitResult afterResult;
                String partRef = ctx.partRef() != null ? ctx.partRef() : ticketCode;

                if (isValid) {
                    authOutcome = new AuthoritativeOutcome(
                            SourceKind.WORKSHOP,
                            group,
                            "workshopsettlement",
                            "valid-cancel-" + ticketCode,
                            loyaltySource.nextOutcomeVersion(),
                            OutcomeKind.WORKSHOP_TICKET_VALID_CANCELLATION,
                            now,
                            loyaltySource.originalDeadline(),
                            loyaltySource.snapshotRef(),
                            true,
                            "cancellation:" + ticketCode,
                            partRef
                    );
                    afterResult = loyalty.restoreCancelledWorkshopBenefits(authOutcome);
                } else {
                    authOutcome = new AuthoritativeOutcome(
                            SourceKind.WORKSHOP,
                            group,
                            "workshopsettlement",
                            "late-cancel-" + ticketCode,
                            loyaltySource.nextOutcomeVersion(),
                            OutcomeKind.WORKSHOP_TICKET_NO_SHOW_FINAL,
                            now,
                            loyaltySource.originalDeadline(),
                            loyaltySource.snapshotRef(),
                            true,
                            "cancellation:" + ticketCode,
                            partRef
                    );
                    afterResult = loyalty.finishIneligibleEarn(authOutcome);
                }

                if (afterResult != null && !voucherAlreadyRestored) {
                    voucherRestoredOnTicket = afterResult.voucherState() == ComponentState.RESTORED
                            || afterResult.voucherState() == ComponentState.RELEASED;
                }
            }

            // 4d. Close ticket folio without debt (FR-041)
            bills.findByBookingReferenceForUpdate(ticketCode).ifPresent(bill -> {
                bill.setLifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name());
                bills.save(bill);
                audits.save(SettlementBusinessAudit.builder()
                        .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                        .aggregateType("SETTLEMENT_FINAL_BILL")
                        .aggregateReference(bill.getBillReference())
                        .actionCode("TICKET_FOLIO_CLOSED_CUSTOMER_CANCELLATION")
                        .actorReference(effectiveActorRef)
                        .correlationReference(correlation)
                        .createdAt(now)
                        .build());
            });

            // 4e. Persist ticket cancellation record
            cancellationRepository.save(SettlementTicketCancellation.builder()
                    .cancellationReference(cancellationReference)
                    .groupRequestReference(groupRequestReference)
                    .ticketCode(ticketCode)
                    .groupCode(group)
                    .requesterAccountId(effectiveRequesterAccountId)
                    .requesterRole(requesterRole)
                    .channel(channel)
                    .buyerProof(buyerProof)
                    .reason(request.reason().trim())
                    .requestedAt(now)
                    .firstSessionStart(ctx.firstSessionStart() != null ? ctx.firstSessionStart() : now)
                    .benefitOutcome(benefitOutcome)
                    .pointsReturned(pointsReturned)
                    .voucherRestored(voucherRestoredOnTicket)
                    .createdAt(now)
                    .build());

            resultDtos.add(new TicketCancellationDto(ticketCode, benefitOutcome, pointsReturned, voucherRestoredOnTicket));
        }

        // 5. Complete idempotency claim
        idempotency.execute(opRef, fingerprint, () -> groupRequestReference);

        log.info("Cancelled {} tickets for group {} (groupRequestReference={})",
                resultDtos.size(), group, groupRequestReference);

        return new GroupCancellationResponseDto(group, resultDtos);
    }

    private BenefitResult findBenefitResultSafe(String groupCode) {
        try {
            return loyalty.findBenefitResult(SourceKind.WORKSHOP, groupCode);
        } catch (Exception ex) {
            return null;
        }
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.trim().length() < 16 || key.trim().length() > 128) {
            throw new WorkshopSettlementValidationException("IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
