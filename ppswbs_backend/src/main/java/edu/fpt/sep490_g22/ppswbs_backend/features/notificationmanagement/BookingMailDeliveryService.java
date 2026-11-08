package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Sends the booking email for one notification, once. The first caller claims the notification by inserting its row
 * (the reference is unique); a handoff delivered twice therefore sends one email. A failed send is kept as FAILED and
 * retried a few times. Logs carry references and the exception type only: never the recipient, tickets or link.
 */
@Service
@Slf4j
class BookingMailDeliveryService {

    static final int MAX_ATTEMPTS = 5;
    private static final Duration RETRY_WINDOW = Duration.ofHours(24);

    private final NotificationDeliveryRepository deliveries;
    private final WorkshopBookingNotificationFacade bookings;
    private final MailAdapter mail;
    private final String frontendBaseUrl;

    BookingMailDeliveryService(NotificationDeliveryRepository deliveries, WorkshopBookingNotificationFacade bookings,
                               MailAdapter mail,
                               @org.springframework.beans.factory.annotation.Value("${app.frontend-base-url:http://localhost:5173}")
                               String frontendBaseUrl) {
        this.deliveries = deliveries;
        this.bookings = bookings;
        this.mail = mail;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Handles a new handoff. Never throws: a delivery problem must not touch the confirmed booking. */
    void deliver(String notificationReference, String bookingReference, String kind) {
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
            attempt(row);
        } catch (RuntimeException failure) {
            log.warn("Booking email {} could not be processed: {}", notificationReference, failure.getClass().getSimpleName());
        }
    }

    /** Retries failed deliveries (a mail server that was briefly down). */
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
                        attempt(claimed);
                    }
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Retrying booking emails failed: {}", failure.getClass().getSimpleName());
        }
    }

    private void attempt(NotificationDelivery row) {
        String status;
        String error = null;
        try {
            BookingNotificationDeliveryPayload payload = bookings.getBookingNotificationDeliveryPayload(
                    new BookingNotificationDeliveryPayloadQuery(row.getNotificationReference()));
            MailMessage message = payload == null ? null
                    : BookingMailComposer.compose(row.getNotificationKind(), row.getBookingReference(), payload, frontendBaseUrl);
            if (message == null) {
                status = NotificationDelivery.SKIPPED;
                error = payload == null ? "NO_PAYLOAD" : "KIND_NOT_MAILED";
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
            log.warn("Booking email {} failed on attempt {}: {}", row.getNotificationReference(), row.getAttempts(), error);
        }
        Instant now = Instant.now();
        row.setStatus(status);
        row.setLastError(error);
        row.setUpdatedAt(now);
        if (NotificationDelivery.SENT.equals(status)) row.setSentAt(now);
        deliveries.save(row);
        log.info("Booking email {} for {}: {}", row.getNotificationReference(), row.getBookingReference(), status);
    }
}
