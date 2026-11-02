package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
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
public class InternalAccountSummaryResponse {

    private Long accountId;
    private AccountRole role;
    private AccountStatus status;
    private String loginEmail;
    private RevocationStatus revocationStatus;
    private Instant createdAt;
    private Instant updatedAt;

    private InternalProvisioningStatus provisioningStatus;
    private Instant invitationLastSentAt;
}
