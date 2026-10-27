package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.PriceSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceSuggestionRepository extends JpaRepository<PriceSuggestion, Long> {

    List<PriceSuggestion> findByProposalIdOrderByCreatedAtDesc(String proposalId);

    Optional<PriceSuggestion> findFirstByProposalIdOrderByCreatedAtDesc(String proposalId);
}
