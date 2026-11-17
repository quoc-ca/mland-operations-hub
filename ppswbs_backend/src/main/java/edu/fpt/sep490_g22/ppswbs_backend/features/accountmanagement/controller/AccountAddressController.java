package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AddressResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.MemberAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/me/addresses")
@RequiredArgsConstructor
public class AccountAddressController {

    private final MemberAddressService addressService;

    @GetMapping
    public ResponseEntity<List<AddressResponse>> getOwnAddresses() {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        return ResponseEntity.ok(addressService.getOwnAddresses(actor.accountId()));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createOwnAddress(@Valid @RequestBody AddressCreateRequest request) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        AddressResponse created = addressService.createAddress(actor.accountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/default")
    public ResponseEntity<AddressResponse> getDefaultAddress() {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        return ResponseEntity.ok(addressService.getDefaultAddress(actor.accountId()));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> updateOwnAddress(
        @PathVariable Long addressId,
        @Valid @RequestBody AddressUpdateRequest request
    ) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        return ResponseEntity.ok(addressService.updateAddress(actor.accountId(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteOwnAddress(@PathVariable Long addressId) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        addressService.deleteAddress(actor.accountId(), addressId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{addressId}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(@PathVariable Long addressId) {
        CurrentAccountActor actor = ActorContext.requireCurrentActor();
        return ResponseEntity.ok(addressService.setDefaultAddress(actor.accountId(), addressId));
    }
}

