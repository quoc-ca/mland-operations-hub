/** Owns ready-ring availability, Member sales, reservation, and delivery choice. */
@ApplicationModule(displayName = "Ready-ring sales", allowedDependencies = {
        "members::facade", "catalogue::facade", "billing::facade"
})
package edu.fpt.sep490_g22.ppswbs_backend.readyringsales;

import org.springframework.modulith.ApplicationModule;
