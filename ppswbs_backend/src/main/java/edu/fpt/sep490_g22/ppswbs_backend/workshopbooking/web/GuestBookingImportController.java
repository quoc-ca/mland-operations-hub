package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.ApiException;
import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidTokenException;
import edu.fpt.sep490_g22.ppswbs_backend.configuration.FirebaseAuthenticationToken;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberDto;
import edu.fpt.sep490_g22.ppswbs_backend.members.facade.MemberFacade;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.facade.WorkshopBookingFacade;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class GuestBookingImportController {

    private final MemberFacade memberFacade;
    private final WorkshopBookingFacade bookingFacade;

    @Data
    @Builder
    public static class GuestBookingImportPreviewDto {
        private String memberEmail;
        private boolean emailVerified;
        private int candidateCount;
        private List<CandidateBookingDto> candidates;

        @Data
        @Builder
        public static class CandidateBookingDto {
            private Long id;
            private String bookingCode;
            private String contactEmail;
            private String bookingStatus;
            private String createdAt;
        }
    }

    @Data
    @Builder
    public static class GuestBookingImportResponseDto {
        private int linkedCount;
        private int skippedCount;
    }

    @GetMapping("/guest-booking-import-preview")
    public ResponseEntity<GuestBookingImportPreviewDto> previewGuestBookingImport() {
        FirebaseAuthenticationToken auth = getAuthToken();
        MemberDto member = getVerifiedMember(auth.getUid());

        List<WorkshopBookingFacade.WorkshopBookingDto> candidates = bookingFacade.getEligibleGuestBookingsForImport(member.getEmail());

        List<GuestBookingImportPreviewDto.CandidateBookingDto> candidateDtos = candidates.stream()
                .map(c -> GuestBookingImportPreviewDto.CandidateBookingDto.builder()
                        .id(c.getId())
                        .bookingCode(c.getBookingCode())
                        .contactEmail(c.getContactEmail())
                        .bookingStatus(c.getBookingStatus())
                        .createdAt(c.getCreatedAt().toString())
                        .build())
                .collect(Collectors.toList());

        GuestBookingImportPreviewDto preview = GuestBookingImportPreviewDto.builder()
                .memberEmail(member.getEmail())
                .emailVerified(member.isEmailVerified())
                .candidateCount(candidateDtos.size())
                .candidates(candidateDtos)
                .build();

        return ResponseEntity.ok(preview);
    }

    @PostMapping("/guest-booking-imports")
    public ResponseEntity<GuestBookingImportResponseDto> confirmGuestBookingImport() {
        FirebaseAuthenticationToken auth = getAuthToken();
        MemberDto member = getVerifiedMember(auth.getUid());

        List<WorkshopBookingFacade.WorkshopBookingDto> candidates = bookingFacade.getEligibleGuestBookingsForImport(member.getEmail());
        List<Long> ids = candidates.stream().map(WorkshopBookingFacade.WorkshopBookingDto::getId).collect(Collectors.toList());

        int linkedCount = bookingFacade.linkGuestBookingsToMember(ids, member.getId());

        GuestBookingImportResponseDto response = GuestBookingImportResponseDto.builder()
                .linkedCount(linkedCount)
                .skippedCount(candidates.size() - linkedCount)
                .build();

        return ResponseEntity.ok(response);
    }

    private MemberDto getVerifiedMember(String uid) {
        MemberDto member = memberFacade.getMemberByUid(uid)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Member not found", HttpStatus.NOT_FOUND));

        if (!member.isEmailVerified()) {
            throw new ApiException("EMAIL_UNVERIFIED", "Email verification required before importing guest bookings", HttpStatus.FORBIDDEN);
        }
        memberFacade.verifyMemberActiveEntitlement(uid);
        return member;
    }

    private FirebaseAuthenticationToken getAuthToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof FirebaseAuthenticationToken token && token.isAuthenticated()) {
            return token;
        }
        throw new InvalidTokenException("Invalid or missing authentication context");
    }
}
