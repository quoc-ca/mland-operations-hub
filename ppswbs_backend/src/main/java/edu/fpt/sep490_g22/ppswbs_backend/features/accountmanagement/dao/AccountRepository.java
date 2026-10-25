package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByFirebaseUid(String firebaseUid);

    boolean existsByFirebaseUid(String firebaseUid);

    @Query("""
        SELECT a FROM Account a
        LEFT JOIN AccountProfile p ON p.accountId = a.id
        WHERE (:role IS NULL OR a.role = :role)
          AND (:status IS NULL OR a.status = :status)
          AND (:q IS NULL OR LOWER(a.loginEmail) LIKE LOWER(CONCAT(:q, '%')) OR LOWER(p.fullName) LIKE LOWER(CONCAT(:q, '%')))
    """)
    Page<Account> searchAccounts(
        @Param("q") String q,
        @Param("role") AccountRole role,
        @Param("status") AccountStatus status,
        Pageable pageable
    );
}
