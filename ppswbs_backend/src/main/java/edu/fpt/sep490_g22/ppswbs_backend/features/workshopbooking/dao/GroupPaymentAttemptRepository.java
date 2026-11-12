package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupPaymentAttemptRepository extends JpaRepository<GroupPaymentAttempt, Long> {

    List<GroupPaymentAttempt> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Optional<GroupPaymentAttempt> findByMerchantReference(String merchantReference);
}
