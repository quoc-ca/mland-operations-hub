package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoyaltyWalletRepository extends JpaRepository<LoyaltyWallet, Long> {

    /** Exclusive wallet lock (lock order step 5). An absent wallet reads as zero and is not created by a read. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from LoyaltyWallet w where w.memberId = :memberId")
    Optional<LoyaltyWallet> lockByMemberId(@Param("memberId") Long memberId);
}
