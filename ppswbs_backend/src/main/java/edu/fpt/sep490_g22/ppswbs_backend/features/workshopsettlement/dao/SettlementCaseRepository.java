package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SettlementCaseRepository extends JpaRepository<SettlementCase, Long> {
 Optional<SettlementCase> findByCaseReference(String caseReference);
 /** First DB access of a resolving transaction: two resolvers must not both see the case as pending. */
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select c from SettlementCase c where c.caseReference = :caseReference")
 Optional<SettlementCase> findByCaseReferenceForUpdate(@org.springframework.data.repository.query.Param("caseReference") String caseReference);
 java.util.List<SettlementCase> findTop100ByCaseTypeAndLifecycleStatusOrderByCreatedAtAsc(String caseType, String lifecycleStatus);
 java.util.List<SettlementCase> findTop100ByCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
         java.util.Collection<String> caseTypes, String lifecycleStatus);
 java.util.List<SettlementCase> findByBookingReferenceAndCaseTypeInAndLifecycleStatusOrderByCreatedAtAsc(
         String bookingReference, java.util.Collection<String> caseTypes, String lifecycleStatus);
 java.util.List<SettlementCase> findTop100ByOrderByCreatedAtDesc();
 java.util.List<SettlementCase> findTop100ByCaseTypeOrderByCreatedAtDesc(String caseType);
 java.util.List<SettlementCase> findTop100ByLifecycleStatusOrderByCreatedAtDesc(String lifecycleStatus);
 java.util.List<SettlementCase> findTop100ByCaseTypeAndLifecycleStatusOrderByCreatedAtDesc(
         String caseType, String lifecycleStatus);
}
