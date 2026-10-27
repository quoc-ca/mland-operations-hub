package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.ImageRateLimiter;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberPolicyAcceptance;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberPolicyAcceptanceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import edu.fpt.sep490_g22.ppswbs_backend.support.TestFirebaseTokenVerifier;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingPaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DesignRequestIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberPolicyAcceptanceRepository acceptanceRepository;

    @Autowired
    private WorkshopBookingRepository bookingRepository;

    @Autowired
    private TestFirebaseTokenVerifier tokenVerifier;

    @Autowired
    private ImageRateLimiter imageRateLimiter;

    private Member testMember;
    private WorkshopBooking testBooking;

    @BeforeEach
    void setUp() {
        tokenVerifier.clearTokens();

        testMember = memberRepository.save(Member.builder()
                .externalUserId("uid-member-test")
                .email("member-test@example.com")
                .emailVerified(true)
                .status(MemberStatus.ACTIVE)
                .build());

        imageRateLimiter.reset(testMember.getId());

        // Accept policy v1.0.0
        acceptanceRepository.save(MemberPolicyAcceptance.builder()
                .memberId(testMember.getId())
                .policyDocumentId(1L)
                .locale("vi")
                .correlationId("test-corr")
                .acceptedAt(Instant.now())
                .build());
        acceptanceRepository.save(MemberPolicyAcceptance.builder()
                .memberId(testMember.getId())
                .policyDocumentId(2L)
                .locale("vi")
                .correlationId("test-corr")
                .acceptedAt(Instant.now())
                .build());

        testBooking = bookingRepository.save(WorkshopBooking.builder()
                .bookingCode("WB-TEST-DESIGN-01")
                .memberId(testMember.getId())
                .packageId(1L)
                .sessionId(1L)
                .participantCount(1)
                .contactEmail("member-test@example.com")
                .canonicalEmail("member-test@example.com")
                .contactPhone("0912345678")
                .canonicalPhone("0912345678")
                .packageNameSnapshot("Basic Silver Ring Workshop")
                .priceSnapshot(750000L)
                .currencySnapshot("VND")
                .bookingStatus(BookingStatus.CONFIRMED)
                .paymentStatus(BookingPaymentStatus.DEPOSIT_PAID)
                .build());

        tokenVerifier.registerToken("token-member-test", "uid-member-test", "member-test@example.com", true);
    }

    @Test
    void whenUnauthenticated_thenReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "image-content".getBytes());

        mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .header("Idempotency-Key", "idemp-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenValidSubmission_thenReturns202WithCandidateFeatures() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "valid-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .param("designVersion", "1")
                        .header("Authorization", "Bearer token-member-test")
                        .header("Idempotency-Key", "idemp-test-01"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.analysisStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.reviewStatus").value("NEED_REVIEW"))
                .andExpect(jsonPath("$.candidateFeatures").isNotEmpty())
                .andExpect(jsonPath("$.imageQuality").isNotEmpty());
    }

    @Test
    void whenDuplicateIdempotencyKey_thenReturnsExistingWithoutConsumingRateLimit() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "valid-jpeg-bytes".getBytes());

        MvcResult first = mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .param("designVersion", "1")
                        .header("Authorization", "Bearer token-member-test")
                        .header("Idempotency-Key", "idemp-duplicate-test"))
                .andExpect(status().isAccepted())
                .andReturn();

        // Second call with same idempotency key
        mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .param("designVersion", "1")
                        .header("Authorization", "Bearer token-member-test")
                        .header("Idempotency-Key", "idemp-duplicate-test"))
                .andExpect(status().isAccepted());
    }

    @Test
    void whenImageRateLimitExceeded_thenReturns429WithRetryAfter() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "valid-jpeg-bytes".getBytes());

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(multipart("/api/v1/design-requests")
                            .file(file)
                            .param("bookingCode", "WB-TEST-DESIGN-01")
                            .param("consentVersion", "v1.0.0")
                            .header("Authorization", "Bearer token-member-test")
                            .header("Idempotency-Key", "idemp-rate-" + i))
                    .andExpect(status().isAccepted());
        }

        // 4th attempt in rolling 5-minute window must return 429
        mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .header("Authorization", "Bearer token-member-test")
                        .header("Idempotency-Key", "idemp-rate-4"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("IMAGE_RATE_LIMITED"));
    }

    @Test
    void whenMemberViewsOwnRequest_thenReturns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "valid-jpeg-bytes".getBytes());

        String responseBody = mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .header("Authorization", "Bearer token-member-test")
                        .header("Idempotency-Key", "idemp-view-test"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String requestId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody).get("id").asText();

        // Own view
        mockMvc.perform(get("/api/v1/design-requests/" + requestId)
                        .header("Authorization", "Bearer token-member-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId));
    }
}
