/**
 * Owns workshop package discovery, capacity holds, bookings, payment processing,
 * QR ticket issuance, notification intents, Magic Links and external handoff records.
 * Cross-module access only via members::facade and payments::facade.
 */
@ApplicationModule(displayName = "Workshop booking", allowedDependencies = {
        "members::facade", "payments::facade"
})
package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import org.springframework.modulith.ApplicationModule;
