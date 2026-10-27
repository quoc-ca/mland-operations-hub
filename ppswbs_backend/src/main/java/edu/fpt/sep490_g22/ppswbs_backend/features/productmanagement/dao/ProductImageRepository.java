package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayPositionAsc(Long productId);

    List<ProductImage> findByProductIdInOrderByProductIdAscDisplayPositionAsc(Collection<Long> productIds);

    long countByProductId(Long productId);

    boolean existsByMediaObjectId(UUID mediaObjectId);

    /** Products that currently show the image (public media serving, T070). */
    @Query("select distinct i.productId from ProductImage i where i.mediaObjectId = :mediaId")
    List<Long> findProductIdsByMediaObjectId(@Param("mediaId") UUID mediaId);
}
