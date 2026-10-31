package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingNotificationReady;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * T014: Booking Lifecycle and transaction orchestration service.
 * <p>
 * Invariants:
 * - Booking-critical commands run in a single local MySQL transaction.
 * - Workshop Operations is consumed strictly via WorkshopOperationsAdapter (public facade only).
 * - All-or-nothing capacity reservation, hold commitment, and hold release.
 * - Gateway/network I/O is strictly outside the local MySQL transaction.
 * - Ticket projection is fetched and stored ONLY after successful hold commit.
 * - Post-commit BookingNotificationReady event is dispatched only after local transaction commits.
 * - Delivery failure never reverses or rolls back the confirmed Booking outcome.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycleService {

    private final WorkshopBookingRepository bookingRepository;
    private final BookingSessionCommitmentRepository commitmentRepository;
    private final BookingCapacityHoldRepository holdRepository;
    private final BookingInvoiceRepository invoiceRepository;
    private final BookingPaymentReceiptRepository paymentReceiptRepository;
    private final BookingTicketRepository ticketRepository;
    private final BookingConfirmationService bookingConfirmationService;
    private final MagicLinkAccessRepository magicLinkRepository;
    private final BookingNotificationIntentRepository notificationIntentRepository;
    private final BookingAuditEvidenceRepository auditEvidenceRepository;
    private final WorkshopOperationsAdapter operationsAdapter;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Atomically validates sale eligibility, reserves capacity across all required sessions,
     * and persists pending booking, invoice, commitments, holds, and minimal audit in one local transaction.
     */
    @Transactional
    public WorkshopBooking createPendingBookingReservation(
            String bookingCode,
            Long packageId,
            String packageNameSnapshot,
            String contactEmail,
            String contactPhone,
            Long memberAccountId,
            int participantCount,
            List<UUID> sessionReferences,
            Long pricePerParticipant,
            Long postPromotionAmount,
            String operationReference,
            String correlationReference
    ) {
        log.info("createPendingBookingReservation: bookingCode={}, packageId={}, participantCount={}, sessionCount={}",
                bookingCode, packageId, participantCount, sessionReferences == null ? 0 : sessionReferences.size());

        if (participantCount < 1) {
            throw new BookingValidationException("participantCount must be at least 1");
        }
        if (sessionReferences == null || sessionReferences.isEmpty()
                || sessionReferences.stream().anyMatch(Objects::isNull)
                || new HashSet<>(sessionReferences).size() != sessionReferences.size()) {
            throw new BookingValidationException("sessionReferences must contain distinct session references");
        }

        // Resolve only customer-safe snapshots. Operations remains the authority for the
        // Package's required count, location and schedule constraints during atomic reserve.
        Long sessionLocationId = null;
        Map<UUID, SaleEligibilitySnapshot> eligibilityByReference = new HashMap<>();
        for (UUID sessionReference : sessionReferences) {
            SaleEligibilitySnapshot eligibility = operationsAdapter.getSaleEligibility(sessionReference);
            if (eligibility == null || (eligibility.sessionReference() != null
                    && !sessionReference.equals(eligibility.sessionReference())) || !eligibility.customerSelectable()) {
                throw new BookingConflictException("A selected session is not currently available");
            }
            if (sessionLocationId == null && eligibility.locationReference() != null) {
                sessionLocationId = eligibility.locationReference();
            }
            eligibilityByReference.put(sessionReference, eligibility);
        }
        if (sessionLocationId == null) {
            throw new BookingConflictException("A selected session is not currently available");
        }

        // 2. Reserve the whole selection against the current published Package.
        CapacityHoldOutcome holdOutcome = operationsAdapter.reserveBookingCapacity(
                bookingCode, String.valueOf(packageId), sessionReferences, participantCount,
                operationReference, correlationReference);

        if (holdOutcome == null || holdOutcome.status() != OutcomeStatus.ACCEPTED) {
            String reason = holdOutcome != null ? String.valueOf(holdOutcome.reason()) : "UNKNOWN";
            log.warn("Capacity reservation rejected for bookingCode={}: reason={}", bookingCode, reason);
            throw new BookingConflictException("Capacity reservation rejected: " + reason);
        }

        if (holdOutcome.holdReferences().size() != sessionReferences.size() || holdOutcome.expiresAt() == null) {
            throw new BookingConflictException("Capacity reservation returned an incomplete outcome");
        }

        Instant holdExpiresAt = holdOutcome.expiresAt();

        // 3. Persist WorkshopBooking
        WorkshopBooking booking = WorkshopBooking.builder()
                .bookingCode(bookingCode)
                .packageId(packageId)
                .packageNameSnapshot(packageNameSnapshot)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .memberAccountId(memberAccountId)
                .participantCount(participantCount)
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .holdExpiresAt(holdExpiresAt)
                .build();
        booking = bookingRepository.save(booking);

        // 4. Persist Commitments and Holds
        int ordinal = 1;
        List<UUID> holdReferences = holdOutcome.holdReferences() != null ? holdOutcome.holdReferences() : List.of();
        Map<UUID, UUID> holdBySession = new HashMap<>();
        for (int i = 0; i < sessionReferences.size(); i++) holdBySession.put(sessionReferences.get(i), holdReferences.get(i));
        List<UUID> chronologicalReferences = sessionReferences.stream().sorted(Comparator.comparing(reference ->
                Optional.ofNullable(eligibilityByReference.get(reference).scheduledStart()).orElse(Instant.MAX))).toList();
        for (UUID sessionReference : chronologicalReferences) {
            SaleEligibilitySnapshot eligibility = eligibilityByReference.get(sessionReference);
            BookingSessionCommitment commitment = BookingSessionCommitment.builder()
                    .booking(booking)
                    .sessionReference(sessionReference.toString())
                    .locationId(eligibility.locationReference())
                    .ordinal(ordinal++)
                    .seatCount(participantCount)
                    .build();
            commitment = commitmentRepository.save(commitment);

            String holdRef = holdBySession.get(sessionReference).toString();

            BookingCapacityHold hold = BookingCapacityHold.builder()
                    .booking(booking)
                    .sessionCommitment(commitment)
                    .holdReference(holdRef)
                    .holdStatus("RESERVED")
                    .build();
            holdRepository.save(hold);
        }

        // 5. Calculate and persist frozen Invoice
        long pkgTotal;
        try {
            pkgTotal = Math.multiplyExact((long) participantCount, pricePerParticipant);
        } catch (ArithmeticException ex) {
            throw new BookingValidationException("Package total exceeds the supported VND range");
        }
        long deposit = Math.floorDiv(postPromotionAmount, 2) + (postPromotionAmount % 2);
        long remaining = postPromotionAmount - deposit;

        BookingInvoice invoice = BookingInvoice.builder()
                .booking(booking)
                .invoiceNumber("INV-" + bookingCode)
                .pricePerParticipant(pricePerParticipant)
                .packageTotal(pkgTotal)
                .postPromotionAmount(postPromotionAmount)
                .depositAmount(deposit)
                .remainingAmount(remaining)
                .currency("VND")
                .build();
        invoiceRepository.save(invoice);

        // 6. Minimal Audit Evidence
        BookingAuditEvidence audit = BookingAuditEvidence.builder()
                .bookingCode(bookingCode)
                .locationId(sessionLocationId)
                .actorType(memberAccountId != null ? "MEMBER" : "GUEST")
                .actorId(memberAccountId)
                .action("RESERVE_CAPACITY")
                .outcome("ACCEPTED")
                .correlationReference(correlationReference)
                .occurredAt(Instant.now())
                .build();
        auditEvidenceRepository.save(audit);

        return booking;
    }

    /**
     * Idempotently releases active holds and updates booking status to CANCELLED.
     */
    @Transactional
    public void releasePendingHold(String bookingCode, String operationReference, String correlationReference) {
        log.info("releasePendingHold: bookingCode={}, opRef={}", bookingCode, operationReference);

        Optional<WorkshopBooking> opt = bookingRepository.findByBookingCode(bookingCode);
        if (opt.isEmpty()) {
            log.warn("Booking {} not found for release", bookingCode);
            return;
        }

        WorkshopBooking booking = opt.get();
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            log.info("Booking {} is already in status {}, skipping release", bookingCode, booking.getBookingStatus());
            return;
        }

        // Release holds in Operations
        List<BookingCapacityHold> holds = holdRepository.findByBookingId(booking.getId());
        List<UUID> holdUuids = new ArrayList<>();
        for (BookingCapacityHold hold : holds) {
            if ("RESERVED".equalsIgnoreCase(hold.getHoldStatus())) {
                try {
                    holdUuids.add(parseHoldReference(hold.getHoldReference()));
                } catch (IllegalArgumentException e) {
                    throw new BookingConflictException("A stored capacity hold reference is invalid");
                }
                hold.setHoldStatus("RELEASED");
                holdRepository.save(hold);
            }
        }

        if (!holdUuids.isEmpty()) {
            operationsAdapter.releaseBookingHolds(bookingCode, holdUuids, operationReference, correlationReference);
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        bookingRepository.save(booking);

        List<BookingSessionCommitment> comms = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        if (comms.isEmpty()) throw new BookingConflictException("Booking has no session commitments to release");
        Long locId = comms.get(0).getLocationId();

        BookingAuditEvidence audit = BookingAuditEvidence.builder()
                .bookingCode(bookingCode)
                .locationId(locId)
                .actorType("SYSTEM")
                .action("RELEASE_HOLD")
                .outcome("ACCEPTED")
                .correlationReference(correlationReference)
                .occurredAt(Instant.now())
                .build();
        auditEvidenceRepository.save(audit);
    }

    /**
     * Atomically confirms booking payment after gateway verification, commits holds in Operations,
     * fetches delivery-safe ticket projection, persists tickets, saves notification intent, and publishes
     * post-commit handoff event.
     */
    @Transactional
    public WorkshopBooking confirmBookingPaymentAndCommitHolds(
            String bookingCode,
            String providerEventId,
            String providerName,
            Long amountReceived,
            String safePayloadHash,
            String operationReference,
            String correlationReference
    ) {
        log.info("confirmBookingPaymentAndCommitHolds: bookingCode={}, providerEventId={}, amountReceived={}",
                bookingCode, providerEventId, amountReceived);

        WorkshopBooking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking " + bookingCode + " not found"));

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            log.info("Booking {} is already CONFIRMED, returning existing record", bookingCode);
            return booking;
        }

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BookingConflictException("Booking is in status " + booking.getBookingStatus() + ", cannot confirm");
        }

        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(Instant.now())) {
            log.warn("Booking {} hold has expired at {}", bookingCode, booking.getHoldExpiresAt());
            throw new BookingConflictException("Booking hold has expired");
        }

        BookingInvoice invoice = invoiceRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new IllegalStateException("Invoice missing for booking " + bookingCode));

        if (!invoice.getDepositAmount().equals(amountReceived)) {
            log.warn("Amount mismatch for booking {}: expected {}, received {}",
                    bookingCode, invoice.getDepositAmount(), amountReceived);
            throw new BookingValidationException("Received amount does not match deposit requirement");
        }

        // Commit holds in Workshop Operations
        List<BookingCapacityHold> holds = holdRepository.findByBookingId(booking.getId());
        List<UUID> holdUuids = new ArrayList<>();
        for (BookingCapacityHold hold : holds) {
            try {
                holdUuids.add(parseHoldReference(hold.getHoldReference()));
            } catch (IllegalArgumentException e) {
                throw new BookingConflictException("A stored capacity hold reference is invalid");
            }
        }

        CapacityCommitOutcome commitOutcome = operationsAdapter.commitBookingHolds(
                bookingCode, holdUuids, operationReference, correlationReference);

        if (commitOutcome == null || commitOutcome.status() != OutcomeStatus.ACCEPTED) {
            String reason = commitOutcome != null ? String.valueOf(commitOutcome.reason()) : "UNKNOWN";
            log.warn("Hold commit rejected by Workshop Operations for bookingCode={}: reason={}", bookingCode, reason);
            throw new BookingConflictException("Hold commit rejected by Workshop Operations: " + reason);
        }

        for (BookingCapacityHold hold : holds) {
            hold.setHoldStatus("COMMITTED");
            holdRepository.save(hold);
        }

        // Update booking state
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
        booking.setConfirmedAt(Instant.now());
        booking = bookingRepository.save(booking);

        // Record payment receipt
        BookingPaymentReceipt receipt = BookingPaymentReceipt.builder()
                .booking(booking)
                .providerEventId(providerEventId)
                .providerName(providerName != null ? providerName : "VNPAY")
                .amountReceived(amountReceived)
                .receivedAt(Instant.now())
                .safePayloadHash(safePayloadHash != null ? safePayloadHash : "UNKNOWN_HASH")
                .build();
        paymentReceiptRepository.save(receipt);

        List<BookingSessionCommitment> commitments = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        // Operations is the only authority for issued ticket identities. Never invent a ticket fallback.
        List<String> renderedTicketCodes = bookingConfirmationService
                .projectConfirmedTickets(booking, commitments);

        // Generate Magic Link Access with protected token hash
        String rawMagicToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawMagicToken);
        MagicLinkAccess magicLink = MagicLinkAccess.builder()
                .booking(booking)
                .tokenHash(tokenHash)
                .lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        magicLinkRepository.save(magicLink);

        // Create immutable notification intent
        String notifRef = "NOTIF-" + UUID.randomUUID();
        String accessLink = "/workshop-booking/access#" + rawMagicToken;
        String snapshotJson = buildDeliverySnapshotJson(booking, notifRef, "CONFIRMED_DELIVERY",
                renderedTicketCodes, accessLink, correlationReference);

        BookingNotificationIntent intent = BookingNotificationIntent.builder()
                .booking(booking)
                .notificationReference(notifRef)
                .notificationKind("CONFIRMED_DELIVERY")
                .correlationReference(correlationReference)
                .deliverySnapshot(snapshotJson)
                .build();
        notificationIntentRepository.save(intent);

        // Audit evidence
        Long confLocId = commitments.isEmpty() ? 36L : commitments.get(0).getLocationId();
        BookingAuditEvidence audit = BookingAuditEvidence.builder()
                .bookingCode(bookingCode)
                .locationId(confLocId)
                .actorType("SYSTEM")
                .action("PAYMENT_CONFIRMED")
                .outcome("ACCEPTED")
                .correlationReference(correlationReference)
                .occurredAt(Instant.now())
                .build();
        auditEvidenceRepository.save(audit);

        // Publish post-commit handoff event to Feature 007
        BookingNotificationReady readyEvent = new BookingNotificationReady(
                notifRef, bookingCode, "CONFIRMED_DELIVERY", correlationReference);
        publishPostCommitHandoff(readyEvent);

        return booking;
    }

    /**
     * Request ticket redelivery to stored booking contact.
     */
    @Transactional
    public void requestTicketRedelivery(
            String bookingCode,
            List<String> activeTicketReferences,
            String operationReference,
            String correlationReference
    ) {
        log.info("requestTicketRedelivery: bookingCode={}, opRef={}", bookingCode, operationReference);

        WorkshopBooking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking " + bookingCode + " not found"));

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new BookingConflictException("Cannot redeliver tickets for unconfirmed booking " + bookingCode);
        }

        List<BookingTicket> tickets = ticketRepository.findByBookingId(booking.getId());
        List<String> ticketCodes = tickets.stream().map(BookingTicket::getTicketCode).toList();

        String notifRef = "NOTIF-REDELIV-" + UUID.randomUUID();
        String snapshotJson = buildDeliverySnapshotJson(booking, notifRef, "CONFIRMATION_RESEND",
                ticketCodes, "/workshop-booking/lookup?code=" + bookingCode, correlationReference);

        BookingNotificationIntent intent = BookingNotificationIntent.builder()
                .booking(booking)
                .notificationReference(notifRef)
                .notificationKind("CONFIRMATION_RESEND")
                .correlationReference(correlationReference)
                .deliverySnapshot(snapshotJson)
                .build();
        notificationIntentRepository.save(intent);

        List<BookingSessionCommitment> redelivComms = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        Long redelivLocId = redelivComms.isEmpty() ? 36L : redelivComms.get(0).getLocationId();

        BookingAuditEvidence audit = BookingAuditEvidence.builder()
                .bookingCode(bookingCode)
                .locationId(redelivLocId)
                .actorType("STAFF")
                .action("CONFIRMATION_RESEND")
                .outcome("ACCEPTED")
                .correlationReference(correlationReference)
                .occurredAt(Instant.now())
                .build();
        auditEvidenceRepository.save(audit);

        BookingNotificationReady readyEvent = new BookingNotificationReady(
                notifRef, bookingCode, "CONFIRMATION_RESEND", correlationReference);
        publishPostCommitHandoff(readyEvent);
    }

    private void publishPostCommitHandoff(BookingNotificationReady event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        log.info("Publishing post-commit BookingNotificationReady: ref={}, booking={}",
                                event.notificationReference(), event.bookingReference());
                        eventPublisher.publishEvent(event);
                    } catch (Exception ex) {
                        log.warn("Post-commit notification delivery handoff caught exception (will not reverse booking): {}",
                                ex.getMessage());
                    }
                }
            });
        } else {
            try {
                eventPublisher.publishEvent(event);
            } catch (Exception ex) {
                log.warn("Notification delivery event dispatch exception: {}", ex.getMessage());
            }
        }
    }

    private String buildDeliverySnapshotJson(
            WorkshopBooking booking,
            String notifRef,
            String kind,
            List<String> ticketCodes,
            String accessLink,
            String correlationRef
    ) {
        Map<String, Object> map = new HashMap<>();
        map.put("notificationKind", kind);
        map.put("storedRecipient", booking.getContactEmail());
        map.put("renderedBookingContent", "Mland Workshop Booking Confirmation - Code: " + booking.getBookingCode());
        map.put("renderedTicketArtifacts", ticketCodes != null ? ticketCodes : List.of());
        map.put("bookingAccessLink", accessLink != null ? accessLink : "");
        map.put("correlationReference", correlationRef != null ? correlationRef : "UNKNOWN");
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{\"storedRecipient\":\"" + booking.getContactEmail() + "\"}";
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private UUID parseHoldReference(String reference) {
        UUID parsed = UUID.fromString(reference);
        if (!parsed.toString().equals(reference)) throw new IllegalArgumentException("Non-canonical UUID");
        return parsed;
    }
}
