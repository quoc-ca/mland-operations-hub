package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderShipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomOrderShipmentRepository extends JpaRepository<CustomOrderShipment, Long> {

    Optional<CustomOrderShipment> findByCustomOrderId(String customOrderId);

    Optional<CustomOrderShipment> findByCustomOrderReference(String customOrderReference);

    Optional<CustomOrderShipment> findByTrackingNumber(String trackingNumber);

    Optional<CustomOrderShipment> findTopByCustomOrderReferenceOrderByCreatedAtDesc(String customOrderReference);

    Optional<CustomOrderShipment> findTopByCustomOrderIdOrderByCreatedAtDesc(String customOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CustomOrderShipment s WHERE s.id = :id")
    Optional<CustomOrderShipment> lockById(@Param("id") Long id);

    Page<CustomOrderShipment> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<CustomOrderShipment> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<CustomOrderShipment> findAllByOrderByCreatedAtDesc();
}
