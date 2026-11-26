package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.AiPromptQuotaUsageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 004 2.1: dropping the Guest session header does not buy unlimited prompts. */
@SpringBootTest(properties = "ai.support.quota.guest-global-daily=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiGuestQuotaPoolIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiPromptQuotaUsageRepository quotaRepository;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    @BeforeEach
    @AfterEach
    void clearQuota() {
        quotaRepository.deleteAll();
    }

    @Test
    void newGuestSessionsShareOneDailyPool() throws Exception {
        for (int i = 0; i < 4; i++) {
            ask(question()).andExpect(status().isOk()).andExpect(jsonPath("$.quota.remaining").value(2));
        }

        ask(question())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_DAILY_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.quota.actor").value("GUEST"));
    }

    @Test
    void anExhaustedSessionDoesNotDrainThePool() throws Exception {
        String session = ask(question()).andReturn().getResponse().getHeader("X-Guest-AI-Session");
        for (int i = 0; i < 2; i++) {
            ask(question().header("X-Guest-AI-Session", session)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            ask(question().header("X-Guest-AI-Session", session)).andExpect(status().isTooManyRequests());
        }

        ask(question()).andExpect(status().isOk());
    }

    @Test
    void membersAreNotAffectedByAnEmptyPool() throws Exception {
        for (int i = 0; i < 4; i++) {
            ask(question()).andExpect(status().isOk());
        }
        when(tokenVerificationService.verify("member")).thenReturn(
                new VerifiedIdentity("uid-pool-member", "member@example.test", true, Instant.now()));
        when(accountFacade.resolveActor("uid-pool-member", true)).thenReturn(Optional.of(
                new CurrentAccountActor(8801L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));

        ask(question().header("Authorization", "Bearer member")).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder question() {
        return post("/api/v1/ai-support/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Workshop mở cửa lúc mấy giờ?\"}");
    }

    private ResultActions ask(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }
}
