package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupedCheckoutRepository extends JpaRepository<GroupedCheckout, String> {
    @EntityGraph(attributePaths = "orders")
    Optional<GroupedCheckout> findByMemberAccountIdAndIdempotencyKey(Long memberAccountId, String idempotencyKey);
    @EntityGraph(attributePaths = "orders")
    Optional<GroupedCheckout> findByIdAndMemberAccountId(String id, Long memberAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select checkout from GroupedCheckout checkout where checkout.id = :id and checkout.memberAccountId = :memberId")
    Optional<GroupedCheckout> lockOwned(@Param("id") String id, @Param("memberId") Long memberId);
}
