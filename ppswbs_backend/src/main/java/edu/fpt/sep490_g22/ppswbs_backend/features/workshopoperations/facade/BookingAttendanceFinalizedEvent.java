package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

/** Durable, reference-only notification that a booking attendance outcome can no longer change. */
public record BookingAttendanceFinalizedEvent(String bookingReference, Instant finalizedAt) {}
