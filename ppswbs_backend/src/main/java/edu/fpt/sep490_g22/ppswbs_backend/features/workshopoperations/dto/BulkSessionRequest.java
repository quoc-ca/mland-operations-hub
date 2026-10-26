package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSessionRequest {

    @NotBlank(message = "branchId is required")
    private String branchId;

    @NotEmpty(message = "slotIds must contain at least one slot ID")
    private List<String> slotIds;

    @NotNull(message = "from date is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate from;

    @NotNull(message = "to date is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate to;

    @NotEmpty(message = "weekdays must contain at least one weekday")
    private List<DayOfWeek> weekdays;

    @Builder.Default
    private SessionState initialState = SessionState.DRAFT;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "from date must not be after to date")
    public boolean isDateRangeValid() {
        if (from == null || to == null) {
            return true;
        }
        return !from.isAfter(to);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "initialState must be either DRAFT or PUBLISHED")
    public boolean isInitialStateValid() {
        if (initialState == null) {
            return true;
        }
        return initialState == SessionState.DRAFT || initialState == SessionState.PUBLISHED;
    }
}
