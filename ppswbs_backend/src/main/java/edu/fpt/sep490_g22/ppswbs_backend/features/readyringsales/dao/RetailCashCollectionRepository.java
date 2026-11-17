package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCashCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RetailCashCollectionRepository extends JpaRepository<RetailCashCollection, String> {
    Optional<RetailCashCollection> findByRetailOrderId(String retailOrderId);
    Optional<RetailCashCollection> findByConfirmedByAccountIdAndIdempotencyKey(Long confirmedByAccountId, String idempotencyKey);
    List<RetailCashCollection> findByLocationIdAndBusinessDateOrderByConfirmedAtAsc(Long locationId, LocalDate businessDate);
}
