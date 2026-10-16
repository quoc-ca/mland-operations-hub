package edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberPolicyAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberPolicyAcceptanceRepository extends JpaRepository<MemberPolicyAcceptance, Long> {
    Optional<MemberPolicyAcceptance> findByMemberIdAndPolicyDocumentId(Long memberId, Long policyDocumentId);
    List<MemberPolicyAcceptance> findAllByMemberId(Long memberId);
}
