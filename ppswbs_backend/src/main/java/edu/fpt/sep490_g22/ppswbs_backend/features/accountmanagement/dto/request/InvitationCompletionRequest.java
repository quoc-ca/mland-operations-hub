package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCompletionRequest {
    @NotBlank
    private String setupSession;

    @NotBlank
    private String method; // PASSWORD, GOOGLE
}
