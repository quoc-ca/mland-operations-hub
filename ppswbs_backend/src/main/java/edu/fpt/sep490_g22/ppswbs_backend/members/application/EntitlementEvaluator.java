package edu.fpt.sep490_g22.ppswbs_backend.members.application;

import edu.fpt.sep490_g22.ppswbs_backend.common.PolicyAcceptanceRequiredException;
import edu.fpt.sep490_g22.ppswbs_backend.common.SuspendedAccountException;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberPolicyAcceptanceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EntitlementEvaluator {

    private final PolicyDocumentRepository policyDocumentRepository;
    private final MemberPolicyAcceptanceRepository acceptanceRepository;

    public MemberEntitlementDto evaluate(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new SuspendedAccountException("Account suspended. Please contact customer support.");
        }

        List<PolicyDocument> effectivePolicies = policyDocumentRepository.findByState(PolicyDocumentState.EFFECTIVE);
        List<MemberPolicyAcceptance> acceptances = acceptanceRepository.findAllByMemberId(member.getId());
        Set<Long> acceptedPolicyIds = acceptances.stream()
                .map(MemberPolicyAcceptance::getPolicyDocumentId)
                .collect(Collectors.toSet());

        List<String> unacceptedPolicyVersions = new ArrayList<>();
        for (PolicyDocument pd : effectivePolicies) {
            if (!acceptedPolicyIds.contains(pd.getId())) {
                unacceptedPolicyVersions.add(pd.getPolicyType() + ":" + pd.getVersion());
            }
        }

        boolean entitlementActive = unacceptedPolicyVersions.isEmpty() && member.getStatus() == MemberStatus.ACTIVE;

        return MemberEntitlementDto.builder()
                .externalUserId(member.getExternalUserId())
                .email(member.getEmail())
                .emailVerified(member.isEmailVerified())
                .status(member.getStatus())
                .entitlementActive(entitlementActive)
                .requiredPolicyVersions(unacceptedPolicyVersions)
                .build();
    }

    public void verifyActiveEntitlement(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new SuspendedAccountException("Account suspended. Please contact customer support.");
        }

        List<PolicyDocument> effectivePolicies = policyDocumentRepository.findByState(PolicyDocumentState.EFFECTIVE);
        List<MemberPolicyAcceptance> acceptances = acceptanceRepository.findAllByMemberId(member.getId());
        Set<Long> acceptedPolicyIds = acceptances.stream()
                .map(MemberPolicyAcceptance::getPolicyDocumentId)
                .collect(Collectors.toSet());

        for (PolicyDocument pd : effectivePolicies) {
            if (!acceptedPolicyIds.contains(pd.getId())) {
                throw new PolicyAcceptanceRequiredException("Current Terms of Use and Privacy Policy acceptance required.");
            }
        }
    }
}
