package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.UUID;

/**
 * 003.2 Phase 18 test support: a Package is sellable at a location only when its pair row is ACTIVE, so fixtures
 * that build a Package and then reserve or move sessions must switch the pair on for the sessions' locations.
 * Works on H2 (MODE=MySQL) and MySQL; pairs that already have a row are left alone.
 */
public final class PackageAvailabilityFixture {

    private PackageAvailabilityFixture() {
    }

    /** Marks the Package ACTIVE at every location of the given sessions (by session reference). */
    public static void activateAtSessionLocations(JdbcTemplate jdbc, Long packageId, Collection<UUID> sessionReferences) {
        for (UUID reference : sessionReferences) {
            jdbc.update("INSERT INTO workshop_package_location_availability "
                    + "(package_id, location_id, active, version, created_at, updated_at) "
                    + "SELECT ?, s.location_id, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM workshop_sessions s "
                    + "WHERE s.reference = ? AND NOT EXISTS (SELECT 1 FROM workshop_package_location_availability a "
                    + "WHERE a.package_id = ? AND a.location_id = s.location_id)",
                    packageId, reference.toString(), packageId);
        }
    }

    /** Marks the Package ACTIVE at the given location. */
    public static void activateAt(JdbcTemplate jdbc, Long packageId, Long locationId) {
        jdbc.update("INSERT INTO workshop_package_location_availability "
                + "(package_id, location_id, active, version, created_at, updated_at) "
                + "SELECT ?, ?, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM (SELECT 1) one "
                + "WHERE NOT EXISTS (SELECT 1 FROM workshop_package_location_availability a "
                + "WHERE a.package_id = ? AND a.location_id = ?)", packageId, locationId, packageId, locationId);
    }

    /** Marks the Package ACTIVE at every existing location (for fixtures that do not care where they book). */
    public static void activateEverywhere(JdbcTemplate jdbc, Long packageId) {
        jdbc.update("INSERT INTO workshop_package_location_availability "
                + "(package_id, location_id, active, version, created_at, updated_at) "
                + "SELECT ?, l.id, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM workshop_locations l "
                + "WHERE NOT EXISTS (SELECT 1 FROM workshop_package_location_availability a "
                + "WHERE a.package_id = ? AND a.location_id = l.id)", packageId, packageId);
    }
}
