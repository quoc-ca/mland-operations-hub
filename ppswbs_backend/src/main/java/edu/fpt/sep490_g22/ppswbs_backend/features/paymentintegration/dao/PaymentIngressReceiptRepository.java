package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentIngressReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentIngressReceiptRepository extends JpaRepository<PaymentIngressReceipt, Long> {
    boolean existsByDeliveryReference(String deliveryReference);
}
