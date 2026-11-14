package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class SessionRequest {

    @NotNull(message = "locationId is required")
    private Long locationId;

    @NotBlank(message = "slotId is required")
    private String slotId;

    @NotNull(message = "workshopDate is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate workshopDate;

    @NotNull(message = "capacity is required")
    @Min(value = 1, message = "capacity must be at least 1")
    private Integer capacity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant checkInOpensAt;

    /** FR-001i: DRAFT (default) or PUBLISHED, validated exactly as in bulk generation. */
    @Builder.Default
    private SessionState initialState = SessionState.DRAFT;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "initialState must be either DRAFT or PUBLISHED")
    public boolean isInitialStateValid() {
        return initialState == null || initialState == SessionState.DRAFT || initialState == SessionState.PUBLISHED;
    }
}
