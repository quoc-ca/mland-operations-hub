package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.UUID;

/**
 * Revokes the active QR of one ticket and issues a new opaque QR for the same entitlements (003.2 FR-033).
 * Idempotent per {@code operationReference}; refused once any entitlement of the ticket is checked in.
 */
public record ReissueTicketQrCommand(
        UUID ticketReference,
        QrRevokeReason reason,
        String operationReference,
        String correlationReference
) {
}
