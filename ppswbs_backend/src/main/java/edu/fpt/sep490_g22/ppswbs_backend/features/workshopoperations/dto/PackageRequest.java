package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageRequest {

    @NotNull(message = "name is required")
    private String name;

    @NotNull(message = "description is required")
    private String description;

    @NotNull(message = "publicPrice is required")
    @Min(value = 0, message = "publicPrice must be nonnegative whole VND")
    private Long publicPrice;

    @NotNull(message = "minParticipants is required")
    @Min(value = 1, message = "minParticipants must be at least 1")
    private Integer minParticipants;

    @NotNull(message = "maxParticipants is required")
    @Min(value = 1, message = "maxParticipants must be at least 1")
    private Integer maxParticipants;

    @NotNull(message = "requiredSessionCount is required")
    @Min(value = 1, message = "requiredSessionCount must be at least 1")
    private Integer requiredSessionCount;

    @NotNull(message = "sameLocationRequired is required")
    private Boolean sameLocationRequired;

    @NotNull(message = "minimumSameDaySlotGap is required")
    @Min(value = 0, message = "minimumSameDaySlotGap must be nonnegative")
    private Integer minimumSameDaySlotGap;

    @JsonIgnore
    @AssertTrue(message = "maxParticipants must be greater than or equal to minParticipants")
    public boolean isParticipantRangeValid() {
        return minParticipants == null || maxParticipants == null || maxParticipants >= minParticipants;
    }
}
