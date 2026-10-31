package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.InternalAccountProvisioning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InternalAccountProvisioningRepository extends JpaRepository<InternalAccountProvisioning, Long> {

    Optional<InternalAccountProvisioning> findByIdempotencyKey(String idempotencyKey);

    Optional<InternalAccountProvisioning> findByAccountId(Long accountId);
}
