package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountProfileRepository extends JpaRepository<AccountProfile, Long> {

    Optional<AccountProfile> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AccountProfile p WHERE p.accountId = :accountId")
    Optional<AccountProfile> findForUpdateByAccountId(@Param("accountId") Long accountId);
}
