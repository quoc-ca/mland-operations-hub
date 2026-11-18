/**
 * Feature-013 Staff Consultation Chat: Customer (Member) consultation chat with Staff / Manager.
 * Context-aware, auditable, UTF-8 plain text only, rate-limited and scoped to Workshop Locations.
 */
@ApplicationModule(
        displayName = "Staff Consultation Chat (FE-13)",
        allowedDependencies = {"accountmanagement :: facade", "workshopoperations :: facade", "ringdesignconfiguration :: facade"})
package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat;

import org.springframework.modulith.ApplicationModule;
