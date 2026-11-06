package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * Creates the fictional GHTK shipment for every paid GHTK branch order.
 *
 * <p>Called from the payment-confirmation paths (local mock verification, VNPay verified evidence, and the
 * zero-total internal acceptance) inside the same transaction that marks the checkout paid. The tracking
 * number is generated locally and never sent to GHTK; no shipment-creation call exists in this slice, so
 * Staff drives every later status transition by hand.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetailShipmentCreationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RetailShipmentRepository shipments;

    /** Idempotent: a branch order already carrying a shipment is left untouched. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void createShipmentsForPaidCheckout(GroupedCheckout checkout) {
        if (checkout == null || checkout.getPaymentState() != edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.PaymentState.PAID) {
            throw new RetailConflictException("Only paid checkouts can create shipments");
        }
        List<RetailOrder> ghtkOrders = checkout.getOrders().stream()
            .filter(order -> "GHTK".equalsIgnoreCase(order.getFulfilmentMethod()))
            .toList();
        for (RetailOrder order : ghtkOrders) {
            if (shipments.findByBranchOrder_Id(order.getId()).isPresent()) {
                continue;
            }
            RetailShipment shipment = new RetailShipment(checkout, order, nextTrackingNumber(),
                order.getRecipientName(), order.getRecipientPhone(), order.getRecipientAddress(),
                order.getRecipientProvince(), order.getRecipientDistrict(), order.getRecipientWard());
            shipment.getHistory().add(new edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatusHistory(
                shipment, edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus.PREPARING,
                null, "SYSTEM", "Created simulated shipment after verified payment"));
            order.syncShipmentFulfilmentState(edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus.PREPARING);
            shipments.save(shipment);
            log.info("Created mock GHTK shipment {} for branch order {} (checkout {})",
                shipment.getMockTrackingNumber(), order.getId(), checkout.getId());
        }
    }

    /**
     * GHTK-shaped label id: "S" plus ten digits, e.g. S1450382716. Uniqueness is enforced by the table's
     * unique key; a collision simply retries with a fresh number.
     */
    private String nextTrackingNumber() {
        String candidate;
        do {
            candidate = "S" + String.format(Locale.ROOT, "%010d", RANDOM.nextLong(10_000_000_000L));
        } while (shipments.existsByMockTrackingNumber(candidate));
        return candidate;
    }
}
