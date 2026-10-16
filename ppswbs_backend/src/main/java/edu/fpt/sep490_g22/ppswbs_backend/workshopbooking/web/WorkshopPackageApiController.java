package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.web;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop-packages")
@RequiredArgsConstructor
public class WorkshopPackageApiController {

    private final WorkshopBookingService bookingService;

    @GetMapping
    public ResponseEntity<List<WorkshopPackage>> listPackages() {
        return ResponseEntity.ok(bookingService.listPublishedPackages());
    }

    @GetMapping("/{packageId}")
    public ResponseEntity<WorkshopPackage> getPackage(@PathVariable("packageId") Long packageId) {
        return ResponseEntity.ok(bookingService.getPublishedPackage(packageId));
    }
}
