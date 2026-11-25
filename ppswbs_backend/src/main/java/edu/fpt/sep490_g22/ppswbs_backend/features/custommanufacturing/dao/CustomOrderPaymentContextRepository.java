package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderPaymentContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomOrderPaymentContextRepository extends JpaRepository<CustomOrderPaymentContext, Long> {
    Optional<CustomOrderPaymentContext> findByMerchantReference(String merchantReference);
    Optional<CustomOrderPaymentContext> findByPaymentAttemptReference(String paymentAttemptReference);
    Optional<CustomOrderPaymentContext> findTopByCustomOrderIdOrderByCreatedAtDesc(String customOrderId);
}
