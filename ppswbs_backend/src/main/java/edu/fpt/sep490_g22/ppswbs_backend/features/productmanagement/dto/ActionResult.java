package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Confirmed minimal command result (OpenAPI {@code ActionResult}). Stored as the receipt's safe result
 * and replayed unchanged; never contains the request payload or credentials.
 */
public record ActionResult(UUID commandId, String resourceId, String resourceType, String action, String status,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Long version, boolean changed) {
}
