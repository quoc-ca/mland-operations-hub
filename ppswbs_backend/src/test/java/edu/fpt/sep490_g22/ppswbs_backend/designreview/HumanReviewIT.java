package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AnalysisStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.BookingDesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.BookingDesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.Member;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberStatus;
import edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure.MemberRepository;
import edu.fpt.sep490_g22.ppswbs_backend.support.TestFirebaseTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class HumanReviewIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingDesignRequestRepository requestRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestFirebaseTokenVerifier tokenVerifier;

    private String lowValueRequestId;
    private String highValueRequestId;

    @BeforeEach
    void setUp() {
        tokenVerifier.clearTokens();

        Member customer = memberRepository.save(Member.builder()
                .externalUserId("uid-customer-01")
                .email("customer@example.com")
                .status(MemberStatus.ACTIVE)
                .build());

        // Low value request: 1,800,000 VND (<= 3M, Staff allowed)
        lowValueRequestId = UUID.randomUUID().toString();
        requestRepository.save(BookingDesignRequest.builder()
                .id(lowValueRequestId)
                .memberId(customer.getId())
                .bookingCode("WB-LOW-VAL-01")
                .designVersion(1)
                .idempotencyKey("idemp-low-01")
                .analysisStatus(AnalysisStatus.SUCCEEDED)
                .reviewStatus(ReviewStatus.NEED_REVIEW)
                .estimatedValue(1_800_000L)
                .nextAction("AWAITING_REVIEW")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // High value request: 4,500,000 VND (> 3M, Manager only)
        highValueRequestId = UUID.randomUUID().toString();
        requestRepository.save(BookingDesignRequest.builder()
                .id(highValueRequestId)
                .memberId(customer.getId())
                .bookingCode("WB-HIGH-VAL-01")
                .designVersion(1)
                .idempotencyKey("idemp-high-01")
                .analysisStatus(AnalysisStatus.SUCCEEDED)
                .reviewStatus(ReviewStatus.NEED_REVIEW)
                .estimatedValue(4_500_000L)
                .nextAction("AWAITING_REVIEW")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        tokenVerifier.registerToken("token-staff-01", "uid-staff-01", "staff@mland.vn", true, "STAFF");
        tokenVerifier.registerToken("token-manager-01", "uid-manager-01", "manager@mland.vn", true, "OWNER");
    }

    @Test
    void whenStaffViewsQueue_thenOnlyLowValueRequestsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/design-review-requests")
                        .header("Authorization", "Bearer token-staff-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + lowValueRequestId + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + highValueRequestId + "')]").doesNotExist());
    }

    @Test
    void whenManagerViewsQueue_thenAllRequestsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/design-review-requests")
                        .header("Authorization", "Bearer token-manager-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + lowValueRequestId + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + highValueRequestId + "')]").exists());
    }

    @Test
    void whenStaffReviewsLowValueRequest_thenSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/design-review-requests/" + lowValueRequestId + "/decisions")
                        .header("Authorization", "Bearer token-staff-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPTED",
                                  "designVersion": 1,
                                  "reason": "Thiết kế nhẫn bạc trơn đạt yêu cầu kỹ thuật và phù hợp với buổi workshop."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.nextAction").value("PROCEED_TO_BOOKING"));
    }

    @Test
    void whenStaffAttemptsToReviewHighValueRequest_thenReturns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/design-review-requests/" + highValueRequestId + "/decisions")
                        .header("Authorization", "Bearer token-staff-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPTED",
                                  "designVersion": 1,
                                  "reason": "Nhân viên phê duyệt"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_AUTHORIZED"));
    }

    @Test
    void whenManagerReviewsHighValueRequest_thenSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/design-review-requests/" + highValueRequestId + "/decisions")
                        .header("Authorization", "Bearer token-manager-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPTED",
                                  "designVersion": 1,
                                  "reason": "Quản lý đã phê duyệt mẫu sáp đúc cao cấp."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("ACCEPTED"));
    }

    @Test
    void whenClarificationRequiredRecorded_thenNewVersionRequired() throws Exception {
        mockMvc.perform(post("/api/v1/design-review-requests/" + lowValueRequestId + "/decisions")
                        .header("Authorization", "Bearer token-staff-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "CLARIFICATION_REQUIRED",
                                  "designVersion": 1,
                                  "reason": "Vui lòng chụp thêm góc ngang của nhẫn để xác định độ dày thành nhẫn."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("CLARIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.nextAction").value("NEW_VERSION_REQUIRED"));
    }

    @Test
    void whenStaleDesignVersionProvided_thenReturns409Conflict() throws Exception {
        mockMvc.perform(post("/api/v1/design-review-requests/" + lowValueRequestId + "/decisions")
                        .header("Authorization", "Bearer token-staff-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPTED",
                                  "designVersion": 99,
                                  "reason": "Phiên bản cũ"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DESIGN_VERSION_STALE"));
    }
}
