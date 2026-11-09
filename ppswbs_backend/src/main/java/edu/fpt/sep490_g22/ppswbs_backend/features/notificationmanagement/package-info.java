/**
 * Feature 007 Notification Management:
 * Modular email infrastructure for Mland transactional business notifications,
 * supporting durable requests, poll-based delivery worker with retry/recovery,
 * template version lifecycle management, delivery diagnostics, manual operations,
 * and data retention scrub.
 */
@org.springframework.modulith.ApplicationModule(
        id = "notificationmanagement",
        displayName = "Notification Management",
        allowedDependencies = { "workshopbooking :: notification", "accountmanagement :: facade" }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;
