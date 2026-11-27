package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementFolioCollectionRepository extends JpaRepository<SettlementFolioCollection, Long> {

    Optional<SettlementFolioCollection> findByCollectionReference(String collectionReference);

    Optional<SettlementFolioCollection> findByMethodAndExternalReference(FolioCollectionMethod method,
                                                                         String externalReference);

    List<SettlementFolioCollection> findByBillReferenceOrderByIdAsc(String billReference);

    List<SettlementFolioCollection> findByBookingReferenceOrderByIdAsc(String bookingReference);

    /** Pending parts of one method, oldest first, for the gateway reconciliation sweep (T109). */
    List<SettlementFolioCollection> findTop100ByMethodAndStatusOrderByIdAsc(FolioCollectionMethod method,
                                                                          FolioCollectionStatus status);

    List<SettlementFolioCollection> findByBillReferenceAndStatusOrderByIdAsc(String billReference,
                                                                            FolioCollectionStatus status);

    /** T133: collections of one method set without a branch, for the one-off back-fill. */
    List<SettlementFolioCollection> findByBranchIdIsNullAndMethodIn(java.util.Collection<FolioCollectionMethod> methods);

    /**
     * T133 (FR-056): collections of one branch and day for the reconciliation list. A confirmed collection belongs to
     * the day of its confirmation, a pending or voided one to the day of its creation.
     */
    @Query("""
            select c from SettlementFolioCollection c
            where c.branchId = :branchId and c.method in :methods
              and ((c.status = :confirmed and c.confirmedAt >= :from and c.confirmedAt < :to)
                or (c.status in :unconfirmed and c.createdAt >= :from and c.createdAt < :to))
            order by c.createdAt asc, c.id asc""")
    List<SettlementFolioCollection> findForReconciliation(@Param("branchId") Long branchId,
                                                         @Param("methods") java.util.Collection<FolioCollectionMethod> methods,
                                                         @Param("confirmed") FolioCollectionStatus confirmed,
                                                         @Param("unconfirmed") java.util.Collection<FolioCollectionStatus> unconfirmed,
                                                         @Param("from") java.time.Instant from,
                                                         @Param("to") java.time.Instant to);

    /** FR-036b: the rows {@link #sumConfirmedCash} adds up, for the cash-close screen. */
    @Query("""
            select c from SettlementFolioCollection c
            where c.method = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod.CASH
              and c.status = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus.CONFIRMED
              and c.branchId = :branchId and c.cashBusinessDay = :businessDay""")
    List<SettlementFolioCollection> findConfirmedCash(@Param("branchId") Long branchId,
                                                     @Param("businessDay") LocalDate businessDay);

    /** Confirmed cash of one branch and working day, net of change returned (FR-036). */
    @Query("""
            select coalesce(sum(c.amountVnd), 0) from SettlementFolioCollection c
            where c.method = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod.CASH
              and c.status = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus.CONFIRMED
              and c.branchId = :branchId and c.cashBusinessDay = :businessDay""")
    long sumConfirmedCash(@Param("branchId") Long branchId, @Param("businessDay") LocalDate businessDay);
}
