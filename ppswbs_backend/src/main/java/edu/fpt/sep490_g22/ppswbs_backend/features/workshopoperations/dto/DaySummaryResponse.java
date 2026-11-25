package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import java.time.LocalDate;

/**
 * navigation shell and dashboard (014 R-1): the workshop sessions of one Vietnam day. {@code sessions} includes cancelled sessions;
 * {@code checkedIn} and {@code expected} cover the sessions that are not cancelled and count people holding a
 * valid (ACTIVE) ticket, not the sessions' capacity.
 */
public record DaySummaryResponse(LocalDate date, long sessions, long cancelledSessions, long checkedIn, long expected) { }
