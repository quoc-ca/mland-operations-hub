package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * T026: DTO representing the OpenAPI 'CheckInResult' schema.
 * <p>
 * OpenAPI Contract:
 * - allOf: [ '#/components/schemas/Ticket', ... ]
 * - Required: [checkInResult, checkedInAt]
 * - Properties:
 *     checkInResult: enum [CHECKED_IN, ALREADY_CHECKED_IN]
 *     checkedInAt: string (date-time)
 * <p>
 * Normal scan result only contains ticket code, session/location, ticket status,
 * checkInResult and checkedInAt. Absolutely no PII, raw QR token, internal audit id,
 * or entity leak.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResult extends TicketResponse {

    @NotNull(message = "checkInResult is required")
    private CheckInResultType checkInResult;

    @NotNull(message = "checkedInAt is required")
    private Instant checkedInAt;
}
