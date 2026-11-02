package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

/** Source-owned, transaction-bound pickup order operations. */
public interface RetailPickupOrderFacade {
    PickupOrder lock(String orderId);
    PickupOrder markPrepared(String orderId);
    PickupOrder markHandedOver(String orderId);
    record PickupOrder(String orderId, Long locationId, String paymentState, String fulfilmentMethod,
                       String fulfilmentState, boolean readyConfirmed) { }
}
