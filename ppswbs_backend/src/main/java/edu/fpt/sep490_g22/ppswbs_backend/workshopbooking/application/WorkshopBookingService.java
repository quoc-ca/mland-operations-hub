package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application;

import edu.fpt.sep490_g22.ppswbs_backend.common.*;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberFacade;
import edu.fpt.sep490_g22.ppswbs_backend.payments.facade.PaymentGatewayPort;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Core application service for Workshop Booking lifecycle.
 * <p>
 * Implements:
 * - US1: Browse published packages
 * - US2: Create pending booking with capacity hold
 * - US3: Start/retry payment, process verified callback, confirm booking
 * - US4: Lookup booking (Guest proof or Member ownership)
 * - Scheduled: expire holds past deadline
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBookingService {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(15);

    // US1
    private final WorkshopPackageRepository packageRepository;
    private final WorkshopSessionRepository sessionRepository;

    // US2/US3/US4
    private final WorkshopBookingRepository bookingRepository;
    private final WorkshopInvoiceRepository invoiceRepository;
    private final CapacityHoldRepository holdRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentEventReceiptRepository receiptRepository;
    private final BookingTicketRepository ticketRepository;
    private final NotificationIntentRepository notificationIntentRepository;
    private final BookingMagicLinkRepository magicLinkRepository;
    private final ExternalRegistrationHandoffRepository handoffRepository;

    private final PaymentGatewayPort paymentGateway;
    private final MemberFacade memberFacade;
    private final AuditEventWriter auditEventWriter;

    // ─── US1: Discover a workshop package ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkshopPackage> listPublishedPackages() {
        return packageRepository.findByStatus("PUBLISHED");
    }

    @Transactional(readOnly = true)
    public WorkshopPackage getPublishedPackage(Long packageId) {
        return packageRepository.findByIdAndStatus(packageId, "PUBLISHED")
                .orElseThrow(() -> new ResourceNotFoundException("Workshop package not found or not available"));
    }

    @Transactional(readOnly = true)
    public List<WorkshopSession> getOpenSessionsForPackage(Long packageId) {
        // Ensure package exists and is published first
        getPublishedPackage(packageId);
        return sessionRepository.findByPackageIdAndStatus(packageId, "OPEN");
    }

    // ─── US2: Create a pending workshop booking ───────────────────────────────

    /**
     * Create a booking for a Guest (no Firebase token).
     * Validates package, session, capacity and contact fields atomically.
     * Creates booking, invoice and capacity hold in one transaction.
     */
    @Transactional
    public WorkshopBooking createGuestBooking(CreateBookingCommand cmd) {
        return createBookingInternal(cmd, null);
    }

    /**
     * Create a booking for an authenticated Member.
     * Auto-fills contact from Member profile if not overridden.
     */
    @Transactional
    public WorkshopBooking createMemberBooking(CreateBookingCommand cmd, String firebaseUid) {
        // Verify token → UID → Member mapping
        var member = memberFacade.getMemberByUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("Member account not found for the provided identity"));
        return createBookingInternal(cmd, member.getId());
    }

    private WorkshopBooking createBookingInternal(CreateBookingCommand cmd, Long memberId) {
        // 1. Validate package is published
        WorkshopPackage pkg = packageRepository.findByIdAndStatus(cmd.getPackageId(), "PUBLISHED")
                .orElseThrow(() -> new InvalidRequestException("Workshop package is not available for booking"));

        // 2. Lock session and check capacity
        WorkshopSession session = sessionRepository.findByIdWithLock(cmd.getSessionId())
                .filter(s -> s.getPackageId().equals(cmd.getPackageId()))
                .orElseThrow(() -> new InvalidRequestException("Workshop session is not available"));

        if (!session.isOpen()) {
            throw new InvalidRequestException("Workshop session is not open for booking");
        }

        int available = session.getAvailableCapacity();
        if (cmd.getParticipantCount() > available) {
            throw new InvalidRequestException(
                    "Insufficient capacity: requested " + cmd.getParticipantCount()
                            + " but only " + available + " available");
        }

        // 3. Generate unique booking code
        String bookingCode = generateUniqueBookingCode();

        // 4. Compute invoice snapshot
        long depositAmount = (pkg.getPrice() * pkg.getDepositPercent()) / 100L;

        // 5. Normalize contact
        String canonicalEmail = cmd.getContactEmail().trim().toLowerCase();
        String canonicalPhone = normalizePhone(cmd.getContactPhone());

        // 6. Create booking (PENDING_PAYMENT + UNPAID)
        WorkshopBooking booking = WorkshopBooking.builder()
                .bookingCode(bookingCode)
                .memberId(memberId)
                .packageId(pkg.getId())
                .sessionId(session.getId())
                .participantCount(cmd.getParticipantCount())
                .contactEmail(cmd.getContactEmail())
                .canonicalEmail(canonicalEmail)
                .contactPhone(cmd.getContactPhone())
                .canonicalPhone(canonicalPhone)
                .designReference(cmd.getDesignReference())
                .packageNameSnapshot(pkg.getName())
                .priceSnapshot(pkg.getPrice())
                .currencySnapshot(pkg.getCurrency())
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(BookingPaymentStatus.UNPAID)
                .build();
        booking = bookingRepository.save(booking);

        // 7. Create invoice snapshot
        WorkshopInvoice invoice = WorkshopInvoice.builder()
                .bookingId(booking.getId())
                .totalAmount(pkg.getPrice())
                .depositPercent(pkg.getDepositPercent())
                .depositAmount(depositAmount)
                .currency(pkg.getCurrency())
                .build();
        invoiceRepository.save(invoice);

        // 8. Reserve capacity and create hold
        session.setReservedParticipants(session.getReservedParticipants() + cmd.getParticipantCount());
        sessionRepository.save(session);

        Instant holdExpiry = Instant.now().plus(HOLD_DURATION);
        CapacityHold hold = CapacityHold.builder()
                .bookingId(booking.getId())
                .sessionId(session.getId())
                .participantCount(cmd.getParticipantCount())
                .expiresAt(holdExpiry)
                .releaseGeneration(0)
                .build();
        holdRepository.save(hold);

        auditEventWriter.writeEvent(AuditEvent.builder()
                .memberId(memberId)
                .eventType("BOOKING_CREATED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"bookingCode\":\"" + bookingCode + "\",\"participantCount\":" + cmd.getParticipantCount() + "}")
                .build());

        return booking;
    }

    // ─── US3: Pay the workshop deposit ───────────────────────────────────────

    /**
     * Start or retry payment for an active PENDING_PAYMENT/UNPAID booking.
     * Guest must provide matching contact proof; Member must be the booking owner.
     */
    @Transactional
    public PaymentGatewayPort.PaymentInitiationResult startPayment(
            String bookingCode, Long memberId, String contactPhone, String contactEmail) {

        WorkshopBooking booking = requireBooking(bookingCode);

        // Authorization
        authorizeForPaymentOrLookup(booking, memberId, contactPhone, contactEmail);

        if (!booking.isPendingPayment()) {
            throw new InvalidRequestException("Payment can only be started for PENDING_PAYMENT bookings");
        }

        // Check hold is still active
        CapacityHold hold = holdRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new InvalidRequestException("No active capacity hold found for this booking"));
        if (!hold.isActive()) {
            throw new InvalidRequestException("Capacity hold has expired; this booking can no longer be paid");
        }

        WorkshopInvoice invoice = invoiceRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for booking"));

        // Create a new payment attempt
        String attemptRef = UUID.randomUUID().toString();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .bookingId(booking.getId())
                .invoiceId(invoice.getId())
                .providerReference(attemptRef)
                .amount(invoice.getDepositAmount())
                .currency(invoice.getCurrency())
                .status(PaymentAttemptStatus.INITIATED)
                .build();
        paymentAttemptRepository.save(attempt);

        // Initiate with gateway
        PaymentGatewayPort.PaymentInitiationResult result = paymentGateway.initiate(
                PaymentGatewayPort.PaymentInitiationRequest.builder()
                        .bookingCode(bookingCode)
                        .providerAttemptReference(attemptRef)
                        .amount(invoice.getDepositAmount())
                        .currency(invoice.getCurrency())
                        .build()
        );

        // Update attempt with gateway expiry
        attempt.setGatewayExpiresAt(result.getExpiresAt());
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        paymentAttemptRepository.save(attempt);

        auditEventWriter.writeEvent(AuditEvent.builder()
                .memberId(memberId)
                .eventType("PAYMENT_INITIATED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"bookingCode\":\"" + bookingCode + "\"}")
                .build());

        return result;
    }

    /**
     * Verify and apply an idempotent provider callback.
     * Confirms booking only once after valid signed, matching, non-duplicate result.
     */
    @Transactional
    public CallbackApplicationResult applyPaymentCallback(PaymentGatewayPort.RawProviderCallback rawCallback) {
        // Idempotency: check if this event was already processed
        if (receiptRepository.existsByProviderEventId(rawCallback.getProviderEventId())) {
            log.info("Duplicate callback received for providerEventId={}, ignoring", rawCallback.getProviderEventId());
            WorkshopBooking booking = bookingRepository.findByBookingCode(rawCallback.getBookingCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
            return CallbackApplicationResult.of(booking, true);
        }

        // Verify signature, amount, currency, reference
        PaymentGatewayPort.VerifiedCallbackResult verified = paymentGateway.verifyCallback(rawCallback);

        if (!verified.isSignatureValid()) {
            log.warn("Invalid payment callback signature for bookingCode={}", rawCallback.getBookingCode());
            throw new InvalidRequestException("Payment callback signature verification failed");
        }

        WorkshopBooking booking = requireBooking(rawCallback.getBookingCode());
        WorkshopInvoice invoice = invoiceRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        // Idempotent receipt recording
        PaymentEventReceipt receipt = PaymentEventReceipt.builder()
                .bookingId(booking.getId())
                .providerEventId(verified.getProviderEventId())
                .providerReference(verified.getProviderReference())
                .outcome(verified.getOutcome())
                .amount(verified.getAmount())
                .currency(verified.getCurrency())
                .build();
        receiptRepository.save(receipt);

        // Update corresponding attempt
        paymentAttemptRepository.findTopByBookingIdAndStatusOrderByCreatedAtDesc(
                        booking.getId(), PaymentAttemptStatus.PENDING)
                .ifPresent(attempt -> {
                    PaymentAttemptStatus newStatus = "SUCCESS".equals(verified.getOutcome())
                            ? PaymentAttemptStatus.SUCCESS : PaymentAttemptStatus.FAILED;
                    attempt.setStatus(newStatus);
                    paymentAttemptRepository.save(attempt);
                });

        if ("SUCCESS".equals(verified.getOutcome())) {
            // Validate amount and currency match invoice
            if (verified.getAmount() != invoice.getDepositAmount() || !verified.getCurrency().equals(invoice.getCurrency())) {
                log.warn("Payment amount/currency mismatch for bookingCode={}: expected {}/{}, got {}/{}",
                        booking.getBookingCode(), invoice.getDepositAmount(), invoice.getCurrency(),
                        verified.getAmount(), verified.getCurrency());
                return CallbackApplicationResult.of(booking, false);
            }

            // Only confirm if still PENDING_PAYMENT (could have expired)
            if (!booking.isPendingPayment()) {
                log.warn("Late payment success for non-pending booking={}, status={}",
                        booking.getBookingCode(), booking.getBookingStatus());
                return CallbackApplicationResult.of(booking, false);
            }

            // Confirm booking
            booking.confirm();
            bookingRepository.save(booking);

            // Release the capacity hold (committed)
            holdRepository.findByBookingId(booking.getId()).ifPresent(hold -> {
                hold.setReleaseGeneration(1);
                hold.setReleasedAt(Instant.now());
                hold.setReleaseReason("CONFIRMED");
                holdRepository.save(hold);
            });

            // Issue QR ticket
            issueTicketAfterConfirmation(booking);

            // Create notification intent (non-blocking)
            createNotificationIntent(booking, "CONFIRMATION");

            // Create external handoff (non-blocking)
            createExternalHandoff(booking, verified.getProviderEventId());

            auditEventWriter.writeEvent(AuditEvent.builder()
                    .eventType("BOOKING_CONFIRMED")
                    .occurredAt(Instant.now())
                    .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                    .safeMetadata("{\"bookingCode\":\"" + booking.getBookingCode() + "\"}")
                    .build());
        }

        return CallbackApplicationResult.of(booking, false);
    }

    // ─── US4: Look up a booking ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BookingLookupResult lookupBooking(
            String bookingCode, Long memberId, String contactPhone, String contactEmail) {

        WorkshopBooking booking = requireBooking(bookingCode);
        authorizeForPaymentOrLookup(booking, memberId, contactPhone, contactEmail);

        BookingTicket ticket = null;
        if (booking.isConfirmed()) {
            ticket = ticketRepository.findByBookingIdAndRevokedFalse(booking.getId()).orElse(null);
        }

        CapacityHold hold = holdRepository.findByBookingId(booking.getId()).orElse(null);
        Instant holdExpiry = (hold != null && hold.isActive()) ? hold.getExpiresAt() : null;

        return BookingLookupResult.builder()
                .booking(booking)
                .ticket(ticket)
                .holdExpiresAt(holdExpiry)
                .paymentResumeAllowed(booking.isPendingPayment() && hold != null && hold.isActive())
                .build();
    }

    /**
     * Guest or Member explicit release of an active PENDING_PAYMENT hold.
     */
    @Transactional
    public void releaseHold(String bookingCode, Long memberId, String contactPhone, String contactEmail) {
        WorkshopBooking booking = requireBooking(bookingCode);
        authorizeForPaymentOrLookup(booking, memberId, contactPhone, contactEmail);

        if (!booking.isPendingPayment()) {
            throw new InvalidRequestException("Only PENDING_PAYMENT bookings can release their hold");
        }

        CapacityHold hold = holdRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new InvalidRequestException("No capacity hold found for this booking"));

        if (hold.isReleased()) {
            throw new InvalidRequestException("Hold has already been released");
        }

        // Idempotent release using generation
        hold.setReleaseGeneration(hold.getReleaseGeneration() + 1);
        hold.setReleasedAt(Instant.now());
        hold.setReleaseReason("CUSTOMER_RELEASE");
        holdRepository.save(hold);

        // Return capacity to session
        WorkshopSession session = sessionRepository.findById(hold.getSessionId()).orElse(null);
        if (session != null) {
            session.setReservedParticipants(
                    Math.max(0, session.getReservedParticipants() - hold.getParticipantCount()));
            sessionRepository.save(session);
        }

        // Transition booking state
        booking.cancelPendingHold();
        bookingRepository.save(booking);

        auditEventWriter.writeEvent(AuditEvent.builder()
                .memberId(memberId)
                .eventType("BOOKING_HOLD_RELEASED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"bookingCode\":\"" + bookingCode + "\"}")
                .build());
    }

    // ─── Magic Link ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BookingLookupResult lookupByMagicLink(String bookingCode, String rawToken) {
        WorkshopBooking booking = requireBooking(bookingCode);
        String tokenHash = hashToken(rawToken);

        BookingMagicLink magicLink = magicLinkRepository.findByBookingIdAndRevokedFalse(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Magic link not found or has been revoked"));

        if (!magicLink.getTokenHash().equals(tokenHash)) {
            throw new ResourceNotFoundException("Magic link is invalid");
        }

        if (!magicLink.isValid()) {
            throw new ResourceNotFoundException("Magic link has expired or been revoked");
        }

        BookingTicket ticket = booking.isConfirmed()
                ? ticketRepository.findByBookingIdAndRevokedFalse(booking.getId()).orElse(null)
                : null;

        return BookingLookupResult.builder()
                .booking(booking)
                .ticket(ticket)
                .holdExpiresAt(null)
                .paymentResumeAllowed(false)
                .build();
    }

    // ─── Scheduled: Expire pending holds ─────────────────────────────────────

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void expireHolds() {
        List<CapacityHold> expired = holdRepository.findExpiredUnreleasedHolds(Instant.now());
        for (CapacityHold hold : expired) {
            bookingRepository.findById(hold.getBookingId()).ifPresent(booking -> {
                if (booking.isPendingPayment()) {
                    booking.expire();
                    bookingRepository.save(booking);

                    hold.setReleaseGeneration(hold.getReleaseGeneration() + 1);
                    hold.setReleasedAt(Instant.now());
                    hold.setReleaseReason("EXPIRED");
                    holdRepository.save(hold);

                    // Return capacity
                    sessionRepository.findById(hold.getSessionId()).ifPresent(session -> {
                        session.setReservedParticipants(
                                Math.max(0, session.getReservedParticipants() - hold.getParticipantCount()));
                        sessionRepository.save(session);
                    });

                    auditEventWriter.writeEvent(AuditEvent.builder()
                            .eventType("BOOKING_EXPIRED")
                            .occurredAt(Instant.now())
                            .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                            .safeMetadata("{\"bookingCode\":\"" + booking.getBookingCode() + "\"}")
                            .build());
                }
            });
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private WorkshopBooking requireBooking(String bookingCode) {
        return bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    /**
     * Authorization for payment and lookup:
     * - If memberId is present: verify booking belongs to this member.
     * - If no memberId (Guest): verify exactly one of contactPhone or contactEmail matches.
     */
    private void authorizeForPaymentOrLookup(
            WorkshopBooking booking, Long memberId, String contactPhone, String contactEmail) {
        if (memberId != null) {
            if (!memberId.equals(booking.getMemberId())) {
                throw new ResourceNotFoundException("Booking not found");
            }
        } else {
            // Guest: exactly one proof required
            boolean phoneMatch = contactPhone != null
                    && normalizePhone(contactPhone).equals(booking.getCanonicalPhone());
            boolean emailMatch = contactEmail != null
                    && contactEmail.trim().toLowerCase().equals(booking.getCanonicalEmail());

            if (!phoneMatch && !emailMatch) {
                throw new ResourceNotFoundException("Booking not found");
            }
        }
    }

    private void issueTicketAfterConfirmation(WorkshopBooking booking) {
        // Check for existing active ticket (idempotent)
        if (ticketRepository.findByBookingIdAndRevokedFalse(booking.getId()).isPresent()) {
            return;
        }

        // QR value: signed opaque reference only, no PII
        String qrValue = "WS-QR-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();

        // Ticket valid until session ends (stub: +1 year from now until session end is available)
        WorkshopSession session = sessionRepository.findById(booking.getSessionId()).orElse(null);
        Instant validUntil = session != null
                ? session.getSessionDate().atStartOfDay().plusDays(1).toInstant(java.time.ZoneOffset.UTC)
                : Instant.now().plus(Duration.ofDays(365));

        BookingTicket ticket = BookingTicket.builder()
                .bookingId(booking.getId())
                .qrValue(qrValue)
                .validUntil(validUntil)
                .revoked(false)
                .build();
        ticketRepository.save(ticket);

        // Issue magic link (hash only; raw token sent via notification)
        String rawMagicToken = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawMagicToken);

        BookingMagicLink magicLink = BookingMagicLink.builder()
                .bookingId(booking.getId())
                .tokenHash(tokenHash)
                .expiresAt(validUntil)
                .revoked(false)
                .build();
        magicLinkRepository.save(magicLink);
    }

    private void createNotificationIntent(WorkshopBooking booking, String intentType) {
        NotificationIntent intent = NotificationIntent.builder()
                .bookingId(booking.getId())
                .intentType(intentType)
                .recipientEmailSnapshot(booking.getContactEmail())
                .status("PENDING")
                .attemptCount(0)
                .nextRetryAt(Instant.now())
                .build();
        notificationIntentRepository.save(intent);
    }

    private void createExternalHandoff(WorkshopBooking booking, String eventIdentity) {
        if (handoffRepository.existsByBookingIdAndEventIdentity(booking.getId(), eventIdentity)) {
            return; // Idempotent
        }
        ExternalRegistrationHandoff handoff = ExternalRegistrationHandoff.builder()
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .eventIdentity(eventIdentity)
                .status("PENDING")
                .attemptCount(0)
                .build();
        handoffRepository.save(handoff);
    }

    private String generateUniqueBookingCode() {
        String code;
        int attempts = 0;
        do {
            code = "WS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase().replace("-", "");
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate unique booking code");
            }
        } while (bookingRepository.existsByBookingCode(code));
        return code;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.trim().replaceAll("[\\s\\-()]", "");
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void releaseExpiredHoldInTransaction(Long holdId) {
        CapacityHold hold = holdRepository.findById(holdId).orElse(null);
        if (hold == null || hold.getReleaseGeneration() > 0) {
            return; // Already released
        }

        WorkshopBooking booking = bookingRepository.findById(hold.getBookingId()).orElse(null);
        if (booking != null && booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT) {
            booking.setBookingStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);

            WorkshopSession session = sessionRepository.findByIdWithLock(hold.getSessionId()).orElse(null);
            if (session != null) {
                session.setReservedParticipants(session.getReservedParticipants() - hold.getParticipantCount());
                sessionRepository.save(session);
            }
        }

        hold.setReleaseGeneration(hold.getReleaseGeneration() + 1);
        hold.setReleasedAt(Instant.now());
        hold.setReleaseReason("EXPIRED");
        holdRepository.save(hold);
    }
}
