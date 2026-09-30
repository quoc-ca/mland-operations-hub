package edu.fpt.sep490_g22.ppswbs_backend.members;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import edu.fpt.sep490_g22.ppswbs_backend.support.TestFirebaseTokenVerifier;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.CapturedBookingMailSender;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class GuestBookingImportIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkshopBookingService bookingService;

    @Autowired
    private WorkshopBookingRepository bookingRepository;

    @Autowired
    private CapturedBookingMailSender mailSender;

    @Autowired
    private TestFirebaseTokenVerifier tokenVerifier;

    @BeforeEach
    void setUp() {
        tokenVerifier.clearTokens();
        mailSender.clear();
    }

    @Test
    void whenEmailUnverified_thenGuestImportPreviewIsForbidden() throws Exception {
        tokenVerifier.registerToken("token-unverified-import", "uid-unverified-import", "unverified@example.com", false);

        mockMvc.perform(put("/api/v1/members/me")
                .header("Authorization", "Bearer token-unverified-import"));

        mockMvc.perform(get("/api/v1/members/me/guest-booking-import-preview")
                        .header("Authorization", "Bearer token-unverified-import"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_UNVERIFIED"));
    }

    @Test
    void whenVerifiedMemberImportsEligibleBookings_thenLinksSuccessfullyAndIdempotently() throws Exception {
        tokenVerifier.registerToken("token-import-user", "uid-import-user", "history@example.com", true);

        // Provision Member
        mockMvc.perform(put("/api/v1/members/me")
                .header("Authorization", "Bearer token-import-user"));

        // Accept Policy
        mockMvc.perform(post("/api/v1/members/me/policy-acceptances")
                .header("Authorization", "Bearer token-import-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "termsVersion": "v1.0.0", "privacyVersion": "v1.0.0" }
                        """));

        // Create confirmed Guest booking for matching email
        WorkshopBooking booking = bookingService.createGuestBooking("history@example.com");
        String rawToken = mailSender.getLastSentToken(booking.getBookingCode());
        bookingService.confirmBookingEmail(booking.getBookingCode(), rawToken);

        // Preview should show 1 candidate
        mockMvc.perform(get("/api/v1/members/me/guest-booking-import-preview")
                        .header("Authorization", "Bearer token-import-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.candidates[0].bookingCode").value(booking.getBookingCode()));

        // Confirm import
        mockMvc.perform(post("/api/v1/members/me/guest-booking-imports")
                        .header("Authorization", "Bearer token-import-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCount").value(1));

        // Verify booking has member_id linked
        Member member = memberRepository.findByExternalUserId("uid-import-user").orElseThrow();
        WorkshopBooking updated = bookingRepository.findByBookingCode(booking.getBookingCode()).orElseThrow();
        assertEquals(member.getId(), updated.getMemberId());

        // Second import produces 0 new links
        mockMvc.perform(post("/api/v1/members/me/guest-booking-imports")
                        .header("Authorization", "Bearer token-import-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCount").value(0));
    }
}
