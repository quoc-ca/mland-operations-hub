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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.*;
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
import java.util.stream.Stream;

/**
 * 003.3 T100: Per-ticket Mland cancellation remedy service (FR-043).
 * Replacement offer and buyer answer, refund by default when unanswered 24h before replacement,
 * 100% of ticket collected money by bank refund, points returned, voucher restored once even for partial group.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketMlandRemedyService {

    private final SettlementTicketMlandRemedyRepository remedyRepository;
    private final SettlementFinalBillRepository bills;
    private final SettlementFolioCollectionRepository collections;
    private final SettlementEvidenceConclusionRepository evidence;
    private final SettlementBusinessAuditRepository audits;
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final BankRefundService bankRefunds;
    private final LoyaltyFacade loyalty;
    private final SettlementCustomerNotifier notifier;
    private final SettlementIdempotencyService idempotency;

    @Transactional
    public SettlementTicketMlandRemedy createOffer(
            String ticketCode,
            String cancelledSessionReference,
            List<String> replacementSessionReferences,
            Instant replacementStartAt,
            String correlationReference
    ) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        TicketFolioContext ctx = booking.getTicketFolioContext(ticketCode.trim())
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));

        SettlementFinalBill bill = bills.findByBookingReference(ticketCode.trim()).orElse(null);
        if (bill == null || !SettlementFinalBillLifecycleStatus.OPEN.name().equals(bill.getLifecycleStatus())) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }

        var activeOpt = remedyRepository.findByTicketCodeAndOutcome(ticketCode.trim(), "PENDING_ANSWER");
        if (activeOpt.isPresent()) {
            return activeOpt.get();
        }

        if (replacementSessionReferences == null || replacementSessionReferences.isEmpty()) {
            throw new WorkshopSettlementValidationException("REPLACEMENT_SESSIONS_REQUIRED");
        }
        if (replacementStartAt == null) {
            throw new WorkshopSettlementValidationException("REPLACEMENT_START_REQUIRED");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant deadline = replacementStartAt.minus(24, ChronoUnit.HOURS);
        String remedyReference = "MREMEDY-" + UUID.randomUUID();
        String correlation = correlationReference != null && !correlationReference.isBlank()
                ? correlationReference.trim() : UUID.randomUUID().toString();

        SettlementTicketMlandRemedy remedy = remedyRepository.save(SettlementTicketMlandRemedy.builder()
                .remedyReference(remedyReference)
                .ticketCode(ticketCode.trim())
                .groupCode(ctx.groupCode())
                .cancelledSessionReference(cancelledSessionReference != null ? cancelledSessionReference.trim() : "")
                .replacementSessionReferences(String.join(",", replacementSessionReferences.stream().map(String::trim).toList()))
                .replacementStartAt(replacementStartAt)
                .answerDeadlineAt(deadline)
                .outcome("PENDING_ANSWER")
                .createdAt(now)
                .updatedAt(now)
                .build());

        notifier.notifyMlandRemedyOffer(ticketCode.trim(), cancelledSessionReference,
                String.join(",", replacementSessionReferences), deadline, correlation);

        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_TICKET_MLAND_REMEDY")
                .aggregateReference(remedyReference)
                .actionCode("MLAND_REMEDY_OFFER_CREATED")
                .correlationReference(correlation)
                .createdAt(now)
                .build());

        log.info("Created Mland remedy offer {} for ticket {} (deadline={})", remedyReference, ticketCode, deadline);
        return remedy;
    }

    @Transactional
    public TicketRemedyAnswerResponseDto answerOffer(
            String ticketCode,
            TicketRemedyAnswerRequestDto request,
            String idempotencyKey,
            String correlationId,
            CurrentAccountActor actor
    ) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        requireIdempotencyKey(idempotencyKey);
        if (request == null || request.answer() == null) {
            throw new WorkshopSettlementValidationException("ANSWER_REQUIRED");
        }

        String ticket = ticketCode.trim();
        TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));

        String opRef = "MREMEDY-ANSWER-" + idempotencyKey.trim();
        String fingerprint = digest(ticket + "|" + request.answer().name() + "|"
                + (request.buyerVerificationReference() == null ? "" : request.buyerVerificationReference().trim()));

        // Idempotency replay check
        Optional<String> priorRef = idempotency.findOutcomeReference(opRef, fingerprint);
        if (priorRef.isPresent()) {
            Optional<SettlementTicketMlandRemedy> storedOpt = remedyRepository.findByRemedyReference(priorRef.get());
            if (storedOpt.isPresent()) {
                return toResponseDto(storedOpt.get());
            }
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String correlation = correlationId != null && !correlationId.isBlank() ? correlationId.trim() : UUID.randomUUID().toString();

        SettlementTicketMlandRemedy offer = remedyRepository.findByTicketCodeAndOutcome(ticket, "PENDING_ANSWER")
                .orElse(null);

        if (offer == null) {
            List<SettlementTicketMlandRemedy> history = remedyRepository.findByTicketCodeOrderByCreatedAtDesc(ticket);
            if (!history.isEmpty()) {
                throw new WorkshopSettlementConflictException("REMEDY_ALREADY_ANSWERED");
            }
            throw new WorkshopSettlementNotFoundException("REMEDY_OFFER_NOT_FOUND");
        }

        // Authorize buyer proof
        Long buyerMemberAccountId = ctx.buyerMemberAccountId();
        Long requesterAccountId = null;
        String requesterRole;
        String channel;
        String buyerProof;

        if (actor != null && actor.role() == ActorRole.MEMBER) {
            if (buyerMemberAccountId == null || !buyerMemberAccountId.equals(actor.accountId())) {
                throw new WorkshopSettlementAuthorizationException("NOT_GROUP_OWNER");
            }
            requesterAccountId = actor.accountId();
            requesterRole = "MEMBER";
            channel = "ONLINE";
            buyerProof = "MEMBER_OWNER";
        } else if (actor != null && (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER)) {
            if (request.buyerVerificationReference() == null || request.buyerVerificationReference().isBlank()) {
                throw new WorkshopSettlementAuthorizationException("BUYER_VERIFICATION_REQUIRED");
            }
            requesterAccountId = actor.accountId();
            requesterRole = actor.role().name();
            channel = "STAFF";
            buyerProof = request.buyerVerificationReference().trim();
        } else {
            if (request.buyerVerificationReference() == null || request.buyerVerificationReference().isBlank()) {
                throw new WorkshopSettlementAuthorizationException("BUYER_VERIFICATION_REQUIRED");
            }
            requesterRole = "GUEST";
            channel = "ONLINE";
            buyerProof = request.buyerVerificationReference().trim();
        }

        // Consume buyer proof via 003.1 Facade (MANDATORY transaction)
        Long authMemberId = "MEMBER".equals(requesterRole) ? requesterAccountId : null;
        String authRef = "MEMBER".equals(requesterRole) ? null : buyerProof;

        ConsumeBuyerProofResult proofResult = booking.consumeBuyerProof(
                new ConsumeBuyerProofCommand(ctx.groupCode(), authMemberId, authRef)
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

        TicketRemedyAnswerResponseDto responseDto;

        if (request.answer() == TicketRemedyAnswer.ACCEPT_REPLACEMENT) {
            // Cannot accept replacement within 24 hours of replacement start
            if (now.isAfter(offer.getAnswerDeadlineAt())) {
                throw new WorkshopSettlementConflictException("ANSWER_DEADLINE_EXPIRED");
            }

            // Replace sessions in 003.2 Operations
            List<UUID> replacementUuids = Arrays.stream(offer.getReplacementSessionReferences().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .toList();

            if (ctx.operationsTicketReference() != null && !ctx.operationsTicketReference().isBlank()) {
                ReplaceTicketSessionsCommand cmd = new ReplaceTicketSessionsCommand(
                        List.of(new ReplaceTicketSessionsCommand.Line(
                                UUID.fromString(ctx.operationsTicketReference()),
                                replacementUuids
                        )),
                        TicketReplacementType.MLAND,
                        "MLAND-REPLACE-" + ticket + "-" + idempotencyKey.trim(),
                        correlation
                );
                TicketSessionReplacementOutcome outcome = operations.replaceTicketSessions(cmd);
                if (outcome.status() != OutcomeStatus.ACCEPTED) {
                    throw new WorkshopSettlementConflictException("TICKET_REPLACEMENT_REJECTED: " + outcome.reason());
                }
            }

            offer.setBuyerAnswer(TicketRemedyAnswer.ACCEPT_REPLACEMENT.name());
            offer.setOutcome("MOVED");
            offer.setAnsweredAt(now);
            offer.setRequesterAccountId(requesterAccountId);
            offer.setRequesterRole(requesterRole);
            offer.setChannel(channel);
            offer.setBuyerProof(buyerProof);
            offer.setUpdatedAt(now);
            remedyRepository.save(offer);

            audits.save(SettlementBusinessAudit.builder()
                    .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                    .aggregateType("SETTLEMENT_TICKET_MLAND_REMEDY")
                    .aggregateReference(offer.getRemedyReference())
                    .actionCode("MLAND_REMEDY_ACCEPTED_REPLACEMENT")
                    .actorReference(requesterAccountId != null ? "account:" + requesterAccountId : requesterRole)
                    .correlationReference(correlation)
                    .createdAt(now)
                    .build());

            responseDto = toResponseDto(offer);

        } else {
            // Buyer chooses REFUND
            responseDto = executeRemedyRefund(
                    offer,
                    ctx,
                    requesterAccountId,
                    requesterRole,
                    channel,
                    buyerProof,
                    idempotencyKey,
                    correlation,
                    now
            );
        }

        // Complete idempotency claim
        idempotency.execute(opRef, fingerprint, offer::getRemedyReference);

        return responseDto;
    }

    /**
     * Executes the 100% bank refund, ticket release, points return, and voucher restore for Mland remedy.
     */
    private TicketRemedyAnswerResponseDto executeRemedyRefund(
            SettlementTicketMlandRemedy offer,
            TicketFolioContext ctx,
            Long requesterAccountId,
            String requesterRole,
            String channel,
            String buyerProof,
            String idempotencyKey,
            String correlation,
            Instant now
    ) {
        String ticketCode = offer.getTicketCode();

        // 1. Calculate 100% collected money
        long counterCollections = collections.findByBookingReferenceOrderByIdAsc(ticketCode).stream()
                .filter(c -> c.getStatus() == FolioCollectionStatus.CONFIRMED)
                .mapToLong(SettlementFolioCollection::getAmountVnd)
                .sum();
        long bookingDeposit = bills.findByBookingReference(ticketCode)
                .map(SettlementFinalBill::getAcceptedDepositAmountVnd)
                .orElse(0L);
        long gatewayCollections = Stream.of("FINAL_PAYMENT_VERIFIED", "SHOP_ADDON_VERIFIED")
                .flatMap(c -> evidence.findByBookingReferenceAndConclusion(ticketCode, c).stream())
                .mapToLong(e -> e.getAmountVnd() == null ? 0L : e.getAmountVnd())
                .sum();
        long totalCollected = Math.addExact(bookingDeposit, Math.addExact(gatewayCollections, counterCollections));

        // 2. Open bank refund for 100% collected money
        String refundRequestRef = null;
        if (totalCollected > 0) {
            BankRefundService.OpenedRefund opened = bankRefunds.open(
                    ticketCode,
                    ctx.groupCode(),
                    RefundReason.MLAND_CANCELLATION,
                    offer.getRemedyReference(),
                    totalCollected,
                    correlation
            );
            refundRequestRef = opened.request().getRequestReference();
        }

        // 3. Cancel ticket entitlements in 003.2 Operations
        if (ctx.operationsTicketReference() != null && !ctx.operationsTicketReference().isBlank()) {
            String opsOpRef = "MLAND-CANCEL-" + ticketCode + "-" + (idempotencyKey != null ? idempotencyKey.trim() : UUID.randomUUID().toString());
            TicketCancellationOutcome opsOutcome = operations.cancelTicket(
                    UUID.fromString(ctx.operationsTicketReference()),
                    offer.getRemedyReference(),
                    opsOpRef,
                    correlation
            );
            if (opsOutcome.status() != OutcomeStatus.ACCEPTED) {
                log.warn("Operations cancel ticket rejected for ticketCode={}: {}", ticketCode, opsOutcome.reason());
            }
        }

        // 4. Return points and restore voucher once via Loyalty
        long pointsReturned = 0L;
        boolean voucherRestoredOnTicket = false;
        var loyaltySource = booking.groupLoyaltyOutcomeSource(ctx.groupCode());
        boolean hasLoyalty = loyaltySource != null && loyaltySource.memberOrigin() && loyaltySource.snapshotRef() != null;
        if (hasLoyalty) {
            var beforeResult = findBenefitResultSafe(ctx.groupCode());
            boolean voucherAlreadyRestored = beforeResult != null
                    && (beforeResult.voucherState() == ComponentState.RESTORED
                        || beforeResult.voucherState() == ComponentState.RELEASED);

            String partRef = ctx.partRef() != null ? ctx.partRef() : ticketCode;
            AuthoritativeOutcome authOutcome = new AuthoritativeOutcome(
                    SourceKind.WORKSHOP,
                    ctx.groupCode(),
                    "workshopsettlement",
                    "mland-remedy-" + ticketCode,
                    loyaltySource.nextOutcomeVersion(),
                    OutcomeKind.WORKSHOP_TICKET_MLAND_CANCELLATION,
                    now,
                    loyaltySource.originalDeadline(),
                    loyaltySource.snapshotRef(),
                    true,
                    "mland-remedy:" + ticketCode,
                    partRef
            );
            BenefitResult afterResult = loyalty.restoreCancelledWorkshopBenefits(authOutcome);
            pointsReturned = ctx.pointsUsed();
            if (afterResult != null && !voucherAlreadyRestored) {
                voucherRestoredOnTicket = afterResult.voucherState() == ComponentState.RESTORED
                        || afterResult.voucherState() == ComponentState.RELEASED;
            }
        }

        // 5. Close ticket folio without debt
        final String effectiveActorRef = requesterAccountId != null ? "account:" + requesterAccountId
                : (requesterRole != null ? requesterRole : "SYSTEM");
        bills.findByBookingReferenceForUpdate(ticketCode).ifPresent(bill -> {
            bill.setLifecycleStatus(SettlementFinalBillLifecycleStatus.CLOSED.name());
            bills.save(bill);
            audits.save(SettlementBusinessAudit.builder()
                    .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                    .aggregateType("SETTLEMENT_FINAL_BILL")
                    .aggregateReference(bill.getBillReference())
                    .actionCode("TICKET_FOLIO_CLOSED_MLAND_REMEDY")
                    .actorReference(effectiveActorRef)
                    .correlationReference(correlation)
                    .createdAt(now)
                    .build());
        });

        // 6. Update remedy entity
        offer.setBuyerAnswer(requesterRole != null ? TicketRemedyAnswer.REFUND.name() : null);
        offer.setOutcome("REFUND");
        offer.setAnsweredAt(now);
        offer.setRefundRequestReference(refundRequestRef);
        offer.setPointsReturned(pointsReturned);
        offer.setVoucherRestored(voucherRestoredOnTicket);
        offer.setRequesterAccountId(requesterAccountId);
        offer.setRequesterRole(requesterRole);
        offer.setChannel(channel);
        offer.setBuyerProof(buyerProof);
        offer.setUpdatedAt(now);
        remedyRepository.save(offer);

        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_TICKET_MLAND_REMEDY")
                .aggregateReference(offer.getRemedyReference())
                .actionCode(requesterRole != null ? "MLAND_REMEDY_CHOSEN_REFUND" : "MLAND_REMEDY_DEFAULTED_TO_REFUND")
                .actorReference(effectiveActorRef)
                .correlationReference(correlation)
                .createdAt(now)
                .build());

        log.info("Resolved Mland remedy {} for ticket {} with outcome REFUND (refundRef={}, pointsReturned={}, voucherRestored={})",
                offer.getRemedyReference(), ticketCode, refundRequestRef, pointsReturned, voucherRestoredOnTicket);

        return toResponseDto(offer);
    }

    /**
     * Default unanswered remedies to refund when 24h before replacement session starts (FR-043).
     */
    @Transactional
    public int processUnansweredRemedies() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<SettlementTicketMlandRemedy> expired = remedyRepository.findByOutcomeAndAnswerDeadlineAtLessThanEqual(
                "PENDING_ANSWER", now
        );
        int count = 0;
        for (SettlementTicketMlandRemedy remedy : expired) {
            try {
                defaultSingleRemedy(remedy, now);
                count++;
            } catch (Exception ex) {
                log.error("Failed to default remedy to refund for remedyReference={}: {}",
                        remedy.getRemedyReference(), ex.getMessage(), ex);
            }
        }
        return count;
    }

    @Transactional
    public TicketRemedyAnswerResponseDto defaultSingleRemedy(SettlementTicketMlandRemedy remedy, Instant now) {
        if (!"PENDING_ANSWER".equals(remedy.getOutcome())) {
            return toResponseDto(remedy);
        }
        TicketFolioContext ctx = booking.getTicketFolioContext(remedy.getTicketCode())
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));

        return executeRemedyRefund(
                remedy,
                ctx,
                null,
                null,
                "SYSTEM",
                null,
                "DEFAULT-REFUND-" + remedy.getRemedyReference(),
                UUID.randomUUID().toString(),
                now != null ? now : Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private TicketRemedyAnswerResponseDto toResponseDto(SettlementTicketMlandRemedy entity) {
        TicketRemedyAnswer answer = entity.getBuyerAnswer() != null
                ? TicketRemedyAnswer.valueOf(entity.getBuyerAnswer())
                : null;
        return new TicketRemedyAnswerResponseDto(
                entity.getRemedyReference(),
                entity.getTicketCode(),
                entity.getGroupCode(),
                answer,
                entity.getOutcome(),
                entity.getAnsweredAt(),
                entity.getRefundRequestReference(),
                entity.getPointsReturned(),
                entity.isVoucherRestored()
        );
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

