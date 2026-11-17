package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryFailed;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.DeliveryFailureCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopSettlementFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Sends the booking email for one notification, once. The first caller claims the notification by inserting its row
 * (the reference is unique); a handoff delivered twice therefore sends one email. A failed send is kept as FAILED and
 * retried with at most 2 technical attempts (initial send + 1 scheduled technical retry after ~1 minute, FR-029).
 * When technical retries are exhausted without successful acceptance, publishes {@link BookingNotificationDeliveryFailed}
 * to allow Feature 003.1 to manage business retries and escalation. Logs carry references and the exception type only:
 * never the recipient, tickets or link.
 */
@Service
@Slf4j
class BookingMailDeliveryService {

    static final int MAX_ATTEMPTS = 2;
    private static final Duration RETRY_WINDOW = Duration.ofHours(24);

    private final NotificationDeliveryRepository deliveries;
    private final WorkshopBookingNotificationFacade bookings;
    private final MailAdapter mail;
    private final String frontendBaseUrl;
    /** Settlement customer emails (003.3, e.g. the refund-account request); null in booking-only unit tests. */
    private final WorkshopSettlementFacade settlements;
    private final ApplicationEventPublisher eventPublisher;

    BookingMailDeliveryService(NotificationDeliveryRepository deliveries, WorkshopBookingNotificationFacade bookings,
                               MailAdapter mail, String frontendBaseUrl) {
        this(deliveries, bookings, mail, frontendBaseUrl, null, null);
    }

    BookingMailDeliveryService(NotificationDeliveryRepository deliveries, WorkshopBookingNotificationFacade bookings,
                               MailAdapter mail, String frontendBaseUrl, ApplicationEventPublisher eventPublisher) {
        this(deliveries, bookings, mail, frontendBaseUrl, null, eventPublisher);
    }

    @org.springframework.beans.factory.annotation.Autowired
    BookingMailDeliveryService(NotificationDeliveryRepository deliveries, WorkshopBookingNotificationFacade bookings,
                               MailAdapter mail,
                               @org.springframework.beans.factory.annotation.Value("${app.frontend-base-url:http://localhost:5173}")
                               String frontendBaseUrl,
                               @org.springframework.context.annotation.Lazy WorkshopSettlementFacade settlements,
                               ApplicationEventPublisher eventPublisher) {
        this.deliveries = deliveries;
        this.bookings = bookings;
        this.mail = mail;
        this.frontendBaseUrl = frontendBaseUrl;
        this.settlements = settlements;
        this.eventPublisher = eventPublisher;
    }

    /** Settlement handoffs are referenced {@code SETTLE-NOTIF-…}; everything else is a Booking handoff. */
    static boolean isSettlementNotification(String notificationReference) {
        return notificationReference != null && notificationReference.startsWith("SETTLE-NOTIF-");
    }

    /** Handles a new handoff. Never throws: a delivery problem must not touch the confirmed booking. */
    void deliver(String notificationReference, String bookingReference, String kind) {
        deliver(notificationReference, bookingReference, kind, null);
    }

    /** Handles a new handoff with correlation reference. Never throws: a delivery problem must not touch the confirmed booking. */
    void deliver(String notificationReference, String bookingReference, String kind, String correlationReference) {
        try {
            Instant now = Instant.now();
            NotificationDelivery row = new NotificationDelivery();
            row.setNotificationReference(notificationReference);
            row.setBookingReference(bookingReference);
            row.setNotificationKind(kind);
            row.setStatus(NotificationDelivery.SENDING);
            row.setAttempts(1);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            try {
                row = deliveries.saveAndFlush(row);
            } catch (DataIntegrityViolationException alreadyHandled) {
                log.debug("Notification {} was already handled", notificationReference);
                return;
            }
            attempt(row, correlationReference);
        } catch (RuntimeException failure) {
            log.warn("Booking email {} could not be processed: {}", notificationReference, failure.getClass().getSimpleName());
        }
    }

