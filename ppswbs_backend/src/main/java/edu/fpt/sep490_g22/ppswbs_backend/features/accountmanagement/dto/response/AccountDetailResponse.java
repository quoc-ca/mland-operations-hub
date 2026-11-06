package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.RevocationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDetailResponse {

    private Long accountId;
    private AccountRole role;
    private AccountStatus status;
    private String loginEmail;
    private RevocationStatus revocationStatus;
    private Instant createdAt;
    private Instant updatedAt;

    private AccountProfileResponse profile;
    private List<LocationAssignmentResponse> locationAssignments;
    private Long version;
}
