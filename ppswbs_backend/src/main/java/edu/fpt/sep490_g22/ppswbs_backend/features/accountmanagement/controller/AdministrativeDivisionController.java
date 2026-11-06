package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AdministrativeDivisionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.AdministrativeDivisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AdministrativeDivisionController {

    private final AdministrativeDivisionService divisionService;

    @GetMapping("/provinces")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listProvinces() {
        return ResponseEntity.ok(divisionService.listProvinces());
    }

    @GetMapping("/districts")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listDistricts(
        @RequestParam(name = "provinceId", required = true) String provinceId
    ) {
        return ResponseEntity.ok(divisionService.listDistricts(provinceId));
    }

    @GetMapping("/wards")
    public ResponseEntity<List<AdministrativeDivisionResponse>> listWards(
        @RequestParam(name = "districtId", required = true) String districtId
    ) {
        return ResponseEntity.ok(divisionService.listWards(districtId));
    }
}

