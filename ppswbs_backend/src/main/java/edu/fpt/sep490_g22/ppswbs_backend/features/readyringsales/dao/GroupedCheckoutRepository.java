package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupedCheckoutRepository extends JpaRepository<GroupedCheckout, String> {
    @EntityGraph(attributePaths = "orders")
    Optional<GroupedCheckout> findByMemberAccountIdAndIdempotencyKey(Long memberAccountId, String idempotencyKey);
    @EntityGraph(attributePaths = "orders")
    Optional<GroupedCheckout> findByIdAndMemberAccountId(String id, Long memberAccountId);
}
