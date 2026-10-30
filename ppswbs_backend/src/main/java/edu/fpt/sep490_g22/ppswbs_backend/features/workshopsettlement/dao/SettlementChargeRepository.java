package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SettlementChargeRepository extends JpaRepository<SettlementCharge, Long> { List<SettlementCharge> findByBillReference(String billReference); }
