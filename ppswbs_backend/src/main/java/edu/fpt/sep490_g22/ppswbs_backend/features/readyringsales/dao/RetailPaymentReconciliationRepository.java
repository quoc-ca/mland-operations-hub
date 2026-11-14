package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailPaymentReconciliation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailPaymentReconciliationRepository extends JpaRepository<RetailPaymentReconciliation, Long> {
    Optional<RetailPaymentReconciliation> findByMerchantReference(String merchantReference);
}
