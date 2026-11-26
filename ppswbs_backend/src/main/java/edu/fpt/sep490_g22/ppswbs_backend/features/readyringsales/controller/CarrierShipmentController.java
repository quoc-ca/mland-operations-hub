package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.CarrierShipmentDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CarrierShipmentService;
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

/** The simulated GHTK partner portal (Session 2026-11-20): GHTK shipments and the courier's two steps. */
@RestController
@RequestMapping(value = "/api/v1/carrier/shipments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CarrierShipmentController {

    private final CarrierShipmentService service;

    @GetMapping
    public CarrierShipmentDtos.Page list(HttpServletRequest request,
                                         @RequestParam(required = false) RetailShipmentStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "100") int size) {
        return service.list(actor(request), status, page, size);
    }

    @PostMapping("/{id}/transitions")
    public CarrierShipmentDtos.TransitionResult transition(HttpServletRequest request, @PathVariable String id,
                                                           @Valid @RequestBody CarrierShipmentDtos.TransitionRequest body) {
        return service.transition(actor(request), id, body.action(), body.expectedVersion());
    }

    private CurrentAccountActor actor(HttpServletRequest request) {
        Object value = request.getAttribute("currentAccountActor");
        return value instanceof CurrentAccountActor actor ? actor : null;
    }
}
