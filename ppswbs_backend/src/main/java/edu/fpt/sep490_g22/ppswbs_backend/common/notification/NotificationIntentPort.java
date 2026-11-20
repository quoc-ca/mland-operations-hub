package edu.fpt.sep490_g22.ppswbs_backend.common.notification;

import java.util.Map;

/**
 * Technical port through which a feature asks Notification Management (007) to enqueue one notification intent
 * without depending on the {@code notificationmanagement} module. It exists so that Workshop Operations can alert
 * Managers while Notification Management keeps reading Workshop Booking, which reads Operations: a direct
 * {@code workshopoperations -> notificationmanagement} edge closed a module cycle (Spring Modulith).
 *
 * <p>Same contract as {@code NotificationFacade.submitNotificationIntent}: it runs inside the caller's transaction,
 * never throws for a validation problem and never rolls the caller back.
 */
public interface NotificationIntentPort {

    /**
     * @return true when the intent was enqueued or was already known (duplicate); false when Notification
     *         Management recorded an operational failure or gave no answer, so the caller may retry later
     */
    boolean submitIntent(String businessEventReference, String templateCode, String recipientEmail,
                         Map<String, Object> businessDataSnapshot);
}
