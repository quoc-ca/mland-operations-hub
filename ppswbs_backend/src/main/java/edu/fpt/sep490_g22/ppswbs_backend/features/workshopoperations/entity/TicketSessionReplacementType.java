package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

/** Why a ticket's session moved (003.2 FR-034); only the customer types count against the reschedule quota. */
public enum TicketSessionReplacementType {
    CUSTOMER_ADVANCE,
    CUSTOMER_LATE_ARRIVAL,
    MLAND;

    public boolean consumesQuota() {
        return this != MLAND;
    }
}
