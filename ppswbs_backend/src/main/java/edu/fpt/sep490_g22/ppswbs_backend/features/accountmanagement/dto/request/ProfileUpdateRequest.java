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
public class ProfileUpdateRequest {

    @NotBlank(message = "error.validation.full_name_required")
    @Size(min = 1, max = 400, message = "error.validation.full_name_length")
    private String fullName;

    @Size(min = 7, max = 30, message = "error.validation.phone_length")
    private String phone;
}
