package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentTransitionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.StaffRetailShipmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff/Manager endpoints for the fictional GHTK shipments (FE-08 mock-shipment slice).
 *
 * <p>No shipment is ever created here; the mock tracking number and status already exist by the
 * time Staff sees it (created at payment-confirmation time by {@code RetailShipmentCreationService}).
 * This controller only reads the branch-scoped list/detail and records hand-driven transitions.
 */
@RestController
@RequestMapping(value = "/api/v1/staff/retail-shipments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class StaffRetailShipmentController {

    private final StaffRetailShipmentService service;

    @GetMapping
    public StaffRetailShipmentSummary.Page list(HttpServletRequest request,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) RetailShipmentStatus status,
                                              @RequestParam(required = false) Long locationId) {
        var query = new StaffRetailShipmentSummary.ListQuery(page, size, status, locationId);
        return service.list(actor(request), query);
    }

    @GetMapping("/{id}")
    public StaffRetailShipmentView detail(HttpServletRequest request, @PathVariable String id) {
        return service.detail(actor(request), id);
    }

    @PostMapping("/{id}/transitions")
    public StaffRetailShipmentTransitionResult transition(HttpServletRequest request, @PathVariable String id,
                                                         @Valid @RequestBody StaffRetailShipmentSummary.TransitionRequest body) {
        return service.transition(actor(request), id, body.nextStatus(), body.expectedVersion(), body.note());
    }

    private CurrentAccountActor actor(HttpServletRequest request) {
        Object value = request.getAttribute("currentAccountActor");
        return value instanceof CurrentAccountActor actor ? actor : null;
    }
}
