package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationReady;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Receives the Booking post-commit handoff and sends the email on its own thread, so the payment confirmation that
 * raised it never waits for a mail server. The event is published after the booking transaction committed, which is
 * why a plain listener is used.
 */
@Component
class BookingNotificationReadyConsumer {

    private final BookingMailDeliveryService delivery;
    private final Executor executor;
    private final ExecutorService owned;

    @Autowired
    BookingNotificationReadyConsumer(BookingMailDeliveryService delivery) {
        this(delivery, Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "booking-mail");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    /** For tests: run the delivery on the calling thread. */
    BookingNotificationReadyConsumer(BookingMailDeliveryService delivery, Executor executor) {
        this(delivery, executor, false);
    }

    private BookingNotificationReadyConsumer(BookingMailDeliveryService delivery, Executor executor, boolean owns) {
        this.delivery = delivery;
        this.executor = executor;
        this.owned = owns ? (ExecutorService) executor : null;
    }

    @EventListener
    void onBookingNotificationReady(BookingNotificationReady event) {
        executor.execute(() -> delivery.deliver(event.notificationReference(), event.bookingReference(), event.notificationKind()));
    }

    @PreDestroy
    void shutdown() {
        if (owned != null) owned.shutdown();
    }
}
