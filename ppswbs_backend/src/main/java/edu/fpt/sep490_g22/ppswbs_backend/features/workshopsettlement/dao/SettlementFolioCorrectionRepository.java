package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementFolioCorrectionRepository extends JpaRepository<SettlementFolioCorrection, Long> {

    Optional<SettlementFolioCorrection> findByCorrectionReference(String correctionReference);

    Optional<SettlementFolioCorrection> findByCaseReference(String caseReference);

    List<SettlementFolioCorrection> findAllByBookingReferenceOrderByCreatedAtDesc(String bookingReference);

    Optional<SettlementFolioCorrection> findByBookingReferenceAndCorrectionReference(String bookingReference, String correctionReference);
}