    /** Retries failed deliveries (a mail server that was briefly down). Polled per FR-028/FR-029. */
    @Scheduled(initialDelayString = "${app.mail.retry-initial-delay-ms:60000}", fixedDelayString = "${app.mail.retry-delay-ms:300000}")
    void retryFailed() {
        try {
            Instant now = Instant.now();
            for (NotificationDelivery row : deliveries.findTop50ByStatusAndAttemptsLessThanAndCreatedAtAfterOrderByCreatedAtAsc(
                    NotificationDelivery.FAILED, MAX_ATTEMPTS, now.minus(RETRY_WINDOW))) {
                if (deliveries.claimForRetry(row.getId(), now) == 1) {
                    NotificationDelivery claimed = deliveries.findById(row.getId()).orElse(null);
                    if (claimed != null) {
                        claimed.setAttempts(claimed.getAttempts() + 1);
                        attempt(claimed, null);
                    }
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Retrying booking emails failed: {}", failure.getClass().getSimpleName());
        }
    }

    private void attempt(NotificationDelivery row, String fallbackCorrelationReference) {
        String status;
        String error = null;
        Exception caughtException = null;
        String correlationReference = fallbackCorrelationReference;
        try {
            Object payload;
            MailMessage message;
            if (isSettlementNotification(row.getNotificationReference())) {
                SettlementNotificationDeliveryPayload settlement = settlements == null ? null
                        : settlements.getSettlementNotificationDeliveryPayload(
                                new SettlementNotificationDeliveryPayloadQuery(row.getNotificationReference()));
                payload = settlement;
                message = settlement == null ? null
                        : SettlementMailComposer.compose(row.getBookingReference(), settlement, frontendBaseUrl);
            } else {
                BookingNotificationDeliveryPayload booking = bookings.getBookingNotificationDeliveryPayload(
                        new BookingNotificationDeliveryPayloadQuery(row.getNotificationReference()));
                payload = booking;
                if (booking != null && booking.correlationReference() != null && !booking.correlationReference().isBlank()) {
                    correlationReference = booking.correlationReference();
                }
                message = booking == null ? null
                        : BookingMailComposer.compose(row.getNotificationKind(), row.getBookingReference(), booking, frontendBaseUrl);
            }
            if (message == null) {
                if (payload == null) {
                    if (isSettlementNotification(row.getNotificationReference())) {
                        status = NotificationDelivery.SKIPPED;
                        error = "NO_PAYLOAD";
                    } else {
                        status = NotificationDelivery.FAILED;
                        error = "PAYLOAD_MISSING";
                    }
                } else {
                    status = NotificationDelivery.SKIPPED;
                    error = "KIND_NOT_MAILED";
                }
            } else if (message.to() == null || message.to().isBlank()) {
                status = NotificationDelivery.SKIPPED;
                error = "NO_RECIPIENT";
            } else {
                mail.send(row.getNotificationReference(), message);
                status = NotificationDelivery.SENT;
            }
        } catch (Exception failure) {
            status = NotificationDelivery.FAILED;
            error = failure.getClass().getSimpleName();
            caughtException = failure;
            log.warn("Booking email {} failed on attempt {}: {}", row.getNotificationReference(), row.getAttempts(), error);
        }
        Instant now = Instant.now();
        row.setStatus(status);
        row.setLastError(error);
        row.setUpdatedAt(now);
        if (NotificationDelivery.SENT.equals(status)) row.setSentAt(now);
        deliveries.save(row);
        log.info("Booking email {} for {}: {}", row.getNotificationReference(), row.getBookingReference(), status);

        // Emit failure event when technical retries are exhausted (FR-029)
        if (eventPublisher != null && NotificationDelivery.FAILED.equals(status)
                && row.getAttempts() >= MAX_ATTEMPTS
                && !isSettlementNotification(row.getNotificationReference())) {
            DeliveryFailureCode failureCode = determineFailureCode(caughtException, error);
            try {
                eventPublisher.publishEvent(new BookingNotificationDeliveryFailed(
                        row.getNotificationReference(),
                        row.getBookingReference(),
                        row.getNotificationKind(),
                        failureCode,
                        now,
                        correlationReference
                ));
            } catch (RuntimeException ex) {
                log.warn("Failed to publish BookingNotificationDeliveryFailed for {}: {}",
                        row.getNotificationReference(), ex.getClass().getSimpleName());
            }
        }
    }

    /**
     * Maps the underlying delivery error into one of the four contract DeliveryFailureCode values.
     */
    static DeliveryFailureCode determineFailureCode(Exception failure, String lastError) {
        if ("PAYLOAD_MISSING".equals(lastError)) {
            return DeliveryFailureCode.PAYLOAD_MISSING;
        }
        if (failure == null) {
            return DeliveryFailureCode.PROVIDER_UNAVAILABLE;
        }

        Throwable current = failure;
        while (current != null) {
            String exName = current.getClass().getSimpleName();
            String msg = current.getMessage() != null ? current.getMessage().toLowerCase() : "";

            if (exName.contains("AddressException")
                    || exName.contains("MailParseException")
                    || msg.contains("invalid address")
                    || msg.contains("illegal address")
                    || msg.contains("unknown user")
                    || msg.contains("user unknown")
                    || msg.contains("mailbox unavailable")
                    || msg.contains("mailbox not found")
                    || msg.contains("no such user")
                    || msg.contains("550")
                    || msg.contains("551")
                    || msg.contains("553")) {
                return DeliveryFailureCode.RECIPIENT_INVALID;
            }

            if (exName.contains("Authentication")
                    || msg.contains("authentication")
                    || msg.contains("relay denied")
                    || msg.contains("500")
                    || msg.contains("501")
                    || msg.contains("502")
                    || msg.contains("503")
                    || msg.contains("504")
                    || msg.contains("554")
                    || msg.contains("rejected")) {
                return DeliveryFailureCode.PROVIDER_REJECTED;
            }

            current = current.getCause();
        }

        return DeliveryFailureCode.PROVIDER_UNAVAILABLE;
    }
}
