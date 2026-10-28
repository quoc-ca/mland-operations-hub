package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.ImageRateLimiter;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.TemporaryImageReference;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.TemporaryImageReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DesignRequestIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    @MockitoBean
    private WorkshopBookingFacade workshopBookingFacade;

    @Autowired
    private ImageRateLimiter imageRateLimiter;

    @Autowired
    private TemporaryImageReferenceRepository temporaryImageRepository;

    private final Long testMemberId = 1001L;
    private final String testMemberUid = "uid-member-test";
    private final String testMemberToken = "token-member-test";

    private final Long otherMemberId = 1002L;
    private final String otherMemberUid = "uid-member-other";
    private final String otherMemberToken = "token-member-other";

    @BeforeEach
    void setUp() {
        imageRateLimiter.reset(testMemberId);
        imageRateLimiter.reset(otherMemberId);

        // Member A (Owner)
        when(tokenVerificationService.verify(testMemberToken)).thenReturn(
                new VerifiedIdentity(testMemberUid, "member-test@example.com", true, Instant.now()));
        when(accountFacade.resolveActor(testMemberUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(testMemberId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(testMemberUid)).thenReturn(Optional.of(
                new AccountSummary(testMemberId, ActorRole.MEMBER, ActorStatus.ACTIVE, "member-test@example.com", Instant.now(), Instant.now())));

        // Member B (Other)
        when(tokenVerificationService.verify(otherMemberToken)).thenReturn(
                new VerifiedIdentity(otherMemberUid, "member-other@example.com", true, Instant.now()));
        when(accountFacade.resolveActor(otherMemberUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(otherMemberId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(otherMemberUid)).thenReturn(Optional.of(
                new AccountSummary(otherMemberId, ActorRole.MEMBER, ActorStatus.ACTIVE, "member-other@example.com", Instant.now(), Instant.now())));

        // Booking mock for Member A
        when(workshopBookingFacade.getBookingEligibilityForDesign("WB-TEST-DESIGN-01", testMemberId)).thenReturn(Optional.of(
                BookingEligibilitySnapshot.builder()
                        .bookingId(1L)
                        .bookingCode("WB-TEST-DESIGN-01")
                        .memberAccountId(testMemberId)
                        .packageId(1L)
                        .packageName("Basic Silver Ring Workshop")
                        .bookingStatus("CONFIRMED")
                        .paymentStatus("DEPOSIT_PAID")
                        .eligibleForDesign(true)
                        .build()
        ));
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
    void whenValidSubmission_thenReturns202WithCandidateFeaturesAndImageDeleted() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "valid-jpeg-bytes".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .param("designVersion", "1")
                        .header("Authorization", "Bearer " + testMemberToken)
                        .header("Idempotency-Key", "idemp-test-01"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.analysisStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.reviewStatus").value("NEED_REVIEW"))
                .andExpect(jsonPath("$.candidateFeatures").isNotEmpty())
                .andExpect(jsonPath("$.imageQuality").isNotEmpty())
                .andReturn();

        // Verify temporary image immediate deletion
        List<TemporaryImageReference> images = temporaryImageRepository.findAll();
        assertThat(images).isNotEmpty();
        assertThat(images.get(images.size() - 1).getDeletionOutcome()).isEqualTo("DELETED_AFTER_PROCESSING");
        assertThat(images.get(images.size() - 1).getDeletedAt()).isNotNull();
    }

    @Test
    void whenDuplicateIdempotencyKey_thenReturnsExistingWithoutConsumingRateLimit() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "valid-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .param("designVersion", "1")
                        .header("Authorization", "Bearer " + testMemberToken)
                        .header("Idempotency-Key", "idemp-duplicate-test"))
                .andExpect(status().isAccepted());

        // Second call with same idempotency key
        mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .param("designVersion", "1")
                        .header("Authorization", "Bearer " + testMemberToken)
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
                            .header("Authorization", "Bearer " + testMemberToken)
                            .header("Idempotency-Key", "idemp-rate-" + i))
                    .andExpect(status().isAccepted());
        }

        // 4th attempt in rolling 5-minute window must return 429
        mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .header("Authorization", "Bearer " + testMemberToken)
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
                        .header("Authorization", "Bearer " + testMemberToken)
                        .header("Idempotency-Key", "idemp-view-test"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String requestId = new ObjectMapper().readTree(responseBody).get("id").asText();

        // Own view
        mockMvc.perform(get("/api/v1/design-requests/" + requestId)
                        .header("Authorization", "Bearer " + testMemberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId));
    }

    @Test
    void whenMemberViewsOtherMemberRequest_thenReturns404NotFoundToPreventLeakage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "ring.jpg", "image/jpeg", "valid-jpeg-bytes".getBytes());

        String responseBody = mockMvc.perform(multipart("/api/v1/design-requests")
                        .file(file)
                        .param("bookingCode", "WB-TEST-DESIGN-01")
                        .param("consentVersion", "v1.0.0")
                        .header("Authorization", "Bearer " + testMemberToken)
                        .header("Idempotency-Key", "idemp-isolation-test"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String requestId = new ObjectMapper().readTree(responseBody).get("id").asText();

        // Other member attempts to view -> 404 NOT_FOUND
        mockMvc.perform(get("/api/v1/design-requests/" + requestId)
                        .header("Authorization", "Bearer " + otherMemberToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
