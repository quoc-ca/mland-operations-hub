package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleLocationAssignRequest {

    @NotNull(message = "error.validation.version_required")
    private Long version;

    @NotNull(message = "error.validation.role_required")
    private AccountRole role;

    @NotNull(message = "error.validation.locations_required")
    private List<Long> locationIds;
}
