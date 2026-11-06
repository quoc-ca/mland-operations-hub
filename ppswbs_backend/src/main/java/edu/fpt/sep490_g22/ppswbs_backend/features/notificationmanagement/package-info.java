/**
 * Minimal Feature 007 delivery: turns the Booking post-commit handoff ({@code BookingNotificationReady}) into an email
 * with the tickets and the booking access link (Magic Link). It reads the protected snapshot only through the Booking
 * notification facade, sends once per notification reference, records the outcome without any recipient or link, and
 * retries failures a few times. The full notification management of 007 can replace this module.
 */
@org.springframework.modulith.ApplicationModule(
        id = "notificationmanagement",
        displayName = "Notification Management (minimal booking mail)",
        allowedDependencies = { "workshopbooking :: notification" }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;
