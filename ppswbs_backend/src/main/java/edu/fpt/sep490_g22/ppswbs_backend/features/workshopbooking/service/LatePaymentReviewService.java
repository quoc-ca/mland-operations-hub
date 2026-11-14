package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.LatePaymentReviewResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort.LatePaymentRefundOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort.OpenLatePaymentRefundCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentReviewQueued;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.GroupCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveGroupCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 003.1 FR-040/FR-040a / T094 (decision 2026-11-13). A verified group payment that lands after the hold gets one
 * review per payment (the attempt's merchant reference, shared by IPN and QueryDR). When all five conditions hold
 * the group is re-confirmed as a new acceptance, all or none; otherwise the review waits for a Manager, the payer is
 * told by email, and a reference-only {@link LatePaymentReviewQueued} is published. A Manager may CONFIRM only
 * when the arrival window and/or a duplicate booking alone failed and conditions 1–3 still hold, or REFUND through
 * {@link LatePaymentRefundPort} in the same transaction. Money is never moved to another booking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LatePaymentReviewService {

    public static final String CAPACITY = "CAPACITY";
    public static final String START_TIME = "START_TIME";
    public static final String AMOUNT = "AMOUNT";
    public static final String ARRIVAL_WINDOW = "ARRIVAL_WINDOW";
    public static final String DUPLICATE_BOOKING = "DUPLICATE_BOOKING";
    private static final List<String> ORDER = List.of(CAPACITY, START_TIME, AMOUNT, ARRIVAL_WINDOW, DUPLICATE_BOOKING);
    private static final Set<String> MANAGER_CONFIRMABLE = Set.of(ARRIVAL_WINDOW, DUPLICATE_BOOKING);

    static final Duration MIN_LEAD = Duration.ofHours(2);
    static final Duration ARRIVAL_LIMIT = Duration.ofMinutes(60);
    static final Duration RESULT_LIMIT = Duration.ofHours(24);

    private final BookingGroupRepository groups;
    private final BookingGroupLineRepository lines;
    private final TicketBookingRepository tickets;
    private final TicketPriceSnapshotRepository snapshots;
    private final LatePaymentReviewRepository reviews;
    private final BookingAuditEvidenceRepository auditEvidence;
    private final GroupConfirmationService confirmation;
    private final WorkshopOperationsAdapter operations;
    private final GroupNotificationService notifications;
    private final LatePaymentRefundPort refunds;
    private final WorkshopBookingAuthorizationService authorization;
    private final BookingIdempotencyService idempotency;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final ManagerAlertRecipientSource managerRecipients;
    private final WorkshopCartQuoteRepository quotes;
    private final WorkshopCartBenefitService benefits;

    private static final Map<String, String> CONDITION_LABELS = Map.of(
            CAPACITY, "Hết chỗ",
            START_TIME, "Còn dưới 2 giờ trước buổi đầu",
            AMOUNT, "Số tiền không khớp giá đã ghi nhận",
            ARRIVAL_WINDOW, "Tiền về sau hơn 60 phút",
            DUPLICATE_BOOKING, "Khách đã có đặt chỗ khác trùng buổi");
    private static final java.time.format.DateTimeFormatter DISPLAY_TIME = java.time.format.DateTimeFormatter
            .ofPattern("HH:mm dd/MM/yyyy").withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));

    /**
     * FR-040a: alerts every active Manager at once by internal email (template 13, decision 2026-11-13). The email
     * carries no buyer contact or payment data; the Manager works from the queue.
     */
    private void alertManagers(BookingGroup group, List<BookingGroupLine> groupLines, LatePaymentReview review,
                               String correlationReference) {
        List<String> recipients = managerRecipients.activeManagerEmails().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .map(EmailMasking::normalize).distinct().sorted().toList();
        if (recipients.isEmpty()) {
            log.warn("Late-payment review {} has no Manager alert recipient; it waits in the queue",
                    review.getReviewReference());
            return;
        }
        Set<String> failed = parse(review.getFailedConditions());
        String due = review.getResultDueAt() == null ? "" : DISPLAY_TIME.format(review.getResultDueAt());
        String subject = "MLAND – [Cần xử lý trước " + due + "] Tiền về sau hạn, đơn " + group.getGroupCode();
        String reasons = String.join("; ", failed.stream().map(c -> CONDITION_LABELS.getOrDefault(c, c)).toList());
        String earliest = review.getEarliestSessionAt() == null ? "chưa xác định"
                : DISPLAY_TIME.format(review.getEarliestSessionAt());
        String action = MANAGER_CONFIRMABLE.containsAll(failed)
                ? "Có thể xác nhận chỗ nếu khách vẫn muốn tham gia và còn chỗ tại thời điểm duyệt."
                : "Chỉ có thể hoàn tiền 100% qua chuyển khoản ngân hàng.";
        String body = "Đơn " + group.getGroupCode() + " vừa nhận " + vnd(review.getAmountVnd())
                + " sau khi thời gian giữ chỗ kết thúc và hệ thống không tự xác nhận lại được.\n\n"
                + "Lý do: " + reasons + "\n"
                + "Buổi sớm nhất: " + earliest + "\n"
                + "Hạn gửi kết quả cho khách: " + due + " (24 giờ kể từ khi tiền về, hoặc trước giờ bắt đầu buổi "
                + "sớm nhất nếu sớm hơn)\n\n"
                + action + "\n\n"
                + "Mở hàng chờ \"Tiền về sau hạn\" trong màn hình quản lý để xử lý.";
        for (String recipient : recipients) {
            notifications.queue(group, null, GroupNotificationService.KIND_LATE_PAYMENT_MANAGER_ALERT, recipient,
                    subject, body, List.of(), null, correlationReference);
        }
    }

    private static String vnd(long amountVnd) {
        return String.format(Locale.ROOT, "%,d", amountVnd).replace(',', '.') + " VND";
    }

    /** Is this group past its hold, so that a verified payment for it is a late payment? Caller holds the lock. */
    public boolean isLate(BookingGroup group, Instant now) {
        return group.getGroupStatus() != BookingStatus.CONFIRMED
                && (group.getHoldExpiresAt() == null || !group.getHoldExpiresAt().isAfter(now));
    }

    /** Records (once) and evaluates a verified late payment. Idempotent per merchant reference. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LatePaymentReview receive(String groupCode, String merchantReference, long amountVnd,
                                     String correlationReference) {
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking group " + groupCode + " was not found"));
        Optional<LatePaymentReview> existing = reviews.findByVerifiedEvidenceReference(merchantReference);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (group.getGroupStatus() == BookingStatus.PENDING_PAYMENT) {
            // The sweep has not run yet: release the whole stale hold first so capacity is counted once.
            confirmation.releaseExpiredGroup(groupCode, "LATE-RELEASE-" + groupCode, correlationReference);
        }
        List<BookingGroupLine> groupLines = lines.findByGroupIdOrderByLineIndexAsc(group.getId());
        Evaluation evaluation = evaluate(group, groupLines, amountVnd, now, now, true);

        LatePaymentReview review = reviews.save(LatePaymentReview.builder()
                .reviewReference(UUID.randomUUID().toString())
                .group(group)
                .verifiedEvidenceReference(merchantReference)
                .amountVnd(amountVnd)
                .arrivedAt(now)
                .outcome(LatePaymentReview.PENDING_MANAGER)
                .earliestSessionAt(evaluation.earliestSessionAt())
                .resultDueAt(resultDue(now, evaluation.earliestSessionAt()))
                .correlationReference(correlationReference)
                .build());

        Set<String> failed = new LinkedHashSet<>(evaluation.failed());
        if (failed.isEmpty()) {
            if (reheldAndConfirmed(group, groupLines, review, "LATE_PAYMENT_RECONFIRMED", correlationReference)) {
                review.setOutcome(LatePaymentReview.AUTO_RECONFIRMED);
                review.setDecidedAt(now);
                reviews.save(review);
                audit(group, groupLines, "SYSTEM", null, "LATE_PAYMENT_RECONFIRMED", correlationReference, null);
                log.info("Late payment for group {} re-confirmed automatically", groupCode);
                return review;
            }
            failed.add(CAPACITY);
        }
        review.setFailedConditions(join(failed));
        reviews.save(review);
        audit(group, groupLines, "SYSTEM", null, "LATE_PAYMENT_REVIEW_QUEUED", correlationReference, review.getFailedConditions());
        notifications.queue(group, null, GroupNotificationService.KIND_LATE_PAYMENT_PENDING, group.getContactEmail(),
                "MLAND – Thanh toán cho đơn " + group.getGroupCode() + " đến sau thời hạn",
                pendingBody(group, amountVnd), List.of(), null, correlationReference);
        alertManagers(group, groupLines, review, correlationReference);
        LatePaymentReviewQueued queued = new LatePaymentReviewQueued(review.getReviewReference(), group.getGroupCode(),
                review.getEarliestSessionAt(), review.getResultDueAt(), correlationReference);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        events.publishEvent(queued);
                    } catch (RuntimeException ex) {
                        log.warn("Late-payment queue event {} failed: {}", queued.reviewReference(), ex.getClass().getSimpleName());
                    }
                }
            });
        } else {
            try {
                events.publishEvent(queued);
            } catch (RuntimeException ex) {
                log.warn("Late-payment queue event {} failed: {}", queued.reviewReference(), ex.getClass().getSimpleName());
            }
        }
        log.info("Late payment for group {} queued for a Manager: {}", groupCode, review.getFailedConditions());
        return review;
    }

    /** FR-040a: the Manager queue, earliest session first. Manager only; no buyer contact data. */
    @Transactional(readOnly = true)
    public List<LatePaymentReviewResponse> queue(String authorizationHeader, String outcome) {
        requireManager(authorizationHeader);
        String status = outcome == null || outcome.isBlank() ? LatePaymentReview.PENDING_MANAGER : outcome.trim();
        if (!Set.of(LatePaymentReview.AUTO_RECONFIRMED, LatePaymentReview.PENDING_MANAGER,
                LatePaymentReview.MANAGER_CONFIRMED, LatePaymentReview.REFUND_REQUESTED).contains(status)) {
            throw new BookingValidationException("Unknown late-payment review status");
        }
        return reviews.findQueue(status).stream().map(LatePaymentReviewService::response).toList();
    }

    public AccountSummary requireManager(String authorizationHeader) {
        AccountSummary account = authorization.authenticate(authorizationHeader);
        if (!authorization.isManager(account)) {
            throw new WorkshopBookingForbiddenException("Late-payment reviews are handled by a Manager");
        }
        return account;
    }

    /** A Manager decision (FR-040). Any refusal rolls back every effect, including capacity and refund. */
    @Transactional
    public LatePaymentReviewResponse decide(String groupCode, String reviewReference, String decision, String reason,
                                            AccountSummary manager, String correlationReference,
                                            BookingIdempotencyClaim claim) {
        LatePaymentReview review = reviews.findByReviewReferenceForUpdate(reviewReference)
                .filter(r -> r.getGroup().getGroupCode().equals(groupCode))
                .orElseThrow(() -> new WorkshopBookingProblemException(HttpStatus.NOT_FOUND,
                        "LATE_PAYMENT_REVIEW_NOT_FOUND", "Late-payment review not found"));
        if (!LatePaymentReview.PENDING_MANAGER.equals(review.getOutcome())) {
            throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "LATE_PAYMENT_REVIEW_ALREADY_DECIDED",
                    "This late-payment review was already decided");
        }
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode).orElseThrow();
        List<BookingGroupLine> groupLines = lines.findByGroupIdOrderByLineIndexAsc(group.getId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if ("CONFIRM".equals(decision)) {
            Set<String> failedOnArrival = parse(review.getFailedConditions());
            if (!MANAGER_CONFIRMABLE.containsAll(failedOnArrival)) {
                throw notAllowed();
            }
            Evaluation now13 = evaluate(group, groupLines, review.getAmountVnd(), review.getArrivedAt(), now, false);
            if (now13.failed().contains(START_TIME) || now13.failed().contains(AMOUNT)
                    || now13.failed().contains(CAPACITY)) {
                throw notAllowed();
            }
            if (!reheldAndConfirmed(group, groupLines, review, "LATE_PAYMENT_MANAGER_CONFIRMED", correlationReference)) {
                throw notAllowed();
            }
            review.setOutcome(LatePaymentReview.MANAGER_CONFIRMED);
        } else if ("REFUND".equals(decision)) {
            LatePaymentRefundOutcome outcome = refunds.openLatePaymentRefund(new OpenLatePaymentRefundCommand(
                    group.getGroupCode(), review.getReviewReference(), review.getVerifiedEvidenceReference(),
                    review.getAmountVnd(), "LPR-REFUND-" + review.getReviewReference(), correlationReference));
            if (outcome == null || !outcome.opened() || outcome.refundRequestReference() == null) {
                throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "LATE_PAYMENT_REFUND_REJECTED",
                        "The bank refund could not be opened: "
                                + (outcome == null || outcome.reason() == null ? "UNKNOWN" : outcome.reason()));
            }
            review.setOutcome(LatePaymentReview.REFUND_REQUESTED);
            review.setRefundRequestReference(outcome.refundRequestReference());
        } else {
            throw new BookingValidationException("decision must be CONFIRM or REFUND");
        }
        review.setDecision(decision);
        review.setDecisionReason(reason.trim());
        review.setDecidedByAccountId(manager.accountId());
        review.setDecidedAt(now);
        reviews.save(review);
        audit(group, groupLines, "MANAGER", manager.accountId(), "LATE_PAYMENT_" + decision, correlationReference,
                reason.trim());
        LatePaymentReviewResponse response = response(review);
        idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), 200, json(response));
        return response;
    }

    // ---------------------------------------------------------------- conditions

    record Evaluation(Set<String> failed, Instant earliestSessionAt) { }

    /**
     * The five conditions of FR-040. Capacity is checked read-only here (remaining seats per session against the
     * whole group's demand); the binding all-or-none hold is taken only when confirming.
     */
    private Evaluation evaluate(BookingGroup group, List<BookingGroupLine> groupLines, long amountVnd,
                                Instant arrivedAt, Instant now, boolean includeArrivalAndDuplicate) {
        Set<String> failed = new LinkedHashSet<>();
        Map<String, Integer> demand = new LinkedHashMap<>();
        Instant earliest = null;
        for (BookingGroupLine line : groupLines) {
            for (BookingGroupLineSession session : line.getSessions()) {
                demand.merge(session.getSessionReference(), line.getTicketCount(), Integer::sum);
            }
        }
        if (demand.isEmpty()) {
            failed.add(CAPACITY);
        }
        for (Map.Entry<String, Integer> entry : demand.entrySet()) {
            SaleEligibilitySnapshot snapshot;
            try {
                snapshot = operations.getSaleEligibility(UUID.fromString(entry.getKey()));
            } catch (IllegalArgumentException ex) {
                snapshot = null;
            }
            if (snapshot == null) {
                failed.add(CAPACITY);
                failed.add(START_TIME);
                continue;
            }
            if (snapshot.remainingCapacity() < entry.getValue()) {
                failed.add(CAPACITY);
            }
            Instant start = snapshot.scheduledStart();
            if (start == null || start.isBefore(now.plus(MIN_LEAD))) {
                failed.add(START_TIME);
            }
            if (start != null && (earliest == null || start.isBefore(earliest))) {
                earliest = start;
            }
        }
        if (!amountMatches(group, groupLines, amountVnd)) {
            failed.add(AMOUNT);
        }
        if (includeArrivalAndDuplicate) {
            if (group.getHoldExpiresAt() == null || arrivedAt.isAfter(group.getHoldExpiresAt().plus(ARRIVAL_LIMIT))) {
                failed.add(ARRIVAL_WINDOW);
            }
            long duplicates = groups.countActiveSameBuyerOnSessions(group.getId(), demand.keySet(),
                    group.getBuyerMemberAccountId(), EmailMasking.normalize(group.getContactEmail()), now);
            if (duplicates > 0) {
                failed.add(DUPLICATE_BOOKING);
            }
        }
        Set<String> ordered = new LinkedHashSet<>();
        ORDER.stream().filter(failed::contains).forEach(ordered::add);
        return new Evaluation(ordered, earliest);
    }

    /**
     * Condition 3 (decision 2026-11-14): recomputes the automatic workshop discount. Condition 3 passes when:
     * (1) the recalculated automatic discount equals the snapshot discount,
     * (2) every ticket/package price is unchanged,
     * (3) and the collected amount matches the expected amount.
     * Vouchers and points still fail condition 3 (decision 2026-11-13).
     */
    private boolean amountMatches(BookingGroup group, List<BookingGroupLine> groupLines, long amountVnd) {
        if (amountVnd != group.getGroupCollectionVnd()) {
            return false;
        }

        List<TicketBooking> groupTickets = tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId());
        if (groupTickets.isEmpty() || groupLines == null || groupLines.isEmpty()) {
            return false;
        }

        long collected = 0;
        long snapshotDiscountTotal = 0;
        for (TicketBooking ticket : groupTickets) {
            TicketPriceSnapshot snapshot = snapshots.findByTicketId(ticket.getId()).orElse(null);
            if (snapshot == null || snapshot.getPointsUsed() != 0 || snapshot.getPointDiscountVnd() != 0) {
                return false;
            }
            collected = Math.addExact(collected, snapshot.getCollectedAtBookingVnd());
            snapshotDiscountTotal = Math.addExact(snapshotDiscountTotal, snapshot.getVoucherDiscountVnd());
        }

        if (collected != amountVnd) {
            return false;
        }

        // Voucher check: a group that used a voucher still fails condition 3 (decision 2026-11-13)
        Optional<WorkshopCartQuote> quoteOpt = quotes.findByAcceptedGroupCode(group.getGroupCode());
        if (quoteOpt.isPresent()) {
            WorkshopCartQuote quote = quoteOpt.get();
            if (quote.getPointsUsed() != 0 || quote.getPointDiscountVnd() != 0) {
                return false;
            }
            if (quote.getBenefitSelectionJson() != null) {
                BenefitSelection selection = readSelection(quote.getBenefitSelectionJson());
                if (selection != null) {
                    if (selection.choice() == SelectionChoice.VOUCHER
                            || selection.voucherCode() != null
                            || (selection.requestedPoints() != null && selection.requestedPoints().signum() > 0)) {
                        return false;
                    }
                }
            }
            if (quote.getVoucherDiscountVnd() != snapshotDiscountTotal) {
                return false;
            }
        } else if (group.getVoucherUseReference() != null) {
            // Cannot verify whether voucherUseReference was a voucher or auto-discount without the quote
            return false;
        }

        // Check package / ticket prices unchanged
        List<WorkshopCartPricingService.PricedLine> pricedLines = new ArrayList<>();
        for (BookingGroupLine line : groupLines) {
            PublishedPackageSelectionSnapshot pkg;
            try {
                pkg = operations.getPublishedPackageSelection(Long.toString(line.getPackageId()));
            } catch (RuntimeException ex) {
                log.warn("Could not read published package {} for group {}: {}", line.getPackageId(), group.getGroupCode(), ex.getMessage());
                return false;
            }
            if (pkg == null || pkg.publicPerParticipantPrice() != line.getPricePerParticipantVnd()) {
                return false;
            }

            List<WorkshopCartPricingService.PricedSession> pricedSessions = new ArrayList<>();
            List<BookingGroupLineSession> orderedSessions = line.getSessions().stream()
                    .sorted(Comparator.comparingInt(BookingGroupLineSession::getOrdinal)).toList();
            for (BookingGroupLineSession session : orderedSessions) {
                UUID sessionRef;
                try {
                    sessionRef = UUID.fromString(session.getSessionReference());
                } catch (IllegalArgumentException ex) {
                    return false;
                }
                SaleEligibilitySnapshot sale;
                try {
                    sale = operations.getSaleEligibility(sessionRef);
                } catch (RuntimeException ex) {
                    sale = null;
                }
                LocalDate workshopDate = sale != null && sale.workshopDate() != null
                        ? sale.workshopDate()
                        : (session.getScheduledStart() != null
                                ? session.getScheduledStart().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()
                                : null);
                Long locationRef = session.getLocationReference() != null
                        ? session.getLocationReference()
                        : (sale != null ? sale.locationReference() : null);
                if (locationRef == null || locationRef <= 0 || workshopDate == null) {
                    return false;
                }
                pricedSessions.add(new WorkshopCartPricingService.PricedSession(
                        sessionRef, locationRef, session.getOrdinal(), session.getScheduledStart(),
                        sale != null ? sale.scheduledEnd() : null, workshopDate));
            }
            pricedLines.add(new WorkshopCartPricingService.PricedLine(
                    line.getLineIndex(), line.getPackageId(), line.getPackageNameSnapshot(),
                    line.getPricePerParticipantVnd(), line.getTicketCount(), pricedSessions));
        }

        // Check each ticket's packagePriceVnd matches the line price
        for (TicketBooking ticket : groupTickets) {
            TicketPriceSnapshot snapshot = snapshots.findByTicketId(ticket.getId()).orElse(null);
            BookingGroupLine line = groupLines.stream().filter(l -> l.getLineIndex() == ticket.getLineIndex()).findFirst().orElse(null);
            if (snapshot == null || line == null || snapshot.getPackagePriceVnd() != line.getPricePerParticipantVnd()) {
                return false;
            }
        }

        // Recalculate automatic discount as of review time
        WorkshopCartPricingService.PricedCart cart = new WorkshopCartPricingService.PricedCart(pricedLines, null, 0L);
        long recalculatedDiscount;
        try {
            WorkshopCartBenefitService.CartBenefits recalculated = benefits.preview(
                    group.getBuyerMemberAccountId(), cart,
                    new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO));
            recalculatedDiscount = recalculated.voucherDiscountVnd();
        } catch (RuntimeException ex) {
            log.warn("Could not recalculate automatic discount for group {}: {}", group.getGroupCode(), ex.getMessage());
            return false;
        }

        return recalculatedDiscount == snapshotDiscountTotal;
    }

    private BenefitSelection readSelection(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, BenefitSelection.class);
        } catch (JsonProcessingException ex) {
            log.warn("Could not read benefit selection json: {}", ex.getMessage());
            return null;
        }
    }

    /** Holds fresh capacity for every line (all or none), then confirms the group as a new acceptance. */
    private boolean reheldAndConfirmed(BookingGroup group, List<BookingGroupLine> groupLines, LatePaymentReview review,
                                       String auditAction, String correlationReference) {
        List<ReserveGroupCapacityCommand.Line> commandLines = new ArrayList<>();
        for (BookingGroupLine line : groupLines) {
            commandLines.add(new ReserveGroupCapacityCommand.Line(line.getLineReference(),
                    Long.toString(line.getPackageId()),
                    line.getSessions().stream().sorted(Comparator.comparingInt(BookingGroupLineSession::getOrdinal))
                            .map(s -> UUID.fromString(s.getSessionReference())).toList(),
                    line.getTicketCount()));
        }
        // Short references: Operations keeps operation references within 64 characters, line suffix included.
        String operation = "LPR-" + review.getId() + (auditAction.contains("MANAGER") ? "-M" : "-A");
        GroupCapacityOutcome outcome = operations.reserveGroupCapacity(group.getGroupCode(), commandLines,
                operation, correlationReference);
        if (outcome == null || outcome.status() != OutcomeStatus.ACCEPTED
                || outcome.lineHoldReferences() == null || outcome.lineHoldReferences().size() != groupLines.size()) {
            return false;
        }
        for (int i = 0; i < groupLines.size(); i++) {
            BookingGroupLine line = groupLines.get(i);
            List<UUID> holds = outcome.lineHoldReferences().get(i);
            List<BookingGroupLineSession> ordered = line.getSessions().stream()
                    .sorted(Comparator.comparingInt(BookingGroupLineSession::getOrdinal)).toList();
            if (holds.size() != ordered.size()) {
                throw new BookingConflictException("Workshop Operations returned an incomplete group hold outcome");
            }
            for (int j = 0; j < ordered.size(); j++) {
                ordered.get(j).setHoldReference(holds.get(j).toString());
                ordered.get(j).setHoldStatus(LineHoldStatus.RESERVED);
            }
            lines.save(line);
        }
        group.setGroupStatus(BookingStatus.PENDING_PAYMENT);
        group.setHoldExpiresAt(outcome.expiresAt());
        groups.save(group);
        for (TicketBooking ticket : tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId())) {
            ticket.setTicketStatus(BookingStatus.PENDING_PAYMENT);
            tickets.save(ticket);
        }
        confirmation.confirmReheldLateGroup(group.getGroupCode(), review.getAmountVnd(),
                review.getVerifiedEvidenceReference(), auditAction, operation + "C", correlationReference);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static WorkshopBookingProblemException notAllowed() {
        return new WorkshopBookingProblemException(HttpStatus.CONFLICT, "LATE_PAYMENT_CONFIRM_NOT_ALLOWED",
                "Confirming needs conditions 1-3 to hold and only the arrival window or a duplicate booking to fail");
    }

    static Instant resultDue(Instant arrivedAt, Instant earliestSessionAt) {
        Instant byDay = arrivedAt.plus(RESULT_LIMIT);
        return earliestSessionAt != null && earliestSessionAt.isBefore(byDay) ? earliestSessionAt : byDay;
    }

    private static String join(Set<String> failed) {
        return failed.isEmpty() ? null : String.join(",", failed);
    }

    private static Set<String> parse(String failed) {
        if (failed == null || failed.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(failed.split(",")));
    }

    static LatePaymentReviewResponse response(LatePaymentReview review) {
        Set<String> failed = parse(review.getFailedConditions());
        return new LatePaymentReviewResponse(review.getReviewReference(), review.getGroup().getGroupCode(),
                review.getOutcome(), Long.toString(review.getAmountVnd()), review.getArrivedAt(), List.copyOf(failed),
                LatePaymentReview.PENDING_MANAGER.equals(review.getOutcome()) && MANAGER_CONFIRMABLE.containsAll(failed),
                review.getEarliestSessionAt(), review.getResultDueAt(), review.getDecidedAt(),
                review.getRefundRequestReference());
    }

    private void audit(BookingGroup group, List<BookingGroupLine> groupLines, String actorType, Long actorId,
                       String action, String correlation, String reason) {
        Long locationId = groupLines.stream().flatMap(l -> l.getSessions().stream())
                .map(BookingGroupLineSession::getLocationReference).filter(Objects::nonNull).findFirst().orElse(0L);
        auditEvidence.save(BookingAuditEvidence.builder()
                .bookingCode(group.getGroupCode())
                .locationId(locationId)
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .outcome("ACCEPTED")
                .correlationReference(correlation)
                .actionReason(reason)
                .occurredAt(Instant.now())
                .build());
    }

    private String json(LatePaymentReviewResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Late-payment decision response could not be stored", e);
        }
    }

    private static String pendingBody(BookingGroup group, long amountVnd) {
        return "Chúng tôi đã nhận " + String.format(Locale.ROOT, "%,d", amountVnd).replace(',', '.') + " VND cho đơn "
                + group.getGroupCode() + " sau khi thời gian giữ chỗ kết thúc. Nhân viên MLAND sẽ liên hệ với bạn trong "
                + "vòng 24 giờ (hoặc trước giờ bắt đầu buổi workshop nếu buổi đó diễn ra sớm hơn) để xác nhận chỗ (nếu "
                + "còn) hoặc hoàn lại toàn bộ số tiền qua chuyển khoản ngân hàng. Bạn không cần thanh toán lại.";
    }
}
