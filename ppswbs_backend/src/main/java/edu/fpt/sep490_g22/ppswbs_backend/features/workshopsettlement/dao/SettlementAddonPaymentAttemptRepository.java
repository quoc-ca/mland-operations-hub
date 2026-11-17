package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonPaymentAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SettlementAddonPaymentAttemptRepository extends JpaRepository<SettlementAddonPaymentAttempt, Long> {
    Optional<SettlementAddonPaymentAttempt> findByAttemptReference(String attemptReference);

    Optional<SettlementAddonPaymentAttempt> findByMerchantReference(String merchantReference);

    Optional<SettlementAddonPaymentAttempt> findByOperationReference(String operationReference);

    Optional<SettlementAddonPaymentAttempt> findFirstByBillReferenceOrderByCreatedAtDescIdDesc(String billReference);

    List<SettlementAddonPaymentAttempt> findAllByBillReferenceOrderByCreatedAtDescIdDesc(String billReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SettlementAddonPaymentAttempt a where a.attemptReference = :ref")
    Optional<SettlementAddonPaymentAttempt> findByAttemptReferenceForUpdate(@Param("ref") String ref);
}

