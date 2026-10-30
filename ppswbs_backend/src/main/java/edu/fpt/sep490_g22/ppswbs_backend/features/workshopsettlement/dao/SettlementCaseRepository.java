package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SettlementCaseRepository extends JpaRepository<SettlementCase, Long> { Optional<SettlementCase> findByCaseReference(String caseReference); }
