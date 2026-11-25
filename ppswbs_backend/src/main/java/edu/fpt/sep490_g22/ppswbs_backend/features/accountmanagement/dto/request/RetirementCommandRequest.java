package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementCommandRequest {
    @NotNull
    private Long accountVersion;

    @NotBlank
    @Email
    private String normalizedEmail;

    @NotBlank
    private String retailSourceVersion;

    @NotBlank
    private String workshopSourceVersion;

    @NotNull
    private Boolean confirm;
}
