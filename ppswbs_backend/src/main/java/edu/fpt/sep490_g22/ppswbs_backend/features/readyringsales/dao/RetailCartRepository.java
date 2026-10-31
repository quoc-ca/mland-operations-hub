package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface RetailCartRepository extends JpaRepository<RetailCart, String> {

    @EntityGraph(attributePaths = "lines")
    Optional<RetailCart> findByMemberAccountId(Long memberAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("select cart from RetailCart cart where cart.memberAccountId = :memberAccountId")
    Optional<RetailCart> findLockedByMemberAccountId(Long memberAccountId);

}
