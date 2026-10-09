package edu.fpt.sep490_g22.ppswbs_backend.members.web;

import edu.fpt.sep490_g22.ppswbs_backend.configuration.FirebaseProperties;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.CurrentPolicyResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/member-auth")
@RequiredArgsConstructor
public class MemberViewController {

    private final FirebaseProperties firebaseProperties;
    private final MemberService memberService;

    @GetMapping("/signin")
    public String showSignInPage(
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Model model) {
        String safeReturnTo = sanitizeReturnTo(returnTo);
        model.addAttribute("returnTo", safeReturnTo);
        model.addAttribute("firebaseConfig", firebaseProperties.getClient());
        
        CurrentPolicyResponseDto policies = memberService.getCurrentPolicies();
        model.addAttribute("policies", policies.getPolicies());

        return "member-auth/signin";
    }

    @GetMapping("/policy-acceptance")
    public String showPolicyAcceptancePage(
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Model model) {
        model.addAttribute("returnTo", sanitizeReturnTo(returnTo));
        model.addAttribute("firebaseConfig", firebaseProperties.getClient());
        
        CurrentPolicyResponseDto policies = memberService.getCurrentPolicies();
        model.addAttribute("policies", policies.getPolicies());

        return "member-auth/policy-acceptance";
    }

    @GetMapping("/guest-import")
    public String showGuestImportPage(
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Model model) {
        model.addAttribute("returnTo", sanitizeReturnTo(returnTo));
        model.addAttribute("firebaseConfig", firebaseProperties.getClient());
        return "member-auth/guest-import";
    }

    private String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return "/";
        }
        // Protect against open redirects: returnTo must start with "/" and not "//"
        if (returnTo.startsWith("/") && !returnTo.startsWith("//") && !returnTo.contains("\\")) {
            return returnTo;
        }
        return "/";
    }
}
