package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShippingQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RetailShippingQuoteRepository extends JpaRepository<RetailShippingQuote, String> {
    Optional<RetailShippingQuote> findByIdAndMemberAccountId(String id, Long memberAccountId);
}
