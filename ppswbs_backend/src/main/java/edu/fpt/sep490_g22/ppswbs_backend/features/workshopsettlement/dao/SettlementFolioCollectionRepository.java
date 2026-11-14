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

    List<SettlementFolioCollection> findByBillReferenceAndStatusOrderByIdAsc(String billReference,
                                                                            FolioCollectionStatus status);

    /** Confirmed cash of one branch and working day, net of change returned (FR-036). */
    @Query("""
            select coalesce(sum(c.amountVnd), 0) from SettlementFolioCollection c
            where c.method = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod.CASH
              and c.status = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus.CONFIRMED
              and c.branchId = :branchId and c.cashBusinessDay = :businessDay""")
    long sumConfirmedCash(@Param("branchId") Long branchId, @Param("businessDay") LocalDate businessDay);
}
