package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountLocationAssignmentRepository extends JpaRepository<AccountLocationAssignment, Long> {

    List<AccountLocationAssignment> findByAccountIdAndActiveTrue(Long accountId);

    List<AccountLocationAssignment> findByAccountId(Long accountId);

    Optional<AccountLocationAssignment> findByAccountIdAndLocationIdAndActiveTrue(Long accountId, Long locationId);

    List<AccountLocationAssignment> findByLocationIdAndActiveTrue(Long locationId);
}
