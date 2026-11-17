package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Manager-only lifecycle inventory; the public add-on route intentionally exposes ACTIVE only. */
@RestController
@RequestMapping("/api/v1/workshop-package-management/packages/{packageId}/locations/{locationId}/add-ons")
@RequiredArgsConstructor
public class WorkshopAddOnManagementController {
    private final WorkshopAddOnService service;
    private final WorkshopPackageAuthorizationService authorization;

    @GetMapping
    public List<AddOnResponse> listForManagement(
            @PathVariable Long packageId,
            @PathVariable Long locationId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authorization.requireActiveManager(authorizationHeader);
        return service.listForManagement(packageId, locationId);
    }
}
