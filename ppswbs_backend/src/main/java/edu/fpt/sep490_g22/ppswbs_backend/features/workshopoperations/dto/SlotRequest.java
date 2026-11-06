package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotRequest {

    public static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @NotBlank(message = "Slot label is required")
    private String label;

    @NotNull(message = "startsAtLocal is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm[:ss]")
    private LocalTime startsAtLocal;

    @NotNull(message = "endsAtLocal is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm[:ss]")
    private LocalTime endsAtLocal;

    @NotNull(message = "defaultCapacity is required")
    @Min(value = 1, message = "defaultCapacity must be at least 1")
    private Integer defaultCapacity;

    @NotNull(message = "active flag is required")
    private Boolean active;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "startsAtLocal must be strictly before endsAtLocal on the same calendar day in Asia/Ho_Chi_Minh")
    public boolean isSameDayTimingValid() {
        if (startsAtLocal == null || endsAtLocal == null) {
            return true;
        }
        return startsAtLocal.isBefore(endsAtLocal);
    }
}
