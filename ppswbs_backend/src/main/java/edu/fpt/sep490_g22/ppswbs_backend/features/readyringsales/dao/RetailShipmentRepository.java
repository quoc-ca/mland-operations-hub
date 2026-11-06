package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RetailShipmentRepository extends JpaRepository<RetailShipment, String> {

    Optional<RetailShipment> findByBranchOrder_Id(String branchOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RetailShipment s where s.id = :id")
    Optional<RetailShipment> lockById(String id);

    List<RetailShipment> findAllByStatusOrderByCreatedAtAsc(
        edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus status);

    List<RetailShipment> findAllByOrderByCreatedAtDesc();

    /** Staff/Manager list, bounded to the actor's assigned branch locations and an optional status filter. */
    @Query("select s from RetailShipment s where s.locationId in :locationIds "
        + "and (:status is null or s.status = :status)")
    Page<RetailShipment> findStaffShipments(Collection<Long> locationIds, RetailShipmentStatus status, Pageable pageable);

    /** Staff/Manager detail, bounded to the actor's assigned branch locations. */
    Optional<RetailShipment> findByIdAndLocationIdIn(String id, Collection<Long> locationIds);

    boolean existsByMockTrackingNumber(String mockTrackingNumber);
}
