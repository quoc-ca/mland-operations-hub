/** Owns workshop capacity, bookings, contact-email confirmation, and expiry. */
@ApplicationModule(displayName = "Workshop booking", allowedDependencies = {
        "members::facade", "catalogue::facade", "billing::facade"
})
package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import org.springframework.modulith.ApplicationModule;
