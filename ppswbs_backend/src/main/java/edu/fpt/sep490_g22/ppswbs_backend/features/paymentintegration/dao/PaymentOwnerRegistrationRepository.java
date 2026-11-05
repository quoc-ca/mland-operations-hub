package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentOwnerRegistrationRepository extends JpaRepository<PaymentOwnerRegistration, Long> {
    Optional<PaymentOwnerRegistration> findByMerchantReference(String merchantReference);
}
