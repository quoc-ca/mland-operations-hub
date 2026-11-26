package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CarrierShipmentDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service managing custom order fulfillment via Mock GHTK (Method 1B).
 * Enforces role boundaries:
 * - Workshop Staff prepares & advances to READY_FOR_CARRIER.
 * - Carrier (role CARRIER) performs PICKED_UP and DELIVERED.
 * - DELIVERED automatically synchronizes CustomOrder to CLOSED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOrderShipmentService {

    private static final SecureRandom RANDOM = new SecureRandom();
    public static final String CUSTOM_SHIPMENT_PREFIX = "CUST_SHIP_";

    private final CustomOrderShipmentRepository shipmentRepository;
    private final CustomOrderRepository customOrderRepository;

    /**
     * Creates a custom order shipment record in PREPARING status with a GHTK tracking number (prefix 'S' + 10 digits).
     * Idempotent: returns existing shipment if already created for this order.
     */
    @Transactional
    public CustomOrderShipment createShipmentForOrder(CustomOrder order) {
        Optional<CustomOrderShipment> existing = shipmentRepository
                .findTopByCustomOrderReferenceOrderByCreatedAtDesc(order.getCustomOrderReference());
        if (existing.isPresent()) {
            return existing.get();
        }

        String trackingNumber = generateGhtkTrackingNumber();
        String shippingAddr = order.getShippingAddress() != null && !order.getShippingAddress().isBlank()
                ? order.getShippingAddress()
                : "Địa chỉ nhận mặc định của khách hàng";

        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .customOrderId(order.getId())
                .customOrderReference(order.getCustomOrderReference())
                .trackingNumber(trackingNumber)
                .carrierService("GHTK")
                .shippingAddress(shippingAddr)
                .recipientName("Khách hàng đơn " + order.getCustomOrderReference())
                .status("PREPARING")
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        CustomOrderShipment saved = shipmentRepository.save(shipment);
        log.info("Created custom order shipment: id={}, tracking={}, orderRef={}",
                saved.getId(), saved.getTrackingNumber(), order.getCustomOrderReference());
        return saved;
    }

    /**
     * Workshop Staff action: pack parcel and advance shipment to READY_FOR_CARRIER ("Chờ bưu tá lấy").
     */
    @Transactional
    public CustomOrderShipment staffAdvanceToReadyForCarrier(String customOrderReference) {
        CustomOrderShipment shipment = shipmentRepository
                .findTopByCustomOrderReferenceOrderByCreatedAtDesc(customOrderReference)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy vận đơn cho đơn " + customOrderReference));

        if (!"PREPARING".equalsIgnoreCase(shipment.getStatus())) {
            log.info("Shipment for {} is already at status {}", customOrderReference, shipment.getStatus());
            return shipment;
        }

        shipment.setStatus("READY_FOR_CARRIER");
        shipment.setUpdatedAt(Instant.now());
        CustomOrderShipment updated = shipmentRepository.save(shipment);
        log.info("Staff advanced custom shipment {} to READY_FOR_CARRIER", customOrderReference);
        return updated;
    }

    /**
     * Resolves shipment details by order reference.
     */
    @Transactional(readOnly = true)
    public Optional<CustomOrderShipment> findByCustomOrderReference(String customOrderReference) {
        return shipmentRepository.findTopByCustomOrderReferenceOrderByCreatedAtDesc(customOrderReference);
    }

    /**
     * Lists custom shipments converted to CarrierShipmentDtos.Shipment for unified carrier portal view.
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<CarrierShipmentDtos.Shipment> listForCarrier() {
        return shipmentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toCarrierDto)
                .toList();
    }

    /**
     * Checks if a shipment ID belongs to custom order shipments.
     */
    public boolean isCustomShipment(String shipmentId) {
        if (shipmentId == null) return false;
        if (shipmentId.startsWith(CUSTOM_SHIPMENT_PREFIX)) return true;
        try {
            Long id = Long.parseLong(shipmentId);
            return shipmentRepository.existsById(id);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Transitions a custom shipment through courier actions:
     * - PICKED_UP ("Đã lấy hàng"): READY_FOR_CARRIER -> HANDED_TO_CARRIER
     * - DELIVERED ("Giao thành công"): HANDED_TO_CARRIER -> DELIVERED -> synchronizes CustomOrder to CLOSED.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierShipmentDtos.TransitionResult transitionCustomShipment(
            String shipmentId,
            String action,
            Integer expectedVersion
    ) {
        Long rawId = parseShipmentId(shipmentId);
        CustomOrderShipment shipment = shipmentRepository.lockById(rawId)
                .orElseThrow(() -> new IllegalArgumentException("Custom shipment does not exist: " + shipmentId));

        if (expectedVersion != null && !expectedVersion.equals(shipment.getVersion())) {
            throw new IllegalStateException("Đơn vừa được cập nhật, hãy tải lại danh sách.");
        }

        String currentStatus = shipment.getStatus();
        String nextStatus;
        Instant now = Instant.now();

        switch (action == null ? "" : action) {
            case "PICKED_UP" -> {
                if (!"READY_FOR_CARRIER".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Chỉ lấy được hàng khi xưởng đã chuẩn bị xong (READY_FOR_CARRIER).");
                }
                nextStatus = "HANDED_TO_CARRIER";
                shipment.setPickedUpAt(now);
            }
            case "DELIVERED" -> {
                if (!"HANDED_TO_CARRIER".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Chỉ giao thành công được đơn đang trên đường giao (HANDED_TO_CARRIER).");
                }
                nextStatus = "DELIVERED";
                shipment.setDeliveredAt(now);
            }
            default -> throw new IllegalArgumentException("Hành động của bưu tá không hợp lệ: " + action);
        }

        shipment.setStatus(nextStatus);
        shipment.setUpdatedAt(now);
        shipmentRepository.saveAndFlush(shipment);

        // Sync with CustomOrder lifecycle
        if ("HANDED_TO_CARRIER".equals(nextStatus)) {
            customOrderRepository.findByCustomOrderReference(shipment.getCustomOrderReference())
                    .ifPresent(order -> {
                        order.setStatus("DELIVERING");
                        order.setFulfilmentStatus("HANDED_TO_CARRIER");
                        order.setUpdatedAt(now);
                        customOrderRepository.save(order);
                        log.info("CustomOrder {} updated to DELIVERING upon courier PICKED_UP confirmation",
                                order.getCustomOrderReference());
                    });
        } else if ("DELIVERED".equals(nextStatus)) {
            customOrderRepository.findByCustomOrderReference(shipment.getCustomOrderReference())
                    .ifPresent(order -> {
                        order.setStatus("CLOSED");
                        order.setFulfilmentStatus("DELIVERED");
                        order.setUpdatedAt(now);
                        customOrderRepository.save(order);
                        log.info("CustomOrder {} successfully CLOSED upon carrier DELIVERED confirmation",
                                order.getCustomOrderReference());
                    });
        }

        return new CarrierShipmentDtos.TransitionResult(
                shipmentId,
                nextStatus,
                shipment.getVersion(),
                shipment.getUpdatedAt()
        );
    }

    public CarrierShipmentDtos.Shipment toCarrierDto(CustomOrderShipment shipment) {
        String carrierId = CUSTOM_SHIPMENT_PREFIX + shipment.getId();
        return new CarrierShipmentDtos.Shipment(
                carrierId,
                shipment.getTrackingNumber(),
                shipment.getStatus(),
                shipment.getVersion() != null ? shipment.getVersion() : 1,
                shipment.getCustomOrderReference(),
                null, // pickupLocationId
                shipment.getRecipientName() != null ? shipment.getRecipientName() : "Khách hàng Mland",
                shipment.getRecipientPhone() != null ? shipment.getRecipientPhone() : "---",
                shipment.getShippingAddress(),
                "Phường Tân Phú", // default/mock geo fields
                "Quận 9",
                "TP. Hồ Chí Minh",
                1, // itemQuantity
                "Miễn phí", // shippingCharge
                shipment.getCreatedAt(),
                shipment.getCreatedAt(), // readyAt
                shipment.getPickedUpAt(),
                shipment.getDeliveredAt(),
                shipment.getUpdatedAt()
        );
    }

    private Long parseShipmentId(String shipmentId) {
        if (shipmentId.startsWith(CUSTOM_SHIPMENT_PREFIX)) {
            return Long.parseLong(shipmentId.substring(CUSTOM_SHIPMENT_PREFIX.length()));
        }
        return Long.parseLong(shipmentId);
    }

    private String generateGhtkTrackingNumber() {
        // GHTK standard format: prefix 'S' followed by 10 digits
        long digits = 1_000_000_000L + (long) (RANDOM.nextDouble() * 8_999_999_999L);
        return "S" + digits;
    }
}
