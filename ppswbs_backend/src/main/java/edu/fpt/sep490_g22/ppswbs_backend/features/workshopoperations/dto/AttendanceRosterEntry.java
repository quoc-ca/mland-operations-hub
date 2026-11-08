package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;

import java.time.Instant;

/** One valid ticket of a session and whether its holder has checked in. No customer contact data. */
public record AttendanceRosterEntry(String ticketCode, String bookingReference, Integer ticketSequence,
                                    AttendanceState attendanceState, Instant checkedInAt) { }
