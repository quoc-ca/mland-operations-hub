package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesign;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RingDesignRepository extends JpaRepository<RingDesign, Long> {

    Optional<RingDesign> findByDesignReferenceAndDesignVersion(String designReference, Integer designVersion);

    @Query("SELECT r FROM RingDesign r WHERE r.designReference = :designReference ORDER BY r.designVersion DESC LIMIT 1")
    Optional<RingDesign> findLatestByDesignReference(@Param("designReference") String designReference);

    @Query("SELECT r FROM RingDesign r WHERE r.ownerType = :ownerType AND r.ownerReference = :ownerReference ORDER BY r.createdAt DESC")
    List<RingDesign> findAllByOwner(
            @Param("ownerType") RingDesignOwnerType ownerType,
            @Param("ownerReference") String ownerReference
    );

    @Query("SELECT r FROM RingDesign r WHERE r.designReference = :designReference ORDER BY r.designVersion ASC")
    List<RingDesign> findAllVersions(@Param("designReference") String designReference);
}

