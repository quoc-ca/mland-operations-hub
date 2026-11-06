package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PaymentOwnerRegistrationRepository extends JpaRepository<PaymentOwnerRegistration, Long> {
    Optional<PaymentOwnerRegistration> findByMerchantReference(String merchantReference);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentOwnerRegistration> findLockedByMerchantReference(String merchantReference);
}
