package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

/** A quote reason with a stable code and a Vietnamese message; {@code message} falls back to the original text. */
public record QuoteReasonDetail(String code, String message) {
}
