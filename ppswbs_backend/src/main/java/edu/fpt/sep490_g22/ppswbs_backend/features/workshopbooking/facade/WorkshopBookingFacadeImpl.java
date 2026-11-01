package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingNotificationIntentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MagicLinkAccessRepository;
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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingLoyaltyOutcomeService;
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
    private final BookingNotificationIntentRepository notificationIntentRepository;
    private final MagicLinkAccessRepository magicLinkRepository;
    private final BookingPaymentAttemptRepository paymentAttemptRepository;
    private final BookingLatePaymentEvidenceRepository latePaymentEvidenceRepository;
    private final BookingPaymentReceiptRepository paymentReceiptRepository;
    private final BookingAuditEvidenceRepository auditEvidenceRepository;
    private final BookingLifecycleService bookingLifecycleService;
    private final WorkshopBookingLoyaltyOutcomeService loyaltyOutcomeService;
    private final ObjectMapper objectMapper;
    private final WorkshopOperationsAdapter operationsAdapter;

    @Override
    @Transactional(readOnly = true)
    public ConfirmedBookingFinancialProjection getConfirmedBookingFinancialProjection(ConfirmedBookingFinancialProjectionQuery query) {
        log.info("WorkshopBookingFacade.getConfirmedBookingFinancialProjection: bookingReference={}", query.bookingReference());
        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(query.bookingReference());
        if (optBooking.isEmpty()) {
            return null;
        }

        WorkshopBooking booking = optBooking.get();
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
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
            if (!"VNPAY".equals(receipt.getProviderName()) || receipt.getPaymentAttempt() == null
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

    private static String providerTransactionNumber(String eventReference) {
        if (eventReference == null || !eventReference.startsWith("VNPAY-")) return null;
        return eventReference.substring("VNPAY-".length());
    }

    @Override
    @Transactional
    public WorkshopBookingLoyaltyOutcomeSource getLoyaltyOutcomeSource(WorkshopBookingLoyaltyOutcomeSourceQuery query) {
        WorkshopBooking booking = bookingRepository.findLockedByBookingCode(query.bookingReference()).orElse(null);
        if (booking == null || booking.getBookingStatus() != BookingStatus.CONFIRMED
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
    public DepositPaymentEvidenceOutcome applyVerifiedDepositPaymentEvidence(ApplyVerifiedDepositPaymentEvidenceCommand command) {
        log.info("WorkshopBookingFacade.applyVerifiedDepositPaymentEvidence: bookingReference={}, evidenceRef={}, opRef={}",
                command.bookingReference(), command.verifiedEvidenceReference(), command.operationReference());

        Optional<WorkshopBooking> optBooking = bookingRepository.findLockedByBookingCode(command.bookingReference());
        if (optBooking.isEmpty()) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        }

        WorkshopBooking booking = optBooking.get();
        Optional<BookingInvoice> optInvoice = invoiceRepository.findByBookingId(booking.getId());
        if (optInvoice.isEmpty()) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        }
        BookingInvoice invoice = optInvoice.get();
        BookingPaymentAttempt attempt = paymentAttemptRepository
                .findByMerchantReference(command.merchantReference()).orElse(null);
        if (attempt == null
                || attempt.getBooking() == null
                || !booking.getId().equals(attempt.getBooking().getId())
                || attempt.getInvoice() == null
                || !invoice.getId().equals(attempt.getInvoice().getId())
                || !invoice.getDepositAmount().equals(attempt.getAttemptAmount())
                || !invoice.getDepositAmount().equals(command.amountVnd())
                || !"VND".equals(invoice.getCurrency())
                || !"VND".equals(command.currencyCode())) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.PAYMENT_MISMATCH);
        }

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            BookingPaymentReceipt receipt = paymentReceiptRepository
                    .findByProviderEventId(command.verifiedEvidenceReference()).orElse(null);
            if (receipt == null
                    || receipt.getBooking() == null
                    || !booking.getId().equals(receipt.getBooking().getId())
                    || !command.amountVnd().equals(receipt.getAmountReceived())
                    || receipt.getPaymentAttempt() == null
                    || !attempt.getId().equals(receipt.getPaymentAttempt().getId())) {
                return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.PAYMENT_MISMATCH);
            }
            markPaymentAttemptSuccessful(command.merchantReference());
            loyaltyOutcomeService.settleVerifiedDeposit(booking, command.verifiedEvidenceReference());
            return DepositPaymentEvidenceOutcome.accepted();
        }

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }

        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(Instant.now())) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.HOLD_MISSING_OR_EXPIRED);
        }
        if (attempt.getStatus() == PaymentAttemptStatus.EXPIRED
                || attempt.getStatus() == PaymentAttemptStatus.FAILED) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.PAYMENT_MISMATCH);
        }

        try {
            bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                    booking.getBookingCode(),
                    command.verifiedEvidenceReference(),
                    "VERIFIED_EVIDENCE",
                    command.amountVnd(),
                    "PAYLOAD_HASH_" + command.verifiedEvidenceReference(),
                    command.operationReference(),
                    command.correlationReference()
            );
        } catch (Exception e) {
            log.warn("Failed to apply deposit payment evidence for booking {}: {}", command.bookingReference(), e.getMessage());
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }

        BookingPaymentReceipt receipt = paymentReceiptRepository
                .findByProviderEventId(command.verifiedEvidenceReference()).orElse(null);
        if (receipt == null || !booking.getId().equals(receipt.getBooking().getId())) {
            throw new IllegalStateException("Verified payment receipt was not persisted for this booking");
        }
        receipt.setPaymentAttempt(attempt);
        paymentReceiptRepository.save(receipt);
        markPaymentAttemptSuccessful(command.merchantReference());
        loyaltyOutcomeService.settleVerifiedDeposit(booking, command.verifiedEvidenceReference());
        return DepositPaymentEvidenceOutcome.accepted();
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

    /**
     * Provider evidence is authoritative for the payment attempt identified by
     * the immutable merchant reference.  This update is deliberately limited
     * to successful evidence that was accepted (or already reflected by a
     * confirmed booking); a late success therefore remains EXPIRED and is
     * available only as reconciliation evidence.
     */
    private void markPaymentAttemptSuccessful(String merchantReference) {
        if (merchantReference == null || merchantReference.isBlank()) {
            return;
        }
        paymentAttemptRepository.findByMerchantReference(merchantReference).ifPresent(attempt -> {
            if (attempt.getStatus() != PaymentAttemptStatus.SUCCESS
                    && attempt.getStatus() != PaymentAttemptStatus.EXPIRED) {
                attempt.setStatus(PaymentAttemptStatus.SUCCESS);
                paymentAttemptRepository.save(attempt);
            }
        });
    }

    @Override
    @Transactional
    public TicketRedeliveryOutcome requestBookingTicketRedelivery(RequestBookingTicketRedeliveryCommand command) {
        log.info("WorkshopBookingFacade.requestBookingTicketRedelivery: bookingReference={}, ticketsCount={}, opRef={}",
                command.bookingReference(), command.activeUnusedTicketReferences().size(), command.operationReference());

        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(command.bookingReference());
        if (optBooking.isEmpty()) {
            return TicketRedeliveryOutcome.rejected(BookingRejectionReason.NOT_READY);
        }

        WorkshopBooking booking = optBooking.get();
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return TicketRedeliveryOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }

        try {
            bookingLifecycleService.requestTicketRedelivery(
                    booking.getBookingCode(),
                    command.activeUnusedTicketReferences(),
                    command.operationReference(),
                    command.correlationReference()
            );
            return TicketRedeliveryOutcome.accepted();
        } catch (Exception e) {
            log.warn("Failed to request ticket redelivery for booking {}: {}", command.bookingReference(), e.getMessage());
            return TicketRedeliveryOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingNotificationDeliveryPayload getBookingNotificationDeliveryPayload(BookingNotificationDeliveryPayloadQuery query) {
        log.info("WorkshopBookingFacade.getBookingNotificationDeliveryPayload: notificationReference={}", query.notificationReference());

        Optional<BookingNotificationIntent> optIntent = notificationIntentRepository.findByNotificationReference(query.notificationReference());
        if (optIntent.isEmpty()) {
            return null;
        }

        BookingNotificationIntent intent = optIntent.get();
        if (intent.getDeliverySnapshot() == null || intent.getDeliverySnapshot().isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(intent.getDeliverySnapshot());
            // Hibernate/H2 may expose a JSON-typed String column as a JSON string
            // containing the serialized object. Accept that representation while
            // continuing to read object-valued JSON columns directly.
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            String kind = node.path("notificationKind").asText(intent.getNotificationKind());
            String recipient = node.path("storedRecipient").asText("");
            String content = node.path("renderedBookingContent").asText("");
            List<String> artifacts = new ArrayList<>();
            if (node.has("renderedTicketArtifacts") && node.get("renderedTicketArtifacts").isArray()) {
                for (JsonNode item : node.get("renderedTicketArtifacts")) {
                    artifacts.add(item.asText());
                }
            }
            String accessLink = node.path("bookingAccessLink").asText("");
            if (!isActiveBookingAccessLink(intent.getBooking().getId(), accessLink)) accessLink = "";
            String correlationRef = node.path("correlationReference").asText(intent.getCorrelationReference());

            return new BookingNotificationDeliveryPayload(kind, recipient, content, artifacts, accessLink, correlationRef);
        } catch (Exception e) {
            // A malformed snapshot may contain the narrowly permitted bearer URL; never log its exception/payload.
            log.warn("Failed to read booking notification snapshot: notificationReference={}", query.notificationReference());
            return null;
        }
    }

    private boolean isActiveBookingAccessLink(Long bookingId, String accessLink) {
        if (accessLink == null || accessLink.isBlank()) return false;
        int fragmentStart = accessLink.indexOf('#');
        if (fragmentStart < 0 || fragmentStart == accessLink.length() - 1) return false;
        String token = accessLink.substring(fragmentStart + 1);
        String tokenHash = hashToken(token);
        Instant now = Instant.now();
        return magicLinkRepository.findByBookingIdAndLifecycleStatus(bookingId, MagicLinkLifecycleStatus.ACTIVE)
                .stream().anyMatch(link -> link.getExpiresAt().isAfter(now) && link.getTokenHash().equals(tokenHash));
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
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
}
