package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

/**
 * Why a ticket's sessions move (003.2 FR-034). Only the customer types consume the reschedule quota of the
 * target sessions (FR-035); a Mland-caused replacement is checked against capacity only.
 */
public enum TicketReplacementType {
    CUSTOMER_ADVANCE,
    CUSTOMER_LATE_ARRIVAL,
    MLAND
}
