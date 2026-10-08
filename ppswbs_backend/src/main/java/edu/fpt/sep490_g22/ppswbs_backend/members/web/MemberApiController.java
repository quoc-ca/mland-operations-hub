package edu.fpt.sep490_g22.ppswbs_backend.members.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidTokenException;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.FirebaseAuthenticationToken;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.*;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @PutMapping
    public ResponseEntity<MemberDto> provisionOrReturnMember() {
        FirebaseAuthenticationToken auth = getAuthToken();
        MemberService.ProvisionResult result = memberService.provisionOrReturnMember(
                auth.getUid(), auth.getEmail(), auth.isEmailVerified());

        if (result.isNew()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.member());
        }
        return ResponseEntity.ok(result.member());
    }

    @GetMapping("/entitlement")
    public ResponseEntity<MemberEntitlementDto> getEntitlement() {
        FirebaseAuthenticationToken auth = getAuthToken();
        MemberEntitlementDto entitlement = memberService.getEntitlement(auth.getUid());
        return ResponseEntity.ok(entitlement);
    }

    @PostMapping("/policy-acceptances")
    public ResponseEntity<MemberEntitlementDto> acceptPolicies(
            @Valid @RequestBody PolicyAcceptanceRequestDto request,
            Locale locale) {
        FirebaseAuthenticationToken auth = getAuthToken();
        MemberEntitlementDto entitlement = memberService.acceptPolicies(
                auth.getUid(), request, locale.getLanguage());
        return ResponseEntity.status(HttpStatus.CREATED).body(entitlement);
    }


    private FirebaseAuthenticationToken getAuthToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof FirebaseAuthenticationToken token && token.isAuthenticated()) {
            return token;
        }
        throw new InvalidTokenException("Invalid or missing authentication context");
    }
}
