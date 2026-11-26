package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.RestoreVoucherCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketCancellationKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.VoucherRestorationOutcome;
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
    private final WorkshopSettlementAuthorizationService authorization;
    private final CancellationHandoffCompletionService handoffCompletion;
    private final MlandCancelledFolioCloser folioCloser;

    /** One session of an offer (T112): the buyer chooses exactly one of them. */
    public record ReplacementOption(String sessionReference, Instant startAt, Instant endAt, Long locationReference,
                                      String locationName) {
        public ReplacementOption(String sessionReference, Instant startAt, Instant endAt) {
            this(sessionReference, startAt, endAt, null, null);
        }
    }

    /** The buyer must answer at least this long before the chosen session starts (FR-043). */
    private static final long ANSWER_LEAD_HOURS = 24;

    private static final java.time.ZoneId DISPLAY_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    private static final java.time.format.DateTimeFormatter DISPLAY_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Offer with one start time shared by the listed sessions (kept for callers that know a single start). */
    @Transactional
    public SettlementTicketMlandRemedy createOffer(
            String ticketCode,
            String cancelledSessionReference,
            List<String> replacementSessionReferences,
            Instant replacementStartAt,
            String correlationReference
    ) {
        List<ReplacementOption> options = replacementSessionReferences == null ? List.of()
                : replacementSessionReferences.stream()
                .map(reference -> new ReplacementOption(reference, replacementStartAt, null)).toList();
        return createOffer(ticketCode, cancelledSessionReference, options, correlationReference);
    }

    /**
     * T112, FR-043 (decided 2026-11-14): offers EVERY valid equivalent session of one ticket; the buyer chooses one.
     * {@code answerDeadlineAt} is 24 h before the LATEST offered session, i.e. when no session can still be chosen.
     * Idempotent per ticket and cancelled session.
     */
    @Transactional
    public SettlementTicketMlandRemedy createOffer(
            String ticketCode,
            String cancelledSessionReference,
            List<ReplacementOption> options,
            String correlationReference
    ) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        String ticket = ticketCode.trim();
        TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));
        String cancelled = cancelledSessionReference != null ? cancelledSessionReference.trim() : "";

        var sameCancellation = remedyRepository
                .findFirstByTicketCodeAndCancelledSessionReferenceOrderByCreatedAtDesc(ticket, cancelled);
        if (sameCancellation.isPresent()) {
            return sameCancellation.get();
        }

        SettlementFinalBill bill = bills.findByBookingReference(ticket).orElse(null);
        if (bill == null || !isRemediable(bill)) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }

        var activeOpt = remedyRepository.findByTicketCodeAndOutcome(ticket, "PENDING_ANSWER");
        if (activeOpt.isPresent()) {
            return activeOpt.get();
        }

        if (options == null || options.isEmpty()) {
            throw new WorkshopSettlementValidationException("REPLACEMENT_SESSIONS_REQUIRED");
        }
        if (options.stream().anyMatch(option -> option == null || option.sessionReference() == null
                || option.sessionReference().isBlank())) {
            throw new WorkshopSettlementValidationException("REPLACEMENT_SESSIONS_REQUIRED");
        }
        if (options.stream().anyMatch(option -> option.startAt() == null)) {
            throw new WorkshopSettlementValidationException("REPLACEMENT_START_REQUIRED");
        }

        List<ReplacementOption> ordered = options.stream()
                .map(option -> new ReplacementOption(option.sessionReference().trim(), option.startAt(), option.endAt(),
                        option.locationReference(), option.locationName()))
                .sorted(Comparator.comparing(ReplacementOption::startAt).thenComparing(ReplacementOption::sessionReference))
                .toList();
        Instant latestStart = ordered.get(ordered.size() - 1).startAt();

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant deadline = latestStart.minus(ANSWER_LEAD_HOURS, ChronoUnit.HOURS);
        String remedyReference = "MREMEDY-" + UUID.randomUUID();
        String correlation = correlationReference != null && !correlationReference.isBlank()
                ? correlationReference.trim() : UUID.randomUUID().toString();

        SettlementTicketMlandRemedy remedy = remedyRepository.save(SettlementTicketMlandRemedy.builder()
                .remedyReference(remedyReference)
                .ticketCode(ticket)
                .groupCode(ctx.groupCode())
                .cancelledSessionReference(cancelled)
                .replacementSessionReferences(String.join(",", ordered.stream()
                        .map(ReplacementOption::sessionReference).toList()))
                .replacementOptions(encodeOptions(ordered))
                .replacementStartAt(latestStart)
                .answerDeadlineAt(deadline)
                .outcome("PENDING_ANSWER")
                .createdAt(now)
                .updatedAt(now)
                .build());

        notifier.notifyMlandRemedyOffer(ticket, cancelledSessionReference, describeOptions(ordered), deadline, correlation);

        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_TICKET_MLAND_REMEDY")
                .aggregateReference(remedyReference)
                .actionCode("MLAND_REMEDY_OFFER_CREATED")
                .correlationReference(correlation)
                .createdAt(now)
                .build());

        log.info("Created Mland remedy offer {} for ticket {} ({} session(s), deadline={})", remedyReference, ticket,
                ordered.size(), deadline);
        return remedy;
    }

    /**
     * T112, FR-043: 003.2 found no equivalent session, so the 100% bank refund opens at once (no offer is waited
     * on). Idempotent per ticket and cancelled session.
     */
    @Transactional
    public SettlementTicketMlandRemedy openDirectRefund(
            String ticketCode,
            String cancelledSessionReference,
            String correlationReference
    ) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        String ticket = ticketCode.trim();
        TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));
        String cancelled = cancelledSessionReference != null ? cancelledSessionReference.trim() : "";

        var sameCancellation = remedyRepository
                .findFirstByTicketCodeAndCancelledSessionReferenceOrderByCreatedAtDesc(ticket, cancelled);
        if (sameCancellation.isPresent()) {
            return sameCancellation.get();
        }
        SettlementFinalBill bill = bills.findByBookingReference(ticket).orElse(null);
        if (bill == null || !isRemediable(bill)) {
            throw new WorkshopSettlementConflictException("FINAL_BILL_NOT_OPEN");
        }
        var pending = remedyRepository.findByTicketCodeAndOutcome(ticket, "PENDING_ANSWER");
        if (pending.isPresent()) {
            return pending.get();
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String remedyReference = "MREMEDY-" + UUID.randomUUID();
        String correlation = correlationReference != null && !correlationReference.isBlank()
                ? correlationReference.trim() : UUID.randomUUID().toString();
        SettlementTicketMlandRemedy remedy = remedyRepository.save(SettlementTicketMlandRemedy.builder()
                .remedyReference(remedyReference)
                .ticketCode(ticket)
                .groupCode(ctx.groupCode())
                .cancelledSessionReference(cancelled)
                .replacementSessionReferences("")
                .replacementOptions("")
                .replacementStartAt(now)
                .answerDeadlineAt(now)
                .outcome("PENDING_ANSWER")
                .createdAt(now)
                .updatedAt(now)
                .build());

        notifier.notifyMlandDirectRefund(ticket, cancelledSessionReference, correlation);
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                .aggregateType("SETTLEMENT_TICKET_MLAND_REMEDY")
                .aggregateReference(remedyReference)
                .actionCode("MLAND_REMEDY_DIRECT_REFUND_OPENED")
                .correlationReference(correlation)
                .createdAt(now)
                .build());
        defaultSingleRemedy(remedy, now);
        log.info("Opened direct Mland refund {} for ticket {} (no equivalent session)", remedyReference, ticket);
        return remedy;
    }

    /**
     * T112: whether a remedy can be opened for this ticket and cancelled session: it already exists (replay) or the
     * folio is still open. Lets the handoff skip a settled ticket without a failing call poisoning its transaction.
     */
    @Transactional(readOnly = true)
    public boolean canOpenRemedy(String ticketCode, String cancelledSessionReference) {
        String ticket = ticketCode.trim();
        String cancelled = cancelledSessionReference != null ? cancelledSessionReference.trim() : "";
        if (remedyRepository.findFirstByTicketCodeAndCancelledSessionReferenceOrderByCreatedAtDesc(ticket, cancelled)
                .isPresent()) {
            return true;
        }
        return bills.findByBookingReference(ticket)
                .map(TicketMlandRemedyService::isRemediable)
                .orElse(false);
    }

    /** FR-043: an OPEN or FINALIZED folio gets the Mland remedy; a CLOSED one has nothing left to remedy. */
    private static boolean isRemediable(SettlementFinalBill bill) {
        return SettlementFinalBillLifecycleStatus.OPEN.name().equals(bill.getLifecycleStatus())
                || SettlementFinalBillLifecycleStatus.FINALIZED.name().equals(bill.getLifecycleStatus());
    }

    /** The current Mland offer of a ticket for the buyer, Staff or Manager (T112): every session and its state. */
    @Transactional(readOnly = true)
    public TicketRemedyOfferDto getOffer(String ticketCode, CurrentAccountActor actor) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND");
        }
        String ticket = ticketCode.trim();
        TicketFolioContext ctx = booking.getTicketFolioContext(ticket)
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("TICKET_NOT_FOUND"));
        // Only an anonymous caller is a Guest: Admin and Carrier have no authority over buyer operations (003.1).
        if (actor != null && actor.role() != ActorRole.MEMBER && actor.role() != ActorRole.STAFF
                && actor.role() != ActorRole.MANAGER) {
            throw new WorkshopSettlementAuthorizationException("STAFF_OR_MANAGER_REQUIRED");
        }
        if (actor != null && actor.role() == ActorRole.MEMBER) {
            if (ctx.buyerMemberAccountId() == null || !ctx.buyerMemberAccountId().equals(actor.accountId())) {
                throw new WorkshopSettlementAuthorizationException("NOT_GROUP_OWNER");
            }
        } else if (actor != null && (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER)) {
            authorization.requireManagerOrStaff(actor, ctx.locationReference());
        }
        SettlementTicketMlandRemedy remedy = remedyRepository.findByTicketCodeAndOutcome(ticket, "PENDING_ANSWER")
                .or(() -> remedyRepository.findByTicketCodeOrderByCreatedAtDesc(ticket).stream().findFirst())
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("REMEDY_OFFER_NOT_FOUND"));

        boolean pending = "PENDING_ANSWER".equals(remedy.getOutcome());
        Instant now = Instant.now();
        Set<String> stillAvailable = pending ? currentlyAvailable(ctx, remedy) : null;
        List<OfferedSessionDto> sessions = optionsOf(remedy).stream()
                .map(option -> new OfferedSessionDto(option.sessionReference(), option.startAt(), option.endAt(),
                        pending && startsInTime(option, now)
                                && (stillAvailable == null || stillAvailable.contains(option.sessionReference())),
                        option.locationReference(), option.locationName()))
                .toList();
        return new TicketRemedyOfferDto(remedy.getRemedyReference(), remedy.getTicketCode(), remedy.getGroupCode(),
                remedy.getCancelledSessionReference(), remedy.getOutcome(), remedy.getAnswerDeadlineAt(), sessions,
                remedy.getSelectedReplacementSessionReference(),
                VoucherRestorationDto.of(remedy.getVoucherRestorationOutcome()));
    }

    /** Sessions 003.2 still has a seat for now; null when it cannot be asked (then only the 24 h rule applies). */
    private Set<String> currentlyAvailable(TicketFolioContext ctx, SettlementTicketMlandRemedy remedy) {
        if (ctx.operationsTicketReference() == null || ctx.operationsTicketReference().isBlank()
                || remedy.getCancelledSessionReference() == null || remedy.getCancelledSessionReference().isBlank()) {
            return null;
        }
        try {
            EquivalentReplacementSessions current = operations.equivalentReplacementSessions(
                    UUID.fromString(ctx.operationsTicketReference()),
                    UUID.fromString(remedy.getCancelledSessionReference()));
            Set<String> references = new HashSet<>();
            current.candidates().forEach(candidate -> references.add(candidate.sessionReference().toString()));
            return references;
        } catch (RuntimeException failure) {
            log.warn("Could not refresh the Mland offer sessions: ticketCode={} ({})", remedy.getTicketCode(),
                    failure.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean startsInTime(ReplacementOption option, Instant answerAt) {
        return option.startAt() != null
                && !option.startAt().isBefore(answerAt.plus(ANSWER_LEAD_HOURS, ChronoUnit.HOURS));
    }

    static String encodeOptions(List<ReplacementOption> options) {
        return options.stream()
                .map(option -> option.sessionReference() + "|" + option.startAt()
                        + "|" + (option.endAt() == null ? "" : option.endAt())
                        + "|" + (option.locationReference() == null ? "" : option.locationReference())
                        + "|" + safeName(option.locationName()))
                .collect(java.util.stream.Collectors.joining(";"));
    }

    /** The offered sessions of a remedy; an offer made before V152 is read from its reference list and one start. */
    static List<ReplacementOption> optionsOf(SettlementTicketMlandRemedy remedy) {
        String encoded = remedy.getReplacementOptions();
        if (encoded != null && !encoded.isBlank()) {
            List<ReplacementOption> parsed = new ArrayList<>();
            for (String part : encoded.split(";")) {
                if (part.isBlank()) continue;
                String[] fields = part.split("\\|", -1);
                parsed.add(new ReplacementOption(fields[0],
                        fields.length > 1 && !fields[1].isBlank() ? Instant.parse(fields[1]) : null,
                        fields.length > 2 && !fields[2].isBlank() ? Instant.parse(fields[2]) : null,
                        fields.length > 3 && !fields[3].isBlank() ? Long.valueOf(fields[3]) : null,
                        fields.length > 4 && !fields[4].isBlank() ? fields[4] : null));
            }
            return parsed;
        }
        String references = remedy.getReplacementSessionReferences();
        if (references == null || references.isBlank()) {
            return List.of();
        }
        return Arrays.stream(references.split(","))
                .map(String::trim).filter(reference -> !reference.isEmpty())
                .map(reference -> new ReplacementOption(reference, remedy.getReplacementStartAt(), null)).toList();
    }

    /** The separators of the stored list never appear inside a name (FR-061: the name is stored with the offer). */
    private static String safeName(String name) {
        return name == null ? "" : name.replace(';', ',').replace('|', '/').trim();
    }

    private static String describeOptions(List<ReplacementOption> options) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            ReplacementOption option = options.get(i);
            text.append(i == 0 ? "" : "\n").append("- ").append(option.sessionReference()).append(": ")
                    .append(DISPLAY_FORMAT.format(option.startAt().atZone(DISPLAY_ZONE)));
            if (option.locationName() != null && !option.locationName().isBlank()) {
                text.append(" (").append(option.locationName()).append(")");
            }
            if (option.endAt() != null) {
                text.append(" - ").append(DISPLAY_FORMAT.format(option.endAt().atZone(DISPLAY_ZONE)));
            }
        }
        return text.toString();
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

        String opRef = OperationReferences.of("MREMEDY-ANSWER-", idempotencyKey.trim());
        String chosenReference = request.replacementSessionReference() == null ? ""
                : request.replacementSessionReference().trim().toLowerCase(Locale.ROOT);
        String fingerprint = digest(ticket + "|" + request.answer().name() + "|"
                + (request.buyerVerificationReference() == null ? "" : request.buyerVerificationReference().trim())
                + "|" + chosenReference);

        // Idempotency replay check
        Optional<String> priorRef = idempotency.findOutcomeReference(opRef, fingerprint);
        if (priorRef.isPresent()) {
            Optional<SettlementTicketMlandRemedy> storedOpt = remedyRepository.findByRemedyReference(priorRef.get());
            if (storedOpt.isPresent()) {
                return toResponseDto(storedOpt.get());
            }
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String correlation = correlationId != null && !correlationId.isBlank() && correlationId.trim().length() <= 64
                ? correlationId.trim() : UUID.randomUUID().toString();

        SettlementTicketMlandRemedy offer = remedyRepository.findByTicketCodeAndOutcome(ticket, "PENDING_ANSWER")
                .orElse(null);

        if (offer == null) {
            List<SettlementTicketMlandRemedy> history = remedyRepository.findByTicketCodeOrderByCreatedAtDesc(ticket);
            if (!history.isEmpty()) {
                throw new WorkshopSettlementConflictException("REMEDY_ALREADY_ANSWERED");
            }
            throw new WorkshopSettlementNotFoundException("REMEDY_OFFER_NOT_FOUND");
        }

        // T112 (FR-043): the buyer chooses ONE offered session, and it must still start 24 h after this answer.
        // Checked before the buyer proof is consumed, so a wrong choice never spends the proof.
        ReplacementOption chosen = null;
        if (request.answer() == TicketRemedyAnswer.ACCEPT_REPLACEMENT) {
            if (chosenReference.isEmpty()) {
                throw new WorkshopSettlementValidationException("REPLACEMENT_SESSION_REQUIRED");
            }
            chosen = optionsOf(offer).stream()
                    .filter(option -> option.sessionReference().equalsIgnoreCase(chosenReference))
                    .findFirst()
                    .orElseThrow(() -> new WorkshopSettlementConflictException("REPLACEMENT_SESSION_NOT_OFFERED"));
            if (!startsInTime(chosen, now)) {
                throw new WorkshopSettlementConflictException("ANSWER_DEADLINE_EXPIRED");
            }
        }

        // Authorize buyer proof
        Long buyerMemberAccountId = ctx.buyerMemberAccountId();
        Long requesterAccountId = null;
        String requesterRole;
        String channel;
        String buyerProof;

        // Only an anonymous caller is a Guest: Admin and Carrier have no authority over buyer operations (003.1).
        if (actor != null && actor.role() != ActorRole.MEMBER && actor.role() != ActorRole.STAFF
                && actor.role() != ActorRole.MANAGER) {
            throw new WorkshopSettlementAuthorizationException("STAFF_OR_MANAGER_REQUIRED");
        }
        if (actor != null && actor.role() == ActorRole.MEMBER) {
            if (buyerMemberAccountId == null || !buyerMemberAccountId.equals(actor.accountId())) {
                throw new WorkshopSettlementAuthorizationException("NOT_GROUP_OWNER");
            }
            requesterAccountId = actor.accountId();
            requesterRole = "MEMBER";
            channel = "ONLINE";
            buyerProof = "MEMBER_OWNER";
        } else if (actor != null && (actor.role() == ActorRole.STAFF || actor.role() == ActorRole.MANAGER)) {
            // T116, FR-002/FR-051: active account and, for Staff, the ticket's location in scope.
            authorization.requireManagerOrStaff(actor, ctx.locationReference());
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
            // Replace sessions in 003.2 Operations: only the cancelled session of the ticket moves to the chosen one
            // (the ticket's other session keeps its place); a Mland replacement is not the customer's change.
            if (ctx.operationsTicketReference() != null && !ctx.operationsTicketReference().isBlank()) {
                List<UUID> targetSessions = targetSessionsAfterMove(ctx, offer.getCancelledSessionReference(),
                        chosen.sessionReference());
                ReplaceTicketSessionsCommand cmd = new ReplaceTicketSessionsCommand(
                        List.of(new ReplaceTicketSessionsCommand.Line(
                                UUID.fromString(ctx.operationsTicketReference()),
                                targetSessions
                        )),
                        TicketReplacementType.MLAND,
                        OperationReferences.of("MLAND-REPLACE-" + ticket + "-", idempotencyKey.trim()),
                        correlation
                );
                TicketSessionReplacementOutcome outcome = operations.replaceTicketSessions(cmd);
                if (outcome.status() != OutcomeStatus.ACCEPTED) {
                    if (outcome.reason() == RejectionReason.INSUFFICIENT_CAPACITY
                            || outcome.reason() == RejectionReason.INVALID_REPLACEMENT
                            || outcome.reason() == RejectionReason.PACKAGE_NOT_AVAILABLE_AT_LOCATION) {
                        // No seat is held while the buyer decides; the buyer may choose another session or a refund.
                        throw new WorkshopSettlementConflictException("REPLACEMENT_SESSION_UNAVAILABLE");
                    }
                    throw new WorkshopSettlementConflictException("TICKET_REPLACEMENT_REJECTED: " + outcome.reason());
                }
            }

            offer.setSelectedReplacementSessionReference(chosen.sessionReference());
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

        // T167: this ticket is resolved (replacement accepted, or refund request opened / no-money answer recorded).
        handoffCompletion.completeIfResolved(offer.getCancelledSessionReference(), correlation);

        // Complete idempotency claim
        idempotency.execute(opRef, fingerprint, offer::getRemedyReference);

        return responseDto;
    }

    /** The ticket's complete session set once the cancelled session is replaced by the chosen one. */
    private static List<UUID> targetSessionsAfterMove(TicketFolioContext ctx, String cancelledReference,
                                                      String chosenReference) {
        List<String> current = ctx.sessionReferences() == null ? List.of() : ctx.sessionReferences();
        List<UUID> target = new ArrayList<>();
        boolean replaced = false;
        for (String reference : current) {
            if (!replaced && reference != null && reference.trim().equalsIgnoreCase(cancelledReference)) {
                target.add(UUID.fromString(chosenReference));
                replaced = true;
            } else {
                target.add(UUID.fromString(reference.trim()));
            }
        }
        if (!replaced) {
            if (current.size() > 1) {
                throw new WorkshopSettlementConflictException("CANCELLED_SESSION_NOT_ON_TICKET");
            }
            return List.of(UUID.fromString(chosenReference));
        }
        return target;
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

        // 1-2. Open the bank refund for what is still owed back for this ticket (FR-043, T170): all collected money
        // (excess kept on the folio included) minus the ticket's other refund requests, under the folio lock.
        String refundRequestRef = null;
        BankRefundService.OpenedRefund opened = bankRefunds.openRemainder(
                ticketCode,
                ctx.groupCode(),
                RefundReason.MLAND_CANCELLATION,
                offer.getRemedyReference(),
                correlation
        );
        if (opened != null) {
            refundRequestRef = opened.request().getRequestReference();
        }

        // 3. Cancel ticket entitlements in 003.2 Operations
        if (ctx.operationsTicketReference() != null && !ctx.operationsTicketReference().isBlank()) {
            String opsOpRef = OperationReferences.of("MLAND-CANCEL-" + ticketCode + "-", idempotencyKey != null ? idempotencyKey.trim() : UUID.randomUUID().toString());
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

        // 4. Points and the group voucher through the shared voucher and points facade (T155, FR-042/FR-043). 003.3
        // only triggers it and stores the answer: the voucher comes back once per group and only while it is still
        // valid, a repeat answers ALREADY_RESTORED, and the bank refund above never depends on the outcome.
        long pointsReturned = 0L;
        boolean voucherRestoredOnTicket = false;
        String voucherOutcome = null;
        var loyaltySource = booking.groupLoyaltyOutcomeSource(ctx.groupCode());
        boolean hasLoyalty = loyaltySource != null && loyaltySource.memberOrigin() && loyaltySource.snapshotRef() != null;
        if (hasLoyalty) {
            VoucherRestorationOutcome restoration = loyalty.restoreVoucherForCancelledTicket(
                    new RestoreVoucherCommand(ctx.groupCode(), ticketCode, TicketCancellationKind.MLAND_CANCELLED));
            voucherOutcome = restoration == null ? null : restoration.name();
            pointsReturned = ctx.pointsUsed();
            voucherRestoredOnTicket = restoration == VoucherRestorationOutcome.RESTORED;
        }

        // 5. Close the ticket folio without debt. T113 (FR-039, US7/AC2): when money goes back, the folio closes
        // only once the bank refund reaches REFUNDED (onBankRefundConfirmed); with nothing collected it closes now.
        final String effectiveActorRef = requesterAccountId != null ? "account:" + requesterAccountId
                : (requesterRole != null ? requesterRole : "SYSTEM");
        if (refundRequestRef == null) {
            folioCloser.closeIfSettled(ticketCode, true, effectiveActorRef, correlation, now);
        }

        // 6. Update remedy entity
        offer.setBuyerAnswer(requesterRole != null ? TicketRemedyAnswer.REFUND.name() : null);
        offer.setOutcome("REFUND");
        offer.setAnsweredAt(now);
        offer.setRefundRequestReference(refundRequestRef);
        offer.setPointsReturned(pointsReturned);
        offer.setVoucherRestored(voucherRestoredOnTicket);
        offer.setVoucherRestorationOutcome(voucherOutcome);
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
     * T113, FR-039: a Mland-remedy refund's folio closes only when the Manager matched the bank refund against the
     * statement (REFUNDED). Runs in the confirming transaction; a replay, or a refund of another source, changes
     * nothing.
     */
    @org.springframework.context.event.EventListener
    @Transactional
    public void onBankRefundConfirmed(edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBankRefundConfirmedEvent event) {
        if (event == null || event.sourceReference() == null || event.requestReference() == null) {
            return;
        }
        remedyRepository.findByRemedyReference(event.sourceReference())
                .filter(remedy -> "REFUND".equals(remedy.getOutcome()))
                .filter(remedy -> event.requestReference().equals(remedy.getRefundRequestReference()))
                .ifPresent(remedy -> folioCloser.closeIfSettled(remedy.getTicketCode(), true,
                        "SYSTEM:bank-refund-confirmed", "REFUND-" + event.requestReference(),
                        event.confirmedAt() != null ? event.confirmedAt() : Instant.now()));
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
                handoffCompletion.completeIfResolved(remedy.getCancelledSessionReference(), null);
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
                entity.isVoucherRestored(),
                entity.getSelectedReplacementSessionReference(),
                VoucherRestorationDto.of(entity.getVoucherRestorationOutcome())
        );
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

