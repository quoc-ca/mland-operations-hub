package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingLatePaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * T002 & T007 & T014: Named implementation of WorkshopBookingFacade.
 * Implements the cross-feature contract boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBookingFacadeImpl implements WorkshopBookingFacade, WorkshopBookingNotificationFacade {

    private final WorkshopBookingRepository bookingRepository;
    private final BookingInvoiceRepository invoiceRepository;
    private final BookingSessionCommitmentRepository commitmentRepository;
    private final MemberWorkshopBookingQuoteRepository memberQuoteRepository;
    private final BookingPaymentAttemptRepository paymentAttemptRepository;
    private final BookingLatePaymentEvidenceRepository latePaymentEvidenceRepository;
    private final BookingPaymentReceiptRepository paymentReceiptRepository;
    private final BookingAuditEvidenceRepository auditEvidenceRepository;
    private final ObjectMapper objectMapper;
    private final WorkshopOperationsAdapter operationsAdapter;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupPaymentEvidenceService
            groupPaymentEvidence;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository
            bookingGroupRepository;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopCartQuoteRepository
            cartQuoteRepository;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository
            ticketBookingRepository;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupNotificationService
            groupNotificationService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.LatePaymentReviewRepository
            latePaymentReviewRepository;

    @Override
    @Transactional(readOnly = true)
    public ConfirmedBookingFinancialProjection getConfirmedBookingFinancialProjection(ConfirmedBookingFinancialProjectionQuery query) {
        log.info("WorkshopBookingFacade.getConfirmedBookingFinancialProjection: bookingReference={}", query.bookingReference());
        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(query.bookingReference());
        if (optBooking.isEmpty()) {
            return null;
        }

        WorkshopBooking booking = optBooking.get();
        // A booking Settlement has concluded as COMPLETED keeps its frozen invoice for the Manager folio correction
        // (003.3 T040); any other state (pending, cancelled, expired, no-show) has no folio to correct.
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED && booking.getBookingStatus() != BookingStatus.COMPLETED) {
            return null;
        }

        Optional<BookingInvoice> optInvoice = invoiceRepository.findByBookingId(booking.getId());
        if (optInvoice.isEmpty()) {
            return null;
        }
        BookingInvoice invoice = optInvoice.get();

        List<BookingSessionCommitment> commitments = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        List<String> sessionRefs = commitments.stream()
                .map(BookingSessionCommitment::getSessionReference)
                .toList();

        Long locationId = commitments.isEmpty() ? 1L : commitments.get(0).getLocationId();

        return new ConfirmedBookingFinancialProjection(
                String.valueOf(booking.getPackageId()),
                invoice.getPricePerParticipant() != null ? invoice.getPricePerParticipant() * booking.getParticipantCount() : invoice.getPackageTotal(),
                invoice.getPostPromotionAmount(),
                invoice.getDepositAmount(),
                invoice.getRemainingAmount(),
                booking.getParticipantCount(),
                sessionRefs,
                locationId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VerifiedBookingDepositPaymentsProjection getVerifiedDepositPayments(VerifiedBookingDepositPaymentsQuery query) {
        WorkshopBooking booking = bookingRepository.findByBookingCode(query.bookingReference().trim()).orElse(null);
        if (booking == null) return new VerifiedBookingDepositPaymentsProjection(List.of());
        java.util.Map<String, VerifiedBookingPayment> facts = new java.util.TreeMap<>();
        for (BookingPaymentReceipt receipt : paymentReceiptRepository.findByBookingId(booking.getId())) {
            // "VERIFIED_EVIDENCE" is how receipts written before 2026-11-05 recorded the same VNPay evidence.
            if (!VERIFIED_VNPAY_RECEIPT_PROVIDERS.contains(receipt.getProviderName()) || receipt.getPaymentAttempt() == null
                    || receipt.getAmountReceived() == null || receipt.getAmountReceived() <= 0) continue;
            BookingPaymentAttempt attempt = receipt.getPaymentAttempt();
            String txn = providerTransactionNumber(receipt.getProviderEventId());
            facts.put(attempt.getMerchantReference(), new VerifiedBookingPayment(attempt.getMerchantReference(), txn,
                    attempt.getProviderCreateDate(), receipt.getAmountReceived(), "VND", true));
        }
        for (BookingLatePaymentEvidence late : latePaymentEvidenceRepository.findByBookingCode(booking.getBookingCode())) {
            BookingPaymentAttempt attempt = paymentAttemptRepository.findByMerchantReference(late.getMerchantReference()).orElse(null);
            if (attempt == null || late.getAmountVnd() == null || late.getAmountVnd() <= 0) continue;
            facts.putIfAbsent(attempt.getMerchantReference(), new VerifiedBookingPayment(attempt.getMerchantReference(),
                    late.getProviderTransactionReference(), attempt.getProviderCreateDate(), late.getAmountVnd(),
                    late.getCurrencyCode(), true));
        }
        List<VerifiedBookingPayment> ordered = facts.values().stream()
                .sorted(java.util.Comparator.comparing(VerifiedBookingPayment::originalProviderCreateDate,
                                java.util.Comparator.nullsLast(String::compareTo))
                        .thenComparing(VerifiedBookingPayment::reference)).toList();
        return new VerifiedBookingDepositPaymentsProjection(ordered);
    }

    private static final java.util.Set<String> VERIFIED_VNPAY_RECEIPT_PROVIDERS = java.util.Set.of("VNPAY", "VERIFIED_EVIDENCE");

    /** IPN evidence is {@code VNPAY-<txn>}; QueryDR recovery evidence is {@code VNPAY-QDR-<txn>}. */
    static String providerTransactionNumber(String eventReference) {
        if (eventReference == null) return null;
        if (eventReference.startsWith("VNPAY-QDR-")) return eventReference.substring("VNPAY-QDR-".length());
        if (eventReference.startsWith("VNPAY-")) return eventReference.substring("VNPAY-".length());
        return null;
    }

    @Override
    @Transactional
    public WorkshopBookingLoyaltyOutcomeSource getLoyaltyOutcomeSource(WorkshopBookingLoyaltyOutcomeSourceQuery query) {
        WorkshopBooking booking = bookingRepository.findLockedByBookingCode(query.bookingReference()).orElse(null);
        if (booking == null || !carriesLoyaltyOutcome(booking.getBookingStatus())
                || booking.getMemberAccountId() == null) {
            return null;
        }
        var quote = memberQuoteRepository.findByAcceptedBookingCode(booking.getBookingCode()).orElse(null);
        if (quote == null || !booking.getMemberAccountId().equals(quote.getMemberAccountId())) {
            return null;
        }
        return new WorkshopBookingLoyaltyOutcomeSource(true, quote.getQuoteRef(),
                Math.addExact(booking.getVersion(), 1L), booking.getHoldExpiresAt());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Optional<TicketBookingRef> findTicketBookingByOperationsReference(String ticketReference) {
        if (ticketReference == null || ticketReference.isBlank()) return java.util.Optional.empty();
        return ticketBookingRepository.findByOperationsTicketReference(ticketReference.trim())
                .map(ticket -> new TicketBookingRef(ticket.getTicketCode(), ticket.getGroup().getGroupCode()));
    }

    @Override
    @Transactional
    public BookingGroupLoyaltyOutcomeSource getGroupLoyaltyOutcomeSource(String groupReference) {
        if (groupReference == null || groupReference.isBlank()) {
            return BookingGroupLoyaltyOutcomeSource.none();
        }
        BookingGroup group = bookingGroupRepository.findByGroupCode(groupReference).orElse(null);
        // A group that reserved nothing has no earn to decide. A Guest group with an automatic discount has a snapshot
        // without a Member (006 D-014), and its earn must still be closed — 006 closes it as a zero grant.
        if (group == null || group.getVoucherUseReference() == null) {
            return BookingGroupLoyaltyOutcomeSource.none();
        }
        var quote = cartQuoteRepository.findByAcceptedGroupCode(groupReference).orElse(null);
        if (quote == null) {
            return BookingGroupLoyaltyOutcomeSource.none();
        }
        var tickets = ticketBookingRepository.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId())
                .stream()
                .map(ticket -> new BookingGroupLoyaltyOutcomeSource.GroupTicket(ticket.getTicketCode(),
                        ticket.getOperationsTicketReference()))
                .toList();
        return new BookingGroupLoyaltyOutcomeSource(true, quote.getQuoteRef(),
                Math.addExact(group.getVersion() == null ? 0L : group.getVersion(), 1L),
                group.getHoldExpiresAt(), tickets);
    }

    /**
     * A Member booking keeps its Loyalty outcome source through every state it can reach once confirmed. The outcome is
     * decided after the booking has moved on (completed or no-show by attendance, cancelled by Settlement), so asking only
     * while it is still CONFIRMED made a completed Member booking look like a Guest one and its earn was never granted.
     */
    private static boolean carriesLoyaltyOutcome(BookingStatus status) {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.COMPLETED
                || status == BookingStatus.NO_SHOW || status == BookingStatus.CANCELLED;
    }

    @Override
    @Transactional(readOnly = true)
    public String getSettlementNotificationRecipient(SettlementNotificationRecipientQuery query) {
        if (query == null || query.bookingReference() == null) return null;
        String reference = query.bookingReference().trim();
        // A single booking, a booking group (late-payment refund) or one ticket of a group.
        String email = bookingRepository.findByBookingCode(reference).map(WorkshopBooking::getContactEmail)
                .or(() -> bookingGroupRepository.findByGroupCode(reference)
                        .map(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup::getContactEmail))
                .or(() -> ticketBookingRepository.findByTicketCode(reference)
                        .map(ticket -> ticket.getGroup().getContactEmail()))
                .orElse(null);
        return email == null || email.isBlank() ? null : email.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerBookingAccessDecision verifyCustomerBookingAccess(CustomerBookingAccessQuery query) {
        if (query == null || query.bookingReference() == null || query.bookingReference().isBlank()) {
            return new CustomerBookingAccessDecision(false);
        }
        boolean memberMode = query.memberAccountId() != null;
        boolean emailMode = query.contactEmail() != null && !query.contactEmail().isBlank();
        boolean phoneMode = query.contactPhone() != null && !query.contactPhone().isBlank();
        if ((memberMode ? 1 : 0) + (emailMode ? 1 : 0) + (phoneMode ? 1 : 0) != 1) {
            return new CustomerBookingAccessDecision(false);
        }
        WorkshopBooking booking = bookingRepository.findByBookingCode(query.bookingReference().trim()).orElse(null);
        if (booking == null) return new CustomerBookingAccessDecision(false);
        boolean authorized = memberMode
                ? query.memberAccountId().equals(booking.getMemberAccountId())
                : emailMode
                    ? query.contactEmail().trim().equalsIgnoreCase(booking.getContactEmail())
                    : query.contactPhone().trim().equals(booking.getContactPhone());
        return new CustomerBookingAccessDecision(authorized);
    }

    @Override
    @Transactional
    public FinalBalanceResolutionOutcome recordFinalBalanceResolved(RecordFinalBalanceResolvedCommand command) {
        log.info("WorkshopBookingFacade.recordFinalBalanceResolved: bookingReference={}, settlementReference={}, opRef={}",
                command.bookingReference(), command.settlementReference(), command.operationReference());

        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(command.bookingReference());
        if (optBooking.isEmpty()) {
            return FinalBalanceResolutionOutcome.rejected(BookingRejectionReason.NOT_READY);
        }

        WorkshopBooking booking = optBooking.get();
        if (booking.getPaymentStatus() == PaymentStatus.FULLY_PAID) {
            return FinalBalanceResolutionOutcome.accepted();
        }

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return FinalBalanceResolutionOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }

        booking.setPaymentStatus(PaymentStatus.FULLY_PAID);
        bookingRepository.save(booking);

        BookingAuditEvidence audit = BookingAuditEvidence.builder()
                .bookingCode(booking.getBookingCode())
                .locationId(1L)
                .actorType("SETTLEMENT")
                .action("FINAL_BALANCE_RESOLVED")
                .outcome("ACCEPTED")
                .correlationReference(command.correlationReference())
                .occurredAt(Instant.now())
                .build();
        auditEvidenceRepository.save(audit);

        return FinalBalanceResolutionOutcome.accepted();
    }

    @Override
    @Transactional
    public BookingServiceOutcomeResult recordBookingServiceOutcome(RecordBookingServiceOutcomeCommand command) {
        WorkshopBooking booking = bookingRepository.findLockedByBookingCode(command.bookingReference().trim()).orElse(null);
        if (booking == null) return BookingServiceOutcomeResult.rejected(BookingRejectionReason.NOT_READY);

        BookingStatus target = BookingStatus.valueOf(command.outcome().name());
        if (booking.getBookingStatus() == target) return BookingServiceOutcomeResult.accepted();
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return BookingServiceOutcomeResult.rejected(BookingRejectionReason.INVALID_STATE);
        }

        booking.setBookingStatus(target);
        bookingRepository.save(booking);
        recordSettlementAudit(booking, "BOOKING_SERVICE_" + target.name(), command.correlationReference());
        return BookingServiceOutcomeResult.accepted();
    }

    @Override
    @Transactional
    public BookingRefundStatusResult recordBookingRefundStatus(RecordBookingRefundStatusCommand command) {
        WorkshopBooking booking = bookingRepository.findLockedByBookingCode(command.bookingReference().trim()).orElse(null);
        if (booking == null) return BookingRefundStatusResult.rejected(BookingRejectionReason.NOT_READY);

        PaymentStatus target = PaymentStatus.valueOf(command.status().name());
        if (booking.getPaymentStatus() == target) return BookingRefundStatusResult.accepted();
        boolean legal = (target == PaymentStatus.REFUND_PENDING && booking.getPaymentStatus() == PaymentStatus.DEPOSIT_PAID)
                || (target == PaymentStatus.REFUNDED && booking.getPaymentStatus() == PaymentStatus.REFUND_PENDING);
        if (!legal) return BookingRefundStatusResult.rejected(BookingRejectionReason.INVALID_STATE);

        booking.setPaymentStatus(target);
        bookingRepository.save(booking);
        recordSettlementAudit(booking, "BOOKING_REFUND_" + target.name(), command.correlationReference());
        return BookingRefundStatusResult.accepted();
    }

    private void recordSettlementAudit(WorkshopBooking booking, String action, String correlationReference) {
        auditEvidenceRepository.save(BookingAuditEvidence.builder()
                .bookingCode(booking.getBookingCode()).locationId(1L).actorType("SETTLEMENT")
                .action(action).outcome("ACCEPTED")
                .correlationReference(correlationReference == null ? "SETTLEMENT-CONCLUSION" : correlationReference)
                .occurredAt(Instant.now()).build());
    }

    @Override
    @Transactional
    public DepositPaymentEvidenceOutcome applyVerifiedDepositPaymentEvidence(ApplyVerifiedDepositPaymentEvidenceCommand command) {
        log.info("WorkshopBookingFacade.applyVerifiedDepositPaymentEvidence: bookingReference={}, evidenceRef={}, opRef={}",
                command.bookingReference(), command.verifiedEvidenceReference(), command.operationReference());

        // A booking group pays once for the whole group, and its attempt owns the merchant reference, so group
        // evidence is routed before the single-booking path ever looks for a booking code (003.1 FR-038).
        if (groupPaymentEvidence.owns(command.merchantReference())) {
            return groupPaymentEvidence.apply(command.bookingReference(), command.merchantReference(),
                    command.amountVnd(), command.currencyCode(), command.verifiedEvidenceReference(),
                    command.operationReference(), command.correlationReference());
        }

        // Decision 2026-11-13 (003.1 FR-044): a direct booking is only a booking group, so no single booking can
        // be awaiting a deposit. Evidence for any other reference is refused and nothing changes.
        return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
    }

    @Override
    @Transactional
    public DepositPaymentEvidenceOutcome recordLateDepositEvidence(RecordLateDepositEvidenceCommand command) {
        Optional<WorkshopBooking> found = bookingRepository.findLockedByBookingCode(command.bookingReference());
        if (found.isEmpty()) return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        WorkshopBooking booking = found.get();
        BookingPaymentAttempt attempt = paymentAttemptRepository.findByMerchantReference(command.merchantReference()).orElse(null);
        if (attempt == null || attempt.getBooking() == null || !booking.getId().equals(attempt.getBooking().getId())
                || attempt.getAttemptAmount() != command.amountVnd() || !"VND".equals(command.currencyCode())) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.PAYMENT_MISMATCH);
        }
        if (paymentReceiptRepository.existsByPaymentAttempt_Id(attempt.getId())) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }
        boolean released = booking.getBookingStatus() == BookingStatus.CANCELLED
                || booking.getBookingStatus() == BookingStatus.EXPIRED
                || (booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT
                    && booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(Instant.now()));
        if (!released || booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }
        if (latePaymentEvidenceRepository.existsByMerchantReference(command.merchantReference())) {
            return DepositPaymentEvidenceOutcome.accepted();
        }
        latePaymentEvidenceRepository.saveAndFlush(BookingLatePaymentEvidence.builder()
                .booking(booking).bookingCode(booking.getBookingCode())
                .merchantReference(command.merchantReference())
                .evidenceReference(command.evidenceReference())
                .providerTransactionReference(command.providerTransactionReference())
                .amountVnd(command.amountVnd()).currencyCode(command.currencyCode()).build());
        return DepositPaymentEvidenceOutcome.accepted();
    }

    @Override
    @Transactional
    public BookingCancellationOutcome cancelConfirmedBooking(CancelConfirmedBookingCommand command) {
        if (command == null || command.bookingReference() == null || command.bookingReference().isBlank()) {
            return BookingCancellationOutcome.rejected(BookingRejectionReason.NOT_READY);
        }
        WorkshopBooking booking = bookingRepository.findLockedByBookingCode(command.bookingReference().trim()).orElse(null);
        if (booking == null) return BookingCancellationOutcome.rejected(BookingRejectionReason.NOT_READY);
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) return BookingCancellationOutcome.accepted();
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return BookingCancellationOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        bookingRepository.save(booking);
        auditEvidenceRepository.save(BookingAuditEvidence.builder()
                .bookingCode(booking.getBookingCode())
                .locationId(1L)
                .actorType("CUSTOMER")
                .action("CUSTOMER_CANCELLATION_NO_REFUND")
                .outcome("ACCEPTED")
                .correlationReference(command.correlationReference() == null ? "BOOKING-CANCEL" : command.correlationReference())
                .actionReason("DIRECT_DEPOSIT_FORFEITED")
                .occurredAt(Instant.now())
                .build());
        return BookingCancellationOutcome.accepted();
    }

    @Override
    @Transactional
    public TicketRedeliveryOutcome requestBookingTicketRedelivery(RequestBookingTicketRedeliveryCommand command) {
        log.info("WorkshopBookingFacade.requestBookingTicketRedelivery: bookingReference={}, ticketsCount={}, opRef={}",
                command.bookingReference(), command.activeUnusedTicketReferences().size(), command.operationReference());

        // Decision 2026-11-13: single direct bookings are gone and an OTA booking has no Mland contact, so there is
        // no single booking to redeliver to. A group ticket is resent with PUT /workshop-tickets/{code}/recipient
        // (same email) and the buyer confirmation with POST /workshop-booking-groups/{code}/confirmation-deliveries.
        return TicketRedeliveryOutcome.rejected(BookingRejectionReason.NOT_READY);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingNotificationDeliveryPayload getBookingNotificationDeliveryPayload(BookingNotificationDeliveryPayloadQuery query) {
        log.info("WorkshopBookingFacade.getBookingNotificationDeliveryPayload: notificationReference={}", query.notificationReference());
        // Only booking groups send emails since the 2026-11-13 decision; their snapshots live with the group.
        return groupNotificationService.readPayload(query.notificationReference());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingEligibilitySnapshot> getBookingEligibilityForDesign(String bookingCode, Long memberAccountId) {
        if (bookingCode == null || bookingCode.isBlank() || memberAccountId == null) {
            return Optional.empty();
        }
        return bookingRepository.findByBookingCode(bookingCode.trim())
                .filter(b -> memberAccountId.equals(b.getMemberAccountId()))
                .map(b -> {
                    boolean eligible = b.getBookingStatus() != BookingStatus.CANCELLED
                            && b.getBookingStatus() != BookingStatus.EXPIRED;
                    return BookingEligibilitySnapshot.builder()
                            .bookingId(b.getId())
                            .bookingCode(b.getBookingCode())
                            .memberAccountId(b.getMemberAccountId())
                            .packageId(b.getPackageId())
                            .packageName(b.getPackageNameSnapshot())
                            .bookingStatus(b.getBookingStatus().name())
                            .paymentStatus(b.getPaymentStatus().name())
                            .eligibleForDesign(eligible)
                            .build();
                });
    }

    @Override
    @Transactional
    public OtaBookingOutcome createOrReturnOtaBooking(OtaBookingCommand command) {
        final String bookingCode = deterministicOtaBookingCode(command.providerReference(), command.externalBookingReference());
        final long packageId;
        try {
            packageId = Long.parseLong(command.packageReference());
        } catch (NumberFormatException ex) {
            return OtaBookingOutcome.rejected("PACKAGE_REFERENCE_INVALID");
        }
        WorkshopBooking existing = bookingRepository.findLockedByBookingCode(bookingCode).orElse(null);
        if (existing != null && !sameOtaSelection(existing, command, packageId)) {
            return OtaBookingOutcome.rejected("IDEMPOTENCY_CONFLICT");
        }
        var eligibility = operationsAdapter.getSaleEligibility(command.sessionReference());
        if (eligibility == null || !eligibility.customerSelectable()) {
            return OtaBookingOutcome.rejected("SESSION_NOT_AVAILABLE");
        }
        var capacity = operationsAdapter.acceptOtaBooking(bookingCode, command.packageReference(),
                command.sessionReference(), command.participantCount(), command.operationReference(),
                command.correlationReference());
        if (capacity == null || capacity.status() != edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus.ACCEPTED) {
            return OtaBookingOutcome.rejected(capacity == null || capacity.reason() == null
                    ? "OPERATIONS_REJECTED" : capacity.reason().name());
        }
        if (existing == null) {
            existing = bookingRepository.save(WorkshopBooking.builder()
                    .bookingCode(bookingCode).packageId(packageId)
                    .packageNameSnapshot("OTA-" + command.packageReference())
                    .contactEmail("ota-booking@redacted.invalid").contactPhone("OTA-NO-CONTACT")
                    .participantCount(command.participantCount()).bookingStatus(BookingStatus.CONFIRMED)
                    .paymentStatus(PaymentStatus.FULLY_PAID).confirmedAt(Instant.now()).build());
            commitmentRepository.save(BookingSessionCommitment.builder().booking(existing)
                    .sessionReference(command.sessionReference().toString())
                    .locationId(eligibility.locationReference()).ordinal(1)
                    .seatCount(command.participantCount()).build());
        }
        return OtaBookingOutcome.accepted(existing.getBookingCode(), capacity.ticketReferences());
    }

    private boolean sameOtaSelection(WorkshopBooking booking, OtaBookingCommand command, long packageId) {
        if (!Long.valueOf(packageId).equals(booking.getPackageId())
                || !Integer.valueOf(command.participantCount()).equals(booking.getParticipantCount())) return false;
        return commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId()).stream()
                .anyMatch(c -> command.sessionReference().toString().equals(c.getSessionReference()));
    }

    private static String deterministicOtaBookingCode(String provider, String externalReference) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (provider.trim() + ":" + externalReference.trim()).getBytes(StandardCharsets.UTF_8));
            return "OTA-" + HexFormat.of().formatHex(digest).substring(0, 28);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LatePaymentReviewSnapshot> findLatePaymentReview(String reviewReference) {
        if (reviewReference == null || reviewReference.isBlank()) return Optional.empty();
        return latePaymentReviewRepository.findByReviewReference(reviewReference.trim())
                .map(review -> new LatePaymentReviewSnapshot(review.getReviewReference(),
                        review.getGroup().getGroupCode(), review.getVerifiedEvidenceReference(),
                        review.getAmountVnd(), review.getOutcome()));
    }
}
