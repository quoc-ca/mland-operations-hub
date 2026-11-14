package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** A scanned QR was revoked by a recipient change (003.2 FR-033); mapped to 409 TICKET_QR_REVOKED. */
public class WorkshopTicketQrRevokedException extends RuntimeException {
    public WorkshopTicketQrRevokedException() {
        super("This QR was revoked; use the ticket's current QR");
    }
}
