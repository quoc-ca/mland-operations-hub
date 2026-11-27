package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

/** One Staff the Manager may assign to a session (FR-069); `fullName` is null when the profile has none. */
public record AssignableStaffResponse(Long accountId, String fullName) {
}
