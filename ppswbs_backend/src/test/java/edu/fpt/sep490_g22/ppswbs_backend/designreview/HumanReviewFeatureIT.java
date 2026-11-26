package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class HumanReviewFeatureIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    private final String staffToken = "token-staff-human-review";
    private final String staffUid = "uid-staff-001";
    private final Long staffAccountId = 2001L;

    private final String managerToken = "token-manager-human-review";
    private final String managerUid = "uid-manager-001";
    private final Long managerAccountId = 3001L;

    private final String memberToken = "token-member-human-review";
    private final String memberUid = "uid-member-001";
    private final Long memberAccountId = 1001L;

    @BeforeEach
    void setUp() {
        // Member
        when(tokenVerificationService.verify(memberToken)).thenReturn(
                new VerifiedIdentity(memberUid, "member@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(memberUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(memberUid)).thenReturn(Optional.of(
                new AccountSummary(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@mland.vn", Instant.now(), Instant.now())));

        // Staff
        when(tokenVerificationService.verify(staffToken)).thenReturn(
                new VerifiedIdentity(staffUid, "staff@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(staffUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(staffAccountId, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(staffUid)).thenReturn(Optional.of(
                new AccountSummary(staffAccountId, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.vn", Instant.now(), Instant.now())));

        // Manager
        when(tokenVerificationService.verify(managerToken)).thenReturn(
                new VerifiedIdentity(managerUid, "manager@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(managerUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(managerAccountId, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(managerUid)).thenReturn(Optional.of(
                new AccountSummary(managerAccountId, ActorRole.MANAGER, ActorStatus.ACTIVE, "manager@mland.vn", Instant.now(), Instant.now())));
    }

    @Test
    void guestSubmissionIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/v1/design-requests")
                        .param("description", "Nhẫn đôi bạc khắc họa tiết hoa sen")
                        .header("Idempotency-Key", "idemp-guest-01")
                        .header("X-Guest-Design-Session", "guest-session-test-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCanReviewAndAccept_withMandatoryExplanation() throws Exception {
        // Create a request
        MvcResult createResult = mockMvc.perform(multipart("/api/v1/design-requests")
                        .param("description", "Mẫu nhẫn hoa văn cổ điển")
                        .header("Idempotency-Key", "idemp-staff-review-01")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isAccepted())
                .andReturn();

        String requestId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Staff sees in review queue
        mockMvc.perform(get("/api/v1/design-review-requests")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        // Decision without explanation -> 400
        DesignReviewDtos.ReviewDecisionRequest emptyReasonReq = new DesignReviewDtos.ReviewDecisionRequest();
        emptyReasonReq.setDecision("ACCEPTED");
        emptyReasonReq.setExplanation("");
        emptyReasonReq.setRequestVersion(1);

        mockMvc.perform(post("/api/v1/design-review-requests/" + requestId + "/decisions")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReasonReq)))
                .andExpect(status().isBadRequest());

        // Decision with valid explanation -> 200 ACCEPTED
        DesignReviewDtos.ReviewDecisionRequest validReq = new DesignReviewDtos.ReviewDecisionRequest();
        validReq.setDecision("ACCEPTED");
        validReq.setExplanation("Thiết kế rất khả thi, thợ kim hoàn có thể chế tác với chất liệu bạc 925 theo đúng tỷ lệ.");
        validReq.setRequestVersion(1);

        mockMvc.perform(post("/api/v1/design-review-requests/" + requestId + "/decisions")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.reviewStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.decisionExplanation").value("Thiết kế rất khả thi, thợ kim hoàn có thể chế tác với chất liệu bạc 925 theo đúng tỷ lệ."));

        // Terminal conflict on second decision -> 409
        mockMvc.perform(post("/api/v1/design-review-requests/" + requestId + "/decisions")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DESIGN_REVIEW_ALREADY_DECIDED"));
    }

    @Test
    void managerCanReviewAndReject_withMandatoryExplanation() throws Exception {
        MvcResult createResult = mockMvc.perform(multipart("/api/v1/design-requests")
                        .param("description", "Mẫu nhẫn đính đá siêu mỏng 0.2mm")
                        .header("Idempotency-Key", "idemp-mgr-review-01")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isAccepted())
                .andReturn();

        String requestId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        DesignReviewDtos.ReviewDecisionRequest rejectReq = new DesignReviewDtos.ReviewDecisionRequest();
        rejectReq.setDecision("REJECTED");
        rejectReq.setExplanation("Độ dày 0.2mm không đảm bảo độ cứng vững của nhẫn kim loại quý, dễ bị móp méo khi đính đá.");
        rejectReq.setRequestVersion(1);

        mockMvc.perform(post("/api/v1/design-review-requests/" + requestId + "/decisions")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionExplanation").value("Độ dày 0.2mm không đảm bảo độ cứng vững của nhẫn kim loại quý, dễ bị móp méo khi đính đá."));
    }
}
