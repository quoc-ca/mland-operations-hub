package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RingMaterialRepository extends JpaRepository<RingMaterial, Long> {

    Optional<RingMaterial> findByMaterialCode(String materialCode);

    List<RingMaterial> findAllByIsActiveTrue();
}

