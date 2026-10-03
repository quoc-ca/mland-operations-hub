package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application;

import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEventWriter;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidRequestException;
import edu.fpt.sep490_g22.ppswbs_backend.common.ResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.BookingEmailConfirmationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.BookingMailSender;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBookingService {

    private static final Duration EXPIRY_DURATION = Duration.ofMinutes(15);

    private final WorkshopBookingRepository bookingRepository;
    private final BookingEmailConfirmationRepository confirmationRepository;
    private final BookingMailSender mailSender;
    private final AuditEventWriter auditEventWriter;

    @Transactional
    public WorkshopBooking createGuestBooking(String contactEmail) {
        String canonicalEmail = contactEmail.trim().toLowerCase();
        String bookingCode = "WB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        WorkshopBooking booking = WorkshopBooking.builder()
                .bookingCode(bookingCode)
                .contactEmail(contactEmail)
                .canonicalEmail(canonicalEmail)
                .confirmationState(ConfirmationState.EMAIL_CONFIRMATION_PENDING)
                .bookingStatus(BookingStatus.PENDING)
                .build();
        booking = bookingRepository.save(booking);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        BookingEmailConfirmation confirmation = BookingEmailConfirmation.builder()
                .bookingId(booking.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(EXPIRY_DURATION))
                .state(ConfirmationTokenState.PENDING)
                .build();
        confirmationRepository.save(confirmation);

        mailSender.sendConfirmationEmail(contactEmail, bookingCode, rawToken);

        auditEventWriter.writeEvent(AuditEvent.builder()
                .eventType("BOOKING_EMAIL_CONFIRMATION_REQUESTED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"bookingCode\":\"" + bookingCode + "\"}")
                .build());

        return booking;
    }

    @Transactional
    public void confirmBookingEmail(String bookingCode, String rawConfirmationToken) {
        WorkshopBooking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingCode));

        BookingEmailConfirmation confirmation = confirmationRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Confirmation record not found"));

        if (confirmation.getState() != ConfirmationTokenState.PENDING) {
            throw new InvalidRequestException("Confirmation token has already been used or expired");
        }

        if (confirmation.getExpiresAt().isBefore(Instant.now())) {
            expireSingleBooking(booking, confirmation);
            throw new InvalidRequestException("Confirmation token has expired (15-minute limit exceeded)");
        }

        String inputHash = hashToken(rawConfirmationToken);
        if (!inputHash.equals(confirmation.getTokenHash())) {
            throw new InvalidRequestException("Invalid confirmation token");
        }

        confirmation.setState(ConfirmationTokenState.CONFIRMED);
        confirmation.setConfirmedAt(Instant.now());
        confirmationRepository.save(confirmation);

        booking.setConfirmationState(ConfirmationState.EMAIL_CONFIRMED);
        bookingRepository.save(booking);

        auditEventWriter.writeEvent(AuditEvent.builder()
                .eventType("BOOKING_EMAIL_CONFIRMED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"bookingCode\":\"" + bookingCode + "\"}")
                .build());
    }

    @Transactional(readOnly = true)
    public WorkshopBooking openDepositPayment(String bookingCode) {
        WorkshopBooking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingCode));

        if (booking.getConfirmationState() == ConfirmationState.EMAIL_CONFIRMATION_PENDING) {
            throw new InvalidRequestException("Booking email MUST be confirmed before opening deposit payment");
        }

        if (booking.getConfirmationState() == ConfirmationState.CANCELLED_EMAIL_UNCONFIRMED) {
            throw new InvalidRequestException("Booking confirmation expired and was cancelled");
        }

        return booking;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void expirePendingConfirmations() {
        List<BookingEmailConfirmation> expiredConfirmations =
                confirmationRepository.findByStateAndExpiresAtBefore(ConfirmationTokenState.PENDING, Instant.now());

        for (BookingEmailConfirmation confirmation : expiredConfirmations) {
            bookingRepository.findById(confirmation.getBookingId()).ifPresent(booking -> {
                expireSingleBooking(booking, confirmation);
            });
        }
    }

    private void expireSingleBooking(WorkshopBooking booking, BookingEmailConfirmation confirmation) {
        confirmation.setState(ConfirmationTokenState.EXPIRED);
        confirmationRepository.save(confirmation);

        booking.setConfirmationState(ConfirmationState.CANCELLED_EMAIL_UNCONFIRMED);
        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        auditEventWriter.writeEvent(AuditEvent.builder()
                .eventType("BOOKING_EMAIL_EXPIRED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"bookingCode\":\"" + booking.getBookingCode() + "\"}")
                .build());
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
}
