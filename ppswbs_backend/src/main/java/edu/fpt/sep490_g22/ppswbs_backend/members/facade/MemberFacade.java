package edu.fpt.sep490_g22.ppswbs_backend.members.facade;

import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.MemberEntitlementDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.MemberService;
import edu.fpt.sep490_g22.ppswbs_backend.members.application.EntitlementEvaluator;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemberFacade {

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final EntitlementEvaluator entitlementEvaluator;

    public Optional<MemberDto> getMemberByUid(String uid) {
        return memberRepository.findByExternalUserId(uid)
                .map(m -> MemberDto.builder()
                        .id(m.getId())
                        .externalUserId(m.getExternalUserId())
                        .email(m.getEmail())
                        .emailVerified(m.isEmailVerified())
                        .status(m.getStatus())
                        .createdAt(m.getCreatedAt())
                        .build());
    }

    public MemberEntitlementDto getEntitlementByUid(String uid) {
        return memberService.getEntitlement(uid);
    }

    public void verifyMemberActiveEntitlement(String uid) {
        Member member = memberRepository.findByExternalUserId(uid)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        entitlementEvaluator.verifyActiveEntitlement(member);
    }
}
