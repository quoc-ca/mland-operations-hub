package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntryResponse {
    private AccountRole code;
    private String displayName;
}
