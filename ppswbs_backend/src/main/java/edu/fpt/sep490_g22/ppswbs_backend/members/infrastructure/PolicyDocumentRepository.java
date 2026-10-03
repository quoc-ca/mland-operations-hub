package edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.PolicyDocument;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.PolicyDocumentState;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, Long> {
    List<PolicyDocument> findByState(PolicyDocumentState state);
    Optional<PolicyDocument> findByPolicyTypeAndVersion(PolicyType policyType, String version);
}
