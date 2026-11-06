package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String>, JpaSpecificationExecutor<Review> {

    Optional<Review> findBySourceTypeAndSourceReference(SourceType sourceType, String sourceReference);

    boolean existsBySourceTypeAndSourceReference(SourceType sourceType, String sourceReference);

    List<Review> findBySourceTypeAndSourceReferenceIn(SourceType sourceType, Collection<String> sourceReferences);

    Page<Review> findByTargetTypeAndTargetReferenceAndStatus(
            TargetType targetType,
            String targetReference,
            ReviewStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(r) as totalCount, AVG(r.rating) as averageRating " +
            "FROM Review r " +
            "WHERE r.targetType = :targetType " +
            "AND r.targetReference = :targetReference " +
            "AND r.status = :status")
    ReviewAggregateProjection getAggregateByTargetAndStatus(
            @Param("targetType") TargetType targetType,
            @Param("targetReference") String targetReference,
            @Param("status") ReviewStatus status
    );

    /** Count and average of the reviews in a status; each target or package filter is optional (null means any). */
    @Query("SELECT COUNT(r) as totalCount, AVG(r.rating) as averageRating " +
            "FROM Review r " +
            "WHERE r.status = :status " +
            "AND (:targetType IS NULL OR r.targetType = :targetType) " +
            "AND (:targetReference IS NULL OR r.targetReference = :targetReference) " +
            "AND (:packageId IS NULL OR r.packageId = :packageId)")
    ReviewAggregateProjection getAggregateByOptionalTarget(
            @Param("targetType") TargetType targetType,
            @Param("targetReference") String targetReference,
            @Param("packageId") Long packageId,
            @Param("status") ReviewStatus status
    );
}

