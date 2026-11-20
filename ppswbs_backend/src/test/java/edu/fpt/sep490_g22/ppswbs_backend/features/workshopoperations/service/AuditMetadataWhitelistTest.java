package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** T170 (a), FR-051: only whitelisted keys leave the server, as strings, and never an e-mail-like value. */
class AuditMetadataWhitelistTest {

    private final AuditMetadataWhitelist whitelist = new AuditMetadataWhitelist();

    @Test
    void keepsWhitelistedKeysAsStringsAndDropsUnknownKeys() {
        Map<String, String> result = whitelist.filter("""
                {"fromState":"PUBLISHED","toState":"CANCELLED","ticketCount":3,"movedTickets":true,
                 "phone":"0901234567","fullName":"Nguyen Van A","internalNote":"secret"}
                """);

        assertThat(result).containsOnly(
                Map.entry("fromState", "PUBLISHED"),
                Map.entry("toState", "CANCELLED"),
                Map.entry("ticketCount", "3"),
                Map.entry("movedTickets", "true"));
    }

    @Test
    void keepsAssignedAccountIdAsAnId() {
        assertThat(whitelist.filter("{\"assignedAccountId\":42}")).containsOnly(Map.entry("assignedAccountId", "42"));
    }

    @Test
    void invalidJsonGivesEmptyMetadata() {
        assertThat(whitelist.filter("{not json")).isEmpty();
        assertThat(whitelist.filter("[1,2,3]")).isEmpty();
        assertThat(whitelist.filter("\"text\"")).isEmpty();
    }

    @Test
    void nullBlankAndEmptyObjectGiveEmptyMetadata() {
        assertThat(whitelist.filter(null)).isEmpty();
        assertThat(whitelist.filter("   ")).isEmpty();
        assertThat(whitelist.filter("{}")).isEmpty();
    }

    @Test
    void dropsEmailLikeValuesEvenUnderAnAllowedKey() {
        Map<String, String> result = whitelist.filter(
                "{\"reasonCode\":\"CUSTOMER_REQUEST\",\"code\":\"someone@example.com\",\"status\":\"ok a@b.c\"}");

        assertThat(result).containsOnly(Map.entry("reasonCode", "CUSTOMER_REQUEST"));
        assertThat(result.values()).noneMatch(value -> value.contains("@"));
    }

    @Test
    void dropsNestedObjectsArraysAndNulls() {
        Map<String, String> result = whitelist.filter(
                "{\"code\":{\"a\":1},\"status\":[\"x\"],\"toState\":null,\"fromState\":\"OPEN\"}");

        assertThat(result).containsOnly(Map.entry("fromState", "OPEN"));
    }

    @Test
    void returnedMapIsNotModifiable() {
        Map<String, String> result = whitelist.filter("{\"code\":\"X\"}");

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.put("code", "Y"));
    }
}
