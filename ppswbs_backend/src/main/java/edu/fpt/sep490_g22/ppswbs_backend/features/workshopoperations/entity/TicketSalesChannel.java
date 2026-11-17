package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

/**
 * Where a ticket was sold (003.2 FR-036). An OTA ticket keeps the OTA's own policy, so Operations refuses to
 * cancel, replace the sessions of or reissue the QR of it, whatever the caller.
 */
public enum TicketSalesChannel {
    DIRECT,
    OTA
}
