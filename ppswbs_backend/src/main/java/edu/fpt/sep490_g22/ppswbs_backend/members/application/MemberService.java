package edu.fpt.sep490_g22.ppswbs_backend.members.application;

import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEventWriter;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidRequestException;
import edu.fpt.sep490_g22.ppswbs_backend.common.ResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberPolicyAcceptanceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.fpt.sep490_g22.ppswbs_backend.common.ApiException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final MemberRepository memberRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final MemberPolicyAcceptanceRepository acceptanceRepository;
    private final EntitlementEvaluator entitlementEvaluator;
    private final AuditEventWriter auditEventWriter;

    public record ProvisionResult(MemberDto member, boolean isNew) {}

    @Transactional
    public ProvisionResult provisionOrReturnMember(String uid, String email, boolean emailVerified) {
        Optional<Member> existingOpt = memberRepository.findByExternalUserId(uid);
        if (existingOpt.isPresent()) {
            Member member = existingOpt.get();
            // Update email / verified flag if changed from identity provider
            boolean updated = false;
            if (email != null && !email.equalsIgnoreCase(member.getEmail())) {
                member.setEmail(email);
                updated = true;
            }
            if (emailVerified != member.isEmailVerified()) {
                member.setEmailVerified(emailVerified);
                updated = true;
            }
            if (updated) {
                memberRepository.save(member);
            }

            auditEventWriter.writeEvent(AuditEvent.builder()
                    .memberId(member.getId())
                    .eventType("MEMBER_SIGNED_IN")
                    .occurredAt(Instant.now())
                    .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                    .safeMetadata("{\"status\":\"" + member.getStatus() + "\"}")
                    .build());

            return new ProvisionResult(toDto(member), false);
        }

        Member newMember = Member.builder()
                .externalUserId(uid)
                .email(email)
                .emailVerified(emailVerified)
                .status(MemberStatus.PENDING_POLICY_ACCEPTANCE)
                .build();
        newMember = memberRepository.save(newMember);

        auditEventWriter.writeEvent(AuditEvent.builder()
                .memberId(newMember.getId())
                .eventType("MEMBER_PROVISIONED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"status\":\"PENDING_POLICY_ACCEPTANCE\"}")
                .build());

        return new ProvisionResult(toDto(newMember), true);
    }

    @Transactional(readOnly = true)
    public CurrentPolicyResponseDto getCurrentPolicies() {
        List<PolicyDocument> effectivePolicies = policyDocumentRepository.findByState(PolicyDocumentState.EFFECTIVE);
        List<CurrentPolicyResponseDto.PolicyItemDto> items = effectivePolicies.stream()
                .map(pd -> CurrentPolicyResponseDto.PolicyItemDto.builder()
                        .policyType(pd.getPolicyType().name())
                        .version(pd.getVersion())
                        .contentUri(pd.getContentUri())
                        .effectiveAt(pd.getEffectiveAt().toString())
                        .build())
                .collect(Collectors.toList());

        return CurrentPolicyResponseDto.builder()
                .policies(items)
                .build();
    }

    @Transactional
    public MemberEntitlementDto acceptPolicies(String uid, PolicyAcceptanceRequestDto request, String locale) {
        Member member = memberRepository.findByExternalUserId(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Member identity not found"));

        PolicyDocument termsDoc = policyDocumentRepository.findByPolicyTypeAndVersion(PolicyType.TERMS_OF_USE, request.getTermsVersion())
                .orElseThrow(() -> new InvalidRequestException("Invalid or non-existent Terms version: " + request.getTermsVersion()));

        PolicyDocument privacyDoc = policyDocumentRepository.findByPolicyTypeAndVersion(PolicyType.PRIVACY_POLICY, request.getPrivacyVersion())
                .orElseThrow(() -> new InvalidRequestException("Invalid or non-existent Privacy version: " + request.getPrivacyVersion()));

        if (termsDoc.getState() != PolicyDocumentState.EFFECTIVE || privacyDoc.getState() != PolicyDocumentState.EFFECTIVE) {
            throw new InvalidRequestException("Submitted policy versions are not currently effective");
        }

        recordAcceptanceIfMissing(member.getId(), termsDoc.getId(), locale);
        recordAcceptanceIfMissing(member.getId(), privacyDoc.getId(), locale);

        if (member.getStatus() == MemberStatus.PENDING_POLICY_ACCEPTANCE) {
            member.setStatus(MemberStatus.ACTIVE);
            memberRepository.save(member);
        }

        auditEventWriter.writeEvent(AuditEvent.builder()
                .memberId(member.getId())
                .eventType("POLICY_ACCEPTED")
                .occurredAt(Instant.now())
                .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                .safeMetadata("{\"terms\":\"" + request.getTermsVersion() + "\",\"privacy\":\"" + request.getPrivacyVersion() + "\"}")
                .build());

        return entitlementEvaluator.evaluate(member);
    }

    @Transactional(readOnly = true)
    public MemberEntitlementDto getEntitlement(String uid) {
        Member member = memberRepository.findByExternalUserId(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Member identity not found"));
        return entitlementEvaluator.evaluate(member);
    }

    private void recordAcceptanceIfMissing(Long memberId, Long policyDocId, String locale) {
        Optional<MemberPolicyAcceptance> existing = acceptanceRepository.findByMemberIdAndPolicyDocumentId(memberId, policyDocId);
        if (existing.isEmpty()) {
            MemberPolicyAcceptance mpa = MemberPolicyAcceptance.builder()
                    .memberId(memberId)
                    .policyDocumentId(policyDocId)
                    .locale(locale != null ? locale : "vi")
                    .correlationId(CorrelationIdFilter.getCurrentCorrelationId())
                    .build();
            acceptanceRepository.save(mpa);
        }
    }

    private MemberDto toDto(Member member) {
        return MemberDto.builder()
                .id(member.getId())
                .externalUserId(member.getExternalUserId())
                .email(member.getEmail())
                .emailVerified(member.isEmailVerified())
                .status(member.getStatus())
                .createdAt(member.getCreatedAt())
                .build();
    }
}

