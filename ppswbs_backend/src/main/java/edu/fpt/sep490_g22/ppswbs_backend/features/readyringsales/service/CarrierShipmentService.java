package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.CarrierShipmentDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.CustomShipmentCarrierPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatusHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The simulated GHTK partner portal (Session 2026-11-20). An active account with role CARRIER (added by FE-01) sees
 * the GHTK shipments of every branch and records only the courier's two steps: collected ("Đã lấy hàng", from
 * READY_FOR_CARRIER) and delivered ("Giao thành công", from HANDED_TO_CARRIER). Packing stays with Staff.
 */
@Service
public class CarrierShipmentService {

    private static final int MAX_PAGE_SIZE = 200;

    private final RetailShipmentRepository shipments;
    private final CustomShipmentCarrierPort customShipmentCarrier;

    public CarrierShipmentService(RetailShipmentRepository shipments) {
        this(shipments, null);
    }

    @Autowired
    public CarrierShipmentService(
            RetailShipmentRepository shipments,
            @Autowired(required = false) CustomShipmentCarrierPort customShipmentCarrier
    ) {
        this.shipments = shipments;
        this.customShipmentCarrier = customShipmentCarrier;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public CarrierShipmentDtos.Page list(CurrentAccountActor actor, RetailShipmentStatus status, int page, int size) {
        requireCarrier(actor);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        var result = shipments.findCarrierShipments(status, pageable);
        List<CarrierShipmentDtos.Shipment> items = new ArrayList<>(result.getContent().stream().map(CarrierShipmentService::view).toList());
        long totalElements = result.getTotalElements();
        int totalPages = result.getTotalPages();

        if (customShipmentCarrier != null) {
            List<CarrierShipmentDtos.Shipment> customShipments = customShipmentCarrier.listForCarrier();
            if (status != null) {
                String filter = status.name();
                customShipments = customShipments.stream()
                        .filter(s -> filter.equalsIgnoreCase(s.status()))
                        .toList();
            }
            items.addAll(customShipments);
            items.sort(Comparator.comparing(CarrierShipmentDtos.Shipment::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
            totalElements += customShipments.size();
            totalPages = (int) Math.ceil((double) totalElements / Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        }

        return new CarrierShipmentDtos.Page(items, result.getNumber(), result.getSize(), totalElements, Math.max(totalPages, 1));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierShipmentDtos.TransitionResult transition(CurrentAccountActor actor, String shipmentId, String action,
                                                           Integer expectedVersion) {
        requireCarrier(actor);
        if (customShipmentCarrier != null && customShipmentCarrier.isCustomShipment(shipmentId)) {
            return customShipmentCarrier.transitionCustomShipment(shipmentId, action, expectedVersion);
        }
        return record(shipmentId, action, expectedVersion, actor.accountId(), "GHTK#" + actor.accountId());
    }

    /** The courier step itself, after the caller was checked; package-private for tests. */
    CarrierShipmentDtos.TransitionResult record(String shipmentId, String action, Integer expectedVersion,
                                               Long accountId, String actorLabel) {
        if (expectedVersion == null) throw new RetailConflictException("The current shipment version is required");
        RetailShipment shipment = shipments.lockById(shipmentId)
            .orElseThrow(() -> new RetailNotFoundException("Retail shipment does not exist"));
        if (shipment.getVersion() != expectedVersion) {
            throw new RetailConflictException("Đơn vừa được cập nhật, hãy tải lại danh sách.");
        }
        RetailShipmentStatus current = shipment.getStatus();
        RetailShipmentStatus next = switch (action == null ? "" : action) {
            case "PICKED_UP" -> {
                if (current != RetailShipmentStatus.READY_FOR_CARRIER) {
                    throw new RetailConflictException("Chỉ lấy được hàng khi shop đã chuẩn bị xong.");
                }
                yield RetailShipmentStatus.HANDED_TO_CARRIER;
            }
            case "DELIVERED" -> {
                if (current != RetailShipmentStatus.HANDED_TO_CARRIER && current != RetailShipmentStatus.IN_TRANSIT) {
                    throw new RetailConflictException("Chỉ giao thành công được đơn đang giao.");
                }
                yield RetailShipmentStatus.DELIVERED;
            }
            default -> throw new RetailConflictException("Unknown courier action");
        };
        try {
            shipment.advanceTo(next, accountId, actorLabel, null);
        } catch (IllegalStateException ex) {
            throw new RetailConflictException(ex.getMessage());
        }
        shipment.getBranchOrder().syncShipmentFulfilmentState(next);
        shipments.flush();
        return new CarrierShipmentDtos.TransitionResult(shipment.getId(), next.name(), shipment.getVersion(),
            shipment.getUpdatedAt());
    }

    static boolean isActiveCarrier(CurrentAccountActor actor) {
        return actor != null && actor.accountId() != null && actor.status() == ActorStatus.ACTIVE
            && actor.role() == ActorRole.CARRIER;
    }

    private static void requireCarrier(CurrentAccountActor actor) {
        if (actor == null || actor.accountId() == null) {
            throw new RetailUnauthorizedException("An authenticated GHTK partner account is required");
        }
        if (!isActiveCarrier(actor)) {
            throw new RetailAccessDeniedException("Only an active GHTK partner account can use the courier portal");
        }
    }

    static CarrierShipmentDtos.Shipment view(RetailShipment shipment) {
        var order = shipment.getBranchOrder();
        return new CarrierShipmentDtos.Shipment(shipment.getId(), shipment.getMockTrackingNumber(),
            shipment.getStatus().name(), shipment.getVersion(),
            RetailOrderCodes.of(order.getId(), shipment.getGroupedCheckout().getCreatedAt()), shipment.getLocationId(),
            shipment.getRecipientName(), shipment.getRecipientPhone(), shipment.getRecipientAddress(),
            shipment.getWardName(), shipment.getDistrictName(), shipment.getProvinceName(),
            order.getItems().stream().mapToInt(item -> item.getQuantity()).sum(),
            order.getShippingCharge() == null ? "0" : order.getShippingCharge().stripTrailingZeros().toPlainString(),
            shipment.getCreatedAt(), reachedAt(shipment, RetailShipmentStatus.READY_FOR_CARRIER),
            reachedAt(shipment, RetailShipmentStatus.HANDED_TO_CARRIER), reachedAt(shipment, RetailShipmentStatus.DELIVERED),
            shipment.getUpdatedAt());
    }

    private static Instant reachedAt(RetailShipment shipment, RetailShipmentStatus status) {
        return shipment.getHistory().stream().filter(entry -> entry.getStatus() == status)
            .map(RetailShipmentStatusHistory::getCreatedAt).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
