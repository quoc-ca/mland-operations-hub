package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.CheckoutHold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CheckoutHoldRepository extends JpaRepository<CheckoutHold, String> {
    Optional<CheckoutHold> findByGroupedCheckoutId(String checkoutId);
}
