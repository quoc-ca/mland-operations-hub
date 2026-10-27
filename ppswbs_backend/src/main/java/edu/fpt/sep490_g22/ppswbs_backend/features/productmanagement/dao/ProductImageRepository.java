package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayPositionAsc(Long productId);

    List<ProductImage> findByProductIdInOrderByProductIdAscDisplayPositionAsc(Collection<Long> productIds);

    long countByProductId(Long productId);

    boolean existsByMediaObjectId(UUID mediaObjectId);
}
