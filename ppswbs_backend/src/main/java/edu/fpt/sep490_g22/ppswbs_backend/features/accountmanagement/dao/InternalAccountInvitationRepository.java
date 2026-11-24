package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternalAccountInvitationRepository extends JpaRepository<InternalAccountInvitation, Long> {

    Optional<InternalAccountInvitation> findTopByAccountIdOrderByInvitationVersionDesc(Long accountId);

    Optional<InternalAccountInvitation> findByAccountIdAndInvitationVersion(Long accountId, int invitationVersion);

    Optional<InternalAccountInvitation> findBySecretDigest(String secretDigest);

    List<InternalAccountInvitation> findByAccountIdOrderByInvitationVersionDesc(Long accountId);
}
