package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCommandRequest {

    @NotNull
    private Long version;

    @NotNull
    private String operation; // SUSPEND, UNSUSPEND

    @Size(min = 1, max = 500)
    private String reason;
}
