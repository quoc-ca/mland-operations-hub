package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import java.util.List;

/**
 * Custom-order shipments shown and recorded in the courier portal. Ready Ring Sales owns the port and custom
 * manufacturing implements it, so the dependency between the two modules stays one-way.
 */
public interface CustomShipmentCarrierPort {

    List<CarrierShipmentDtos.Shipment> listForCarrier();

    boolean isCustomShipment(String shipmentId);

    CarrierShipmentDtos.TransitionResult transitionCustomShipment(String shipmentId, String action,
                                                                  Integer expectedVersion);
}
