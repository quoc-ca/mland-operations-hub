/**
 * FE-10 Manager reporting: the read-only executive dashboard. It composes committed facts owned by retail
 * (FE-08) and workshop (FE-03) through read-only source queries in {@code reporting.source}; it never changes them.
 */
@org.springframework.modulith.ApplicationModule(
    id = "reporting",
    displayName = "Manager Reporting",
    allowedDependencies = {"accountmanagement :: facade"}
)
package edu.fpt.sep490_g22.ppswbs_backend.features.reporting;
