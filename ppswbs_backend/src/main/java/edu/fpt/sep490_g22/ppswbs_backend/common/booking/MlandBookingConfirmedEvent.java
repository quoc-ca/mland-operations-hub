package edu.fpt.sep490_g22.ppswbs_backend.common.booking;

import java.time.Instant;

/** Durable, PII-free handoff for policy snapshotting after a booking is confirmed. */
public record MlandBookingConfirmedEvent(String bookingReference, Instant confirmedAt) { }
