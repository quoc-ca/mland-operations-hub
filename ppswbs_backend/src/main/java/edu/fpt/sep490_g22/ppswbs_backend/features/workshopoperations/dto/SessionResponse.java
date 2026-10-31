package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private String id;
    private Long locationId;
    private String slotId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate workshopDate;

    private Integer capacity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant checkInOpensAt;

    private SessionState state;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant scheduledStartAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant scheduledEndAt;

    private CheckInOpeningSource checkInOpeningSource;
    private Long operationalLeadAccountId;
}
