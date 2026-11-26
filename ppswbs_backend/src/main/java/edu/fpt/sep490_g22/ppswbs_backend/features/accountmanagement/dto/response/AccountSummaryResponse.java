package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.RevocationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryResponse {

    private Long accountId;
    private AccountRole role;
    private AccountStatus status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String loginEmail;
    private String fullName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RevocationStatus revocationStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
