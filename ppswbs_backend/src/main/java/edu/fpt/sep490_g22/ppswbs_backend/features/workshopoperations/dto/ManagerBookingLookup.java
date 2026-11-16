package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import java.util.List;

/**
 * T116: OpenAPI 'ManagerBookingLookup' (spec FR-003b). Every ticket of one booking in every session and
 * location. Carries no contact email or phone, no QR value and no payment data.
 */
public record ManagerBookingLookup(String bookingCode, List<Ticket> tickets) {
}
