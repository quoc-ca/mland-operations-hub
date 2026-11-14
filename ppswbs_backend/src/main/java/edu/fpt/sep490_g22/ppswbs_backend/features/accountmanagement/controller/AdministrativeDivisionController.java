package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AdministrativeDivisionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.AdministrativeDivisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdministrativeDivisionController {

    private final AdministrativeDivisionService divisionService;

    @GetMapping("/api/v1/administrative-divisions/provinces")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listProvinces() {
        return ResponseEntity.ok(divisionService.listProvinces());
    }

    @GetMapping("/api/v1/administrative-divisions/provinces/{provinceCode}/communes")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listCommunes(
        @PathVariable String provinceCode
    ) {
        return ResponseEntity.ok(divisionService.listCommunes(provinceCode));
    }

    // Compatibility alias for legacy /api/v1/addresses/provinces
    @GetMapping("/api/v1/addresses/provinces")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listProvincesAlias() {
        return listProvinces();
    }

    @GetMapping("/api/v1/addresses/districts")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listDistrictsAlias(
        @RequestParam(name = "provinceId", required = false) String provinceId
    ) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/api/v1/addresses/wards")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listWardsAlias(
        @RequestParam(name = "districtId", required = false) String districtId
    ) {
        return ResponseEntity.ok(List.of());
    }
}
