package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * T026: Privacy-safe DTO representing an issued attendance ticket with opaque QR reference.
 * <p>
 * Exposes ticket code, opaque QR lookup reference, entitled session, canonical location,
 * validity state, and initial attendance state.
 * Does NOT expose raw QR cryptographic token, customer PII, booking contact, or internal entity keys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketIssueResponse {

    @NotNull(message = "ticketCode is required")
    private String ticketCode;

    @NotNull(message = "qrReference is required")
    private String qrReference;

    @NotNull(message = "sessionId is required")
    private String sessionId;

    @NotNull(message = "locationId is required")
    private Long locationId;

    @NotNull(message = "validityState is required")
    private TicketValidityState validityState;

    @NotNull(message = "attendanceState is required")
    private AttendanceState attendanceState;
}
