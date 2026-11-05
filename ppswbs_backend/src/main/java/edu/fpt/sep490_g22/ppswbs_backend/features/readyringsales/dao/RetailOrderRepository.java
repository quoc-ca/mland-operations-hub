package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface RetailOrderRepository extends JpaRepository<RetailOrder, String> {
    @Transactional(readOnly = true)
    @Query("select distinct o from RetailOrder o join fetch o.groupedCheckout c left join fetch o.items "
        + "where c.memberAccountId = :memberId order by c.createdAt desc, o.id asc")
    List<RetailOrder> findOwnedOrders(@Param("memberId") Long memberId);

    @Transactional(readOnly = true)
    @Query("select distinct o from RetailOrder o join fetch o.groupedCheckout c left join fetch o.items "
        + "where c.memberAccountId = :memberId and o.id = :orderId")
    Optional<RetailOrder> findOwnedOrder(@Param("memberId") Long memberId, @Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RetailOrder o join fetch o.groupedCheckout where o.id = :orderId")
    Optional<RetailOrder> lockOrder(@Param("orderId") String orderId);
}
