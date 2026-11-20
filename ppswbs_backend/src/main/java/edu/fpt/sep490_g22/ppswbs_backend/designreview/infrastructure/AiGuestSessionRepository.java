package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiGuestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiGuestSessionRepository extends JpaRepository<AiGuestSession, Long> {
    Optional<AiGuestSession> findBySessionDigest(String sessionDigest);
}
