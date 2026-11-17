package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSuspendRequest {

    @NotBlank(message = "error.validation.reason_required")
    @Size(min = 1, max = 500, message = "error.validation.reason_length")
    private String reason;
}
