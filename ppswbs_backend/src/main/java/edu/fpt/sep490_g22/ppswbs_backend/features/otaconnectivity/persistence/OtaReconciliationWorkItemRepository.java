package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface OtaReconciliationWorkItemRepository extends JpaRepository<OtaReconciliationWorkItem, Long> {
    java.util.Optional<OtaReconciliationWorkItem> findByPublicReference(String publicReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from OtaReconciliationWorkItem item join fetch item.mapping mapping "
            + "join fetch mapping.connection where item.publicReference = :publicReference")
    java.util.Optional<OtaReconciliationWorkItem> findMappingLinkedByPublicReferenceForUpdate(
            @Param("publicReference") String publicReference);
    List<OtaReconciliationWorkItem> findByLocationReferenceAndStatusOrderByCreatedAtAsc(String locationReference, String status);

    List<OtaReconciliationWorkItem> findByAssignedManagerReferenceAndStatusOrderByCreatedAtAsc(String managerReference, String status);

    boolean existsByMapping_IdAndCategoryAndStatus(Long mappingId, String category, String status);

    List<OtaReconciliationWorkItem> findByStatusOrderByCreatedAtAsc(String status);

    @Query("select item from OtaReconciliationWorkItem item join fetch item.mapping mapping "
            + "join fetch mapping.connection where item.mapping is not null order by item.createdAt asc")
    List<OtaReconciliationWorkItem> findMappingLinkedOrderByCreatedAtAsc();

    @Query("select item from OtaReconciliationWorkItem item join fetch item.mapping mapping "
            + "join fetch mapping.connection where item.mapping is not null and item.status = :status "
            + "order by item.createdAt asc")
    List<OtaReconciliationWorkItem> findMappingLinkedByStatusOrderByCreatedAtAsc(@Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from OtaReconciliationWorkItem item left join fetch item.mapping mapping "
            + "left join fetch mapping.connection where item.id = :id")
    java.util.Optional<OtaReconciliationWorkItem> findByIdForUpdate(@Param("id") Long id);

    java.util.Optional<OtaReconciliationWorkItem> findFirstByMapping_IdAndStatusOrderByCreatedAtAsc(
            Long mappingId, String status);
}
