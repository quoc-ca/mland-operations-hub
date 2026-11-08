package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentTransitionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatusHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Staff/Manager operations on the fictional GHTK shipments (FE-08 mock-shipment slice).
 *
 * <p>Authorization model mirrors {@code PickupOperationsService}: the identity gate has already
 * resolved the {@link CurrentAccountActor}; this service re-checks active STAFF or MANAGER status
 * server-side and scopes every read and write to the actor's assigned branch locations. There is
 * no debug or header-based identity path here.
 *
 * <p>Transitions are Staff-driven only (no GHTK callback exists in this slice): the entity guard
 * {@link RetailShipment#advanceTo} rejects skipped, repeated, or post-delivery moves, the
 * expected-version check rejects stale concurrent updates, and every accepted move appends an
 * audited history entry and mirrors the new status onto the branch order's fulfilment state.
 */
@Service
@RequiredArgsConstructor
public class StaffRetailShipmentService {

    static final String CURRENT_ACTOR_ATTRIBUTE = "currentAccountActor";

    private final RetailShipmentRepository shipments;

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public StaffRetailShipmentSummary.Page list(CurrentAccountActor actor, StaffRetailShipmentSummary.ListQuery query) {
        requireActiveStaffOrManager(actor);
        Pageable pageable = PageRequest.of(query.page(), query.size(),
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        List<Long> locations = actor.activeLocationIds();
        if (query.locationId() != null) {
            if (!locations.contains(query.locationId())) {
                throw new RetailAccessDeniedException("Location is outside the assigned locations");
            }
            locations = List.of(query.locationId());
        }
        Page<RetailShipment> page = shipments.findStaffShipments(locations, query.status(), pageable);
        List<StaffRetailShipmentSummary> items = page.getContent().stream().map(StaffRetailShipmentService::summary).toList();
        return new StaffRetailShipmentSummary.Page(items, page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public StaffRetailShipmentView detail(CurrentAccountActor actor, String shipmentId) {
        requireActiveStaffOrManager(actor);
        RetailShipment shipment = shipments.findByIdAndLocationIdIn(shipmentId, actor.activeLocationIds())
            .orElseThrow(() -> new RetailNotFoundException("Retail shipment does not exist"));
        return view(shipment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffRetailShipmentTransitionResult transition(CurrentAccountActor actor, String shipmentId,
                                                         RetailShipmentStatus nextStatus,
                                                         Integer expectedVersion, String note) {
        requireActiveStaffOrManager(actor);
        if (nextStatus == null) {
            throw new RetailConflictException("A next shipment status is required");
        }
        if (expectedVersion == null) {
            throw new RetailConflictException("The current shipment version is required");
        }
        RetailShipment shipment = shipments.lockById(shipmentId)
            .orElseThrow(() -> new RetailNotFoundException("Retail shipment does not exist"));
        if (!actor.activeLocationIds().contains(shipment.getLocationId())) {
            throw new RetailAccessDeniedException("Shipment is outside the assigned location");
        }
        if (shipment.getVersion() != expectedVersion) {
            throw new RetailConflictException("The shipment changed; refresh before retrying");
        }
        RetailShipmentStatusHistory entry;
        try {
            entry = shipment.advanceTo(nextStatus, actor.accountId(), actorLabel(actor), note);
        } catch (IllegalStateException ex) {
            throw new RetailConflictException(ex.getMessage());
        }
        shipment.getBranchOrder().syncShipmentFulfilmentState(nextStatus);
        // Flush before projecting the optimistic version and generated update timestamp.
        shipments.flush();
        return new StaffRetailShipmentTransitionResult(shipment.getId(), entry.getStatus(),
            shipment.getVersion(), shipment.getUpdatedAt());
    }

    private void requireActiveStaffOrManager(CurrentAccountActor actor) {
        if (actor == null || actor.accountId() == null) {
            throw new RetailUnauthorizedException("An authenticated staff actor is required");
        }
        if (actor.status() != ActorStatus.ACTIVE
            || (actor.role() != ActorRole.STAFF && actor.role() != ActorRole.MANAGER)) {
            throw new RetailAccessDeniedException("Only active Staff or Manager can manage retail shipments");
        }
        if (actor.activeLocationIds().isEmpty()) {
            throw new RetailAccessDeniedException("No assigned branch location for this actor");
        }
    }

    private static String actorLabel(CurrentAccountActor actor) {
        return (actor.role() == ActorRole.MANAGER ? "MANAGER" : "STAFF") + "#" + actor.accountId();
    }

    static StaffRetailShipmentSummary summary(RetailShipment shipment) {
        return new StaffRetailShipmentSummary(shipment.getId(), shipment.getBranchOrder().getId(),
            shipment.getBranchOrderIdentifier(), shipment.getLocationId(), shipment.getMockTrackingNumber(),
            shipment.getStatus(), shipment.getVersion(), shipment.getRecipientName(),
            shipment.getProvinceName(), shipment.getDistrictName(), shipment.getWardName(),
            shipment.getCreatedAt(), shipment.getUpdatedAt());
    }

    static StaffRetailShipmentView view(RetailShipment shipment) {
        List<StaffRetailShipmentView.HistoryEntry> history = shipment.getHistory().stream()
            .map(entry -> new StaffRetailShipmentView.HistoryEntry(entry.getStatus(), entry.getActorId(),
                entry.getActorLabel(), entry.getNote(), entry.getCreatedAt()))
            .toList();
        return new StaffRetailShipmentView(shipment.getId(), shipment.getBranchOrder().getId(),
            shipment.getBranchOrderIdentifier(), shipment.getLocationId(), shipment.getFulfilmentMethod(),
            shipment.getMockTrackingNumber(), shipment.getStatus(), shipment.getVersion(),
            shipment.getRecipientName(), shipment.getRecipientPhone(), shipment.getRecipientAddress(),
            shipment.getProvinceName(), shipment.getDistrictName(), shipment.getWardName(),
            shipment.getCreatedAt(), shipment.getUpdatedAt(), history);
    }
}
