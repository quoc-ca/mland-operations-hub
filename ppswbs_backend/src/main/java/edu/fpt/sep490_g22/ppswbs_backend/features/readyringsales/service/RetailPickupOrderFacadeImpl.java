package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPickupOrderFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPickupException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RetailPickupOrderFacadeImpl implements RetailPickupOrderFacade {
    private final RetailOrderRepository orders;
    @Override public PickupOrder lock(String orderId) { return view(require(orderId)); }
    @Override public PickupOrder markPrepared(String orderId) {
        RetailOrder order = require(orderId);
        if (!"PAID".equals(order.getPaymentState().name()) || !"PICKUP".equals(order.getFulfilmentMethod())
            || order.isPickupReadyConfirmed() || "PICKED_UP".equals(order.getFulfilmentState())) throw new RetailPickupException(RetailPickupException.Reason.INVALID_STATE);
        order.markPickupReady();
        return view(order);
    }
    @Override public PickupOrder markHandedOver(String orderId) {
        RetailOrder order = require(orderId);
        if (!"PAID".equals(order.getPaymentState().name()) || !"PICKUP".equals(order.getFulfilmentMethod())
            || !order.isPickupReadyConfirmed() || !"READY_FOR_PICKUP".equals(order.getFulfilmentState())) throw new RetailPickupException(RetailPickupException.Reason.INVALID_STATE);
        order.markPickedUp();
        return view(order);
    }
    private RetailOrder require(String id) { return orders.lockOrder(id).orElseThrow(() -> new RetailPickupException(RetailPickupException.Reason.NOT_FOUND)); }
    private PickupOrder view(RetailOrder order) { return new PickupOrder(order.getId(), order.getLocationId(), order.getPaymentState().name(),
        order.getFulfilmentMethod(), order.getFulfilmentState(), order.isPickupReadyConfirmed()); }
}
