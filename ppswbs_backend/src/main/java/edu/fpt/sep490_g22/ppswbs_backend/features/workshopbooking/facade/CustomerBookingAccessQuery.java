package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/** Exactly one proof mode is supplied. Contact values are consumed only by Booking and never returned. */
public record CustomerBookingAccessQuery(String bookingReference, Long memberAccountId,
                                         String contactEmail, String contactPhone) { }
