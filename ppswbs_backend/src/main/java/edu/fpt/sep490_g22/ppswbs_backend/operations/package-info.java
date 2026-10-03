/** Owns staff check-in and other operational workflows. */
@ApplicationModule(displayName = "Operations", allowedDependencies = {
        "members::facade", "workshopbooking::facade", "billing::facade", "fulfilment::facade"
})
package edu.fpt.sep490_g22.ppswbs_backend.operations;

import org.springframework.modulith.ApplicationModule;
