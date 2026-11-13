/**
 * Feature-012 Workshop Ring Design Configuration:
 * Configured ring designs, version lineage, Guest opaque sessions with 20-min inactivity expiry,
 * package-scoped options catalog, advisory evaluation and Workshop Booking attachment facade.
 */
@ApplicationModule(
    displayName = "Workshop Ring Design Configuration (FE-12)",
    allowedDependencies = {"accountmanagement :: facade"}
)
package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import org.springframework.modulith.ApplicationModule;
