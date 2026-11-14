package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.MemberAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    long countByAccountId(Long accountId);

    List<MemberAddress> findAllByAccountIdOrderByIsDefaultDescUpdatedAtDesc(Long accountId);

    Optional<MemberAddress> findByAccountIdAndId(Long accountId, Long id);

    Optional<MemberAddress> findByAccountIdAndIsDefaultTrue(Long accountId);
}

