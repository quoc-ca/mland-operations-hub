package edu.fpt.sep490_g22.ppswbs_backend.members.web;

import edu.fpt.sep490_g22.ppswbs_backend.members.application.CurrentPolicyResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyApiController {

    private final MemberService memberService;

    @GetMapping("/current")
    public ResponseEntity<CurrentPolicyResponseDto> getCurrentPolicies() {
        return ResponseEntity.ok(memberService.getCurrentPolicies());
    }
}
