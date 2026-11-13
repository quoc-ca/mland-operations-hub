package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCashCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailPickupView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCashCollection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Read models for the Staff pickup desk and the Manager daily cash view (FR-08-045, FR-08-047). */
@Service
@RequiredArgsConstructor
public class RetailCounterCashQueryService {
    private static final Duration PICKUP_DESK_WINDOW = Duration.ofDays(30);

    private final RetailOrderRepository orders;
    private final RetailCashCollectionRepository collections;
    private final RetailOrderQueryService orderViews;

    /**
     * Pickup orders that Staff can act on: unpaid cash orders still awaiting the customer and paid orders,
     * limited to the actor's branches (or one of them) and the last 30 days.
     */
    @Transactional(readOnly = true)
    public List<StaffRetailPickupView> pickupDesk(CurrentAccountActor actor, Long locationId) {
        RetailCashCollectionService.requireActiveStaffOrManager(actor);
        List<Long> locations = actor.activeLocationIds();
        if (locationId != null) {
            if (!locations.contains(locationId)) throw new RetailAccessDeniedException("Location is outside the assigned locations");
            locations = List.of(locationId);
        }
        return orders.findBranchPickupOrders(locations, Instant.now().minus(PICKUP_DESK_WINDOW)).stream()
            .filter(order -> order.getPaymentState() == PaymentState.PAID
                || (order.getPaymentState() == PaymentState.ACCEPTED_UNPAID && order.getGroupedCheckout().isCounterCash()))
            .map(order -> new StaffRetailPickupView(orderViews.view(order), order.getFulfilmentState(),
                order.isPickupReadyConfirmed(), collections.findByRetailOrderId(order.getId())
                    .map(row -> RetailCashCollectionService.view(row, order.getPaymentState().name())).orElse(null)))
            .toList();
    }

    /** Confirmed retail counter cash of one permitted branch and Vietnam business date (Manager, read-only). */
    @Transactional(readOnly = true)
    public CheckoutDtos.DailyCashView daily(CurrentAccountActor actor, Long locationId, LocalDate businessDate) {
        if (actor == null || actor.accountId() == null) throw new RetailUnauthorizedException("Authentication is required");
        if (actor.status() != ActorStatus.ACTIVE || actor.role() != ActorRole.MANAGER) {
            throw new RetailAccessDeniedException("Active Manager access is required");
        }
        if (locationId == null || businessDate == null) throw new RetailConflictException("Branch and business date are required");
        if (!actor.activeLocationIds().contains(locationId)) throw new RetailAccessDeniedException("Location is outside the assigned locations");
        List<RetailCashCollection> rows = collections.findByLocationIdAndBusinessDateOrderByConfirmedAtAsc(locationId, businessDate);
        BigDecimal total = rows.stream().map(RetailCashCollection::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CheckoutDtos.DailyCashView(locationId, businessDate.toString(), rows.size(),
            RetailCashCollectionService.money(total),
            rows.stream().map(row -> RetailCashCollectionService.view(row, PaymentState.PAID.name())).toList());
    }
}
