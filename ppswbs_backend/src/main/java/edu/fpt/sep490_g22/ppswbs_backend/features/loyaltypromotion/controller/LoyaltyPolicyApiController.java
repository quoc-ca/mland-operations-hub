package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyPolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyPolicyDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization.Capability;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyPolicyService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Points policy API (T033): getCurrentLoyaltyPolicy and updateCurrentLoyaltyPolicy. Only a current Manager may read
 * or change it; Admin has no FE-06 capability. An update needs {@code Idempotency-Key} and {@code expectedVersion},
 * answers only after commit and is never cached.
 */
@RestController
@RequestMapping("/api/v1/loyalty-policies/current")
class LoyaltyPolicyApiController {

    private final LoyaltyAuthorization authorization;
    private final LoyaltyPolicyService policies;
    private final LoyaltyPolicyRepository policyRows;

    LoyaltyPolicyApiController(LoyaltyAuthorization authorization, LoyaltyPolicyService policies,
                               LoyaltyPolicyRepository policyRows) {
        this.authorization = authorization;
        this.policies = policies;
        this.policyRows = policyRows;
    }

    @GetMapping
    ResponseEntity<LoyaltyPolicyDtos.Policy> current() {
        authorization.requireManager(Capability.MANAGE_POLICY);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(LoyaltyPolicyDtos.Policy.of(policies.current()));
    }

    @PutMapping
    ResponseEntity<LoyaltyPolicyDtos.Policy> update(@RequestHeader("Idempotency-Key") UUID key,
                                                    @RequestBody JsonNode body) {
        String manager = authorization.requireManager(Capability.MANAGE_POLICY);
        long policyId = policies.update(manager, key, LoyaltyPolicyDtos.parseUpdate(body));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(LoyaltyPolicyDtos.Policy.of(policyRows.findById(policyId).orElseThrow()));
    }
}
