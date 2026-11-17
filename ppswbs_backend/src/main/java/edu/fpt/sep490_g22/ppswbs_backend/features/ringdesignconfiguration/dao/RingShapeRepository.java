package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingShape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RingShapeRepository extends JpaRepository<RingShape, Long> {

    Optional<RingShape> findByShapeCode(String shapeCode);

    List<RingShape> findAllByIsActiveTrue();
}

