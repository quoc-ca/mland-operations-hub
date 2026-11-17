package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * T026: DTO representing the OpenAPI 'Ticket' schema.
 * <p>
 * OpenAPI Contract:
 * - Required: [ticketCode, sessionId, locationId, validityState, attendanceState]
 * - Properties:
 *     ticketCode: string
 *     sessionId: string (uuid)
 *     locationId: integer (int64)
 *     validityState: enum [ACTIVE, CANCELLED, REVOKED, REFUNDED]
 *     attendanceState: enum [NOT_CHECKED_IN, CHECKED_IN, NO_SHOW]
 *     checkedInAt: string (date-time, nullable)
 * <p>
 * Strict privacy boundary: Contains NO customer PII, NO booking contact info,
 * NO raw QR payloads, and NO JPA entity references.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    @NotNull(message = "ticketCode is required")
    private String ticketCode;

    @NotNull(message = "sessionId is required")
    private String sessionId;

    @NotNull(message = "locationId is required")
    private Long locationId;

    @NotNull(message = "validityState is required")
    private TicketValidityState validityState;

    @NotNull(message = "attendanceState is required")
    private AttendanceState attendanceState;

    private Instant checkedInAt;
}
