package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

/** Safe outcome of a group support command (003.1 FR-048); carries no contact, link or token. */
public record GroupActionOutcomeResponse(String groupCode, String action, String outcome, String correlationId) {
}
