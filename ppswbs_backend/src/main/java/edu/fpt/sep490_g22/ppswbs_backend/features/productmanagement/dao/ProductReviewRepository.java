package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByProductIdOrderByRevisionAsc(Long productId);

    List<ProductReview> findByProductIdInOrderByProductIdAscRevisionAsc(Collection<Long> productIds);

    Optional<ProductReview> findFirstByProductIdOrderByRevisionDesc(Long productId);

    Optional<ProductReview> findByProductIdAndRevision(Long productId, long revision);
}
