package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.PaymentEventReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentEventReceiptRepository extends JpaRepository<PaymentEventReceipt, Long> {

    Optional<PaymentEventReceipt> findByProviderEventId(String providerEventId);

    boolean existsByProviderEventId(String providerEventId);
}
