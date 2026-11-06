package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.VnpayMerchantOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VnpayMerchantOperationRepository extends JpaRepository<VnpayMerchantOperation, Long> {
    Optional<VnpayMerchantOperation> findByOperationReference(String operationReference);
}
