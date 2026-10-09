package edu.fpt.sep490_g22.ppswbs_backend.members.application;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAcceptanceRequestDto {
    @NotBlank(message = "termsVersion is required")
    private String termsVersion;

    @NotBlank(message = "privacyVersion is required")
    private String privacyVersion;
}
