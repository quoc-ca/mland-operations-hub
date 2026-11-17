package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingGemstone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RingGemstoneRepository extends JpaRepository<RingGemstone, Long> {

    Optional<RingGemstone> findByGemstoneCode(String gemstoneCode);

    List<RingGemstone> findAllByIsActiveTrue();
}

