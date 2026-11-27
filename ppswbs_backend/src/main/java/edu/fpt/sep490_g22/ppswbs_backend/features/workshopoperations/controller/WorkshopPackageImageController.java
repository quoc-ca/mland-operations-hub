package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SharedMediaFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cover image bytes of a Workshop Package (FR-073); streamed by the application, never the provider URL. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkshopPackageImageController {

    private final WorkshopPackageService packageService;
    private final WorkshopPackageAuthorizationService packageAuthorization;

    @GetMapping("/workshop-packages/{packageId}/image")
    public ResponseEntity<InputStreamResource> publishedImage(@PathVariable Long packageId) {
        return stream(packageService.openPublishedImage(packageId));
    }

    @GetMapping("/workshop-package-management/packages/{packageId}/image")
    public ResponseEntity<InputStreamResource> managedImage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long packageId) {
        packageAuthorization.requireActiveManager(authorization);
        return stream(packageService.openManagedImage(packageId));
    }

    private static ResponseEntity<InputStreamResource> stream(SharedMediaFacade.Content image) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(image.content()));
    }
}
