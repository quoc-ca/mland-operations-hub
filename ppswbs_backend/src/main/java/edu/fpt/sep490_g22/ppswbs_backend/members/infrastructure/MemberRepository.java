package edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByExternalUserId(String externalUserId);
    Optional<Member> findByEmail(String email);
}
