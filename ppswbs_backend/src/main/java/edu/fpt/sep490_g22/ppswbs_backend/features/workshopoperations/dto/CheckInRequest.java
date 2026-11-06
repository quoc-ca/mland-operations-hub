package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T026: Request DTO for normal QR or exact ticket-code check-in.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/check-ins (POST)
 * - Properties: qrToken, ticketCode
 * - Validation: oneOf [ { required: [qrToken] }, { required: [ticketCode] } ]
 * <p>
 * Does not accept legacy branch or personnel fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRequest {

    private String qrToken;
    private String ticketCode;

    @JsonIgnore
    @AssertTrue(message = "Exactly one of qrToken or ticketCode must be provided")
    public boolean isOneOfProvided() {
        boolean hasQrToken = qrToken != null && !qrToken.isBlank();
        boolean hasTicketCode = ticketCode != null && !ticketCode.isBlank();
        return hasQrToken ^ hasTicketCode;
    }
}
