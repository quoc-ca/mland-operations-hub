package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomOrderRepository extends JpaRepository<CustomOrder, String> {
    Optional<CustomOrder> findByCustomOrderReference(String customOrderReference);
    Optional<CustomOrder> findByDesignRequestId(String designRequestId);
    List<CustomOrder> findByMemberAccountIdOrderByCreatedAtDesc(Long memberAccountId);
    List<CustomOrder> findAllByOrderByCreatedAtDesc();
    List<CustomOrder> findByStatusOrderByCreatedAtDesc(String status);
    List<CustomOrder> findByStatusInOrderByCreatedAtDesc(java.util.Collection<String> statuses);
}
