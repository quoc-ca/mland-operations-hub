package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalAccountCreateRequest {

    @NotNull(message = "Role is required")
    private AccountRole role;

    private String email;

    @NotBlank(message = "Full name is required")
    @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
    private String fullName;

    @Size(min = 7, max = 30, message = "error.validation.phone_length")
    private String phone;

    @NotEmpty(message = "error.validation.locations_required")
    private List<Long> locationIds;
}
