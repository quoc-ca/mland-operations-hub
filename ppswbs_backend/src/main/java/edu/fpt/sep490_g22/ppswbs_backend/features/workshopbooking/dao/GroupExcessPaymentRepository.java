package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupExcessPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupExcessPaymentRepository extends JpaRepository<GroupExcessPayment, Long> {

    Optional<GroupExcessPayment> findByVerifiedEvidenceReference(String verifiedEvidenceReference);

    List<GroupExcessPayment> findByGroupIdOrderByReceivedAtAsc(Long groupId);
}
