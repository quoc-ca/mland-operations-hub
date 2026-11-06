package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkshopVisibleLocationPolicyTest {

    @Test
    void emptyConfigurationAllowsAllLocations() {
        var policy = new WorkshopVisibleLocationPolicy("");

        assertTrue(policy.allows(1L));
        assertTrue(policy.allows(999L));
    }

    @Test
    void configuredLocalAllowlistHidesFixtureLocations() {
        var policy = new WorkshopVisibleLocationPolicy("1, 2");

        assertTrue(policy.allows(1L));
        assertTrue(policy.allows(2L));
        assertFalse(policy.allows(3L));
    }

    @Test
    void invalidLocalAllowlistFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new WorkshopVisibleLocationPolicy("1, fixture"));
    }
}
