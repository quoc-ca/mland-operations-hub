package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationReady;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Turns Settlement's post-commit handoff into an email (003.3 FR-040: refund-account request and the other customer
 * notifications Settlement already records). Same delivery rules as Booking mail: once per notification reference,
 * retried on failure, no recipient or link in logs.
 */
@Component
class SettlementNotificationReadyConsumer {

    private final BookingMailDeliveryService delivery;
    private final Executor executor;
    private final ExecutorService owned;

    @Autowired
    SettlementNotificationReadyConsumer(BookingMailDeliveryService delivery) {
        this(delivery, Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "settlement-mail");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    /** For tests: run the delivery on the calling thread. */
    SettlementNotificationReadyConsumer(BookingMailDeliveryService delivery, Executor executor) {
        this(delivery, executor, false);
    }

    private SettlementNotificationReadyConsumer(BookingMailDeliveryService delivery, Executor executor, boolean owns) {
        this.delivery = delivery;
        this.executor = executor;
        this.owned = owns ? (ExecutorService) executor : null;
    }

    @EventListener
    void onSettlementNotificationReady(SettlementNotificationReady event) {
        executor.execute(() -> delivery.deliver(event.notificationReference(), event.settlementReference(),
                event.notificationKind()));
    }

    @PreDestroy
    void shutdown() {
        if (owned != null) owned.shutdown();
    }
}
