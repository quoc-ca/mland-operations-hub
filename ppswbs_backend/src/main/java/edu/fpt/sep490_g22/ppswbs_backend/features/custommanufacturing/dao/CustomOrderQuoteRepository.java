package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomOrderQuoteRepository extends JpaRepository<CustomOrderQuote, String> {
    Optional<CustomOrderQuote> findByQuoteReference(String quoteReference);
    List<CustomOrderQuote> findByCustomOrderIdOrderByQuoteGenerationDesc(String customOrderId);
    Optional<CustomOrderQuote> findTopByCustomOrderIdOrderByQuoteGenerationDesc(String customOrderId);
    long countByCustomOrderId(String customOrderId);
}
