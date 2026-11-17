package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingCatalogueModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RingCatalogueModelRepository extends JpaRepository<RingCatalogueModel, Long> {

    Optional<RingCatalogueModel> findByModelCode(String modelCode);

    List<RingCatalogueModel> findAllByPackageReferenceAndIsActiveTrue(String packageReference);
}

