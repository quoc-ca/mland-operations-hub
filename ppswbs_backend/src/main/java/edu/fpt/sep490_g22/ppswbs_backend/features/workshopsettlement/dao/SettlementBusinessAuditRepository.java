package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SettlementBusinessAuditRepository extends JpaRepository<SettlementBusinessAudit, Long> {
    boolean existsByCorrelationReference(String correlationReference);

    /**
     * 003.2 FR-049/FR-050/FR-052 (T171): one keyset page for the Manager audit review, newest id first. Every filter
     * but {@code from} is optional; {@code beforeId} is the cursor (rows with a smaller id only), so rows written
     * while the Manager pages neither repeat nor go missing. {@code actorReference} is matched exactly
     * ({@code account:<id>}). Callers pass {@code PageRequest.of(0, limit + 1)}.
     */
    @Query("""
            select a from SettlementBusinessAudit a
            where (:actionCode is null or a.actionCode = :actionCode)
              and (:actorReference is null or a.actorReference = :actorReference)
              and (:aggregateType is null or a.aggregateType = :aggregateType)
              and (:aggregateReference is null or a.aggregateReference = :aggregateReference)
              and a.createdAt >= :from
              and (:to is null or a.createdAt <= :to)
              and (:beforeId is null or a.id < :beforeId)
            order by a.id desc
            """)
    List<SettlementBusinessAudit> findPage(@Param("actionCode") String actionCode,
                                           @Param("actorReference") String actorReference,
                                           @Param("aggregateType") String aggregateType,
                                           @Param("aggregateReference") String aggregateReference,
                                           @Param("from") Instant from, @Param("to") Instant to,
                                           @Param("beforeId") Long beforeId, Pageable pageable);

    /**
     * navigation shell and dashboard (014 R-5): the same page as {@link #findPage}, but only denied attempts, i.e. rows whose action code
     * starts with {@code DENIED_} (the way 003.3 records a refused caller). The existing query is left unchanged.
     */
    @Query("""
            select a from SettlementBusinessAudit a
            where a.actionCode like 'DENIED!_%' escape '!'
              and (:actionCode is null or a.actionCode = :actionCode)
              and (:actorReference is null or a.actorReference = :actorReference)
              and (:aggregateType is null or a.aggregateType = :aggregateType)
              and (:aggregateReference is null or a.aggregateReference = :aggregateReference)
              and a.createdAt >= :from
              and (:to is null or a.createdAt <= :to)
              and (:beforeId is null or a.id < :beforeId)
            order by a.id desc
            """)
    List<SettlementBusinessAudit> findDeniedPage(@Param("actionCode") String actionCode,
                                                 @Param("actorReference") String actorReference,
                                                 @Param("aggregateType") String aggregateType,
                                                 @Param("aggregateReference") String aggregateReference,
                                                 @Param("from") Instant from, @Param("to") Instant to,
                                                 @Param("beforeId") Long beforeId, Pageable pageable);
}
