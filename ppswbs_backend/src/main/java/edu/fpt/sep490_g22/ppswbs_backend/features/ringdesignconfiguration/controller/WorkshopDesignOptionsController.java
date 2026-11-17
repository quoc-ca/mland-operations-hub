package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.DesignOptionsResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.DesignOptionRulesPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workshop-design-options")
@RequiredArgsConstructor
public class WorkshopDesignOptionsController {

    private final DesignOptionRulesPort rulesPort;

    @GetMapping
    public ResponseEntity<DesignOptionsResponseDto> getWorkshopDesignOptions(
            @RequestParam String packageReference,
            @RequestParam(required = false) String branchReference
    ) {
        DesignOptionsResponseDto response = rulesPort.loadPackageOptions(packageReference, branchReference);
        return ResponseEntity.ok(response);
    }
}

