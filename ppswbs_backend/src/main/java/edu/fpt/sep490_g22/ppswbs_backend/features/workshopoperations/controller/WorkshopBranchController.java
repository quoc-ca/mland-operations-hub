package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopBranchService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * T018: REST controller for Workshop Branch and Branch Slot endpoints.
 * Corresponds to OpenAPI paths:
 * - GET  /api/v1/workshop-branches
 * - POST /api/v1/workshop-branches
 * - GET  /api/v1/workshop-branches/{branchId}/slots
 * - POST /api/v1/workshop-branches/{branchId}/slots
 */
@RestController
@RequestMapping("/api/v1/workshop-branches")
@RequiredArgsConstructor
public class WorkshopBranchController {

    private final WorkshopBranchService branchService;
    private final WorkshopSlotService slotService;

    @GetMapping
    public ResponseEntity<List<BranchResponse>> listBranches(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        List<BranchResponse> response = branchService.listBranches(authHeader);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<BranchResponse> createBranch(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody BranchRequest request) {
        BranchResponse response = branchService.createBranch(authHeader, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{branchId}/slots")
    public ResponseEntity<List<SlotResponse>> listBranchSlots(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("branchId") String branchId) {
        List<SlotResponse> response = slotService.listSlots(authHeader, branchId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{branchId}/slots")
    public ResponseEntity<SlotResponse> createSlot(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("branchId") String branchId,
            @Valid @RequestBody SlotRequest request) {
        SlotResponse response = slotService.createSlot(authHeader, branchId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
