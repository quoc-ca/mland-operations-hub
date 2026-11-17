package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDetailResponse {
    private URI type;
    private String title;
    private int status;
    private String code;
    private String detail;

    /** Only with STAFF_NOT_ELIGIBLE (and the T139 resend INVALID_STATE reasons); omitted otherwise. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String reason;
    private String correlationId;

    /** Only with CHECK_IN_NOT_OPEN (T126); omitted otherwise. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.time.Instant checkInOpensAt;
}
