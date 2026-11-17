package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

/**
 * Minimal post-payment event published once for each paid branch order.
 *
 * <p>The fulfilment module consumes this boundary without accessing retail
 * entities, repositories, services, controllers, or DTOs.</p>
 */
public record RetailOrderPaid(
    String branchOrderId,
    String fulfilmentMethod,
    long sourceVersion,
    String correlationId
) {
}
