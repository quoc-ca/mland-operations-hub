package edu.fpt.sep490_g22.ppswbs_backend.members.application;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberEntitlementDto {
    private String externalUserId;
    private String email;
    private boolean emailVerified;
    private MemberStatus status;
    private boolean entitlementActive;
    private List<String> requiredPolicyVersions;
}
