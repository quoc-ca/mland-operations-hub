package edu.fpt.sep490_g22.ppswbs_backend.members;

import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import edu.fpt.sep490_g22.ppswbs_backend.support.TestFirebaseTokenVerifier;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingPaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.CreateBookingCommand;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;
import java.time.LocalDate;

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
    private WorkshopPackageRepository packageRepository;

    @Autowired
    private WorkshopSessionRepository sessionRepository;

    @Autowired
    private TestFirebaseTokenVerifier tokenVerifier;

    @BeforeEach
    void setUp() {
        tokenVerifier.clearTokens();
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

        WorkshopPackage pkg = packageRepository.save(WorkshopPackage.builder()
                .name("Test Package")
                .price(500000L)
                .currency("VND")
                .depositPercent(50)
                .minParticipants(1)
                .maxParticipants(10)
                .status("PUBLISHED")
                .build());

        WorkshopSession session = sessionRepository.save(WorkshopSession.builder()
                .packageId(pkg.getId())
                .sessionDate(LocalDate.now().plusDays(5))
                .capacity(10)
                .reservedParticipants(0)
                .status("OPEN")
                .build());

        CreateBookingCommand cmd = CreateBookingCommand.builder()
                .packageId(pkg.getId())
                .sessionId(session.getId())
                .participantCount(1)
                .contactEmail("history@example.com")
                .contactPhone("0901234567")
                .build();
        WorkshopBooking booking = bookingService.createGuestBooking(cmd);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(BookingPaymentStatus.DEPOSIT_PAID);
        bookingRepository.save(booking);

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
