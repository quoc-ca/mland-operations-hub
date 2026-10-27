package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PriceProposalIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    @BeforeEach
    void setUp() {
        // Manager
        when(tokenVerificationService.verify("token-manager-pricing")).thenReturn(
                new VerifiedIdentity("uid-manager-pricing", "manager@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor("uid-manager-pricing", true)).thenReturn(Optional.of(
                new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of())));

        // Admin
        when(tokenVerificationService.verify("token-admin-pricing")).thenReturn(
                new VerifiedIdentity("uid-admin-pricing", "admin@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor("uid-admin-pricing", true)).thenReturn(Optional.of(
                new CurrentAccountActor(11L, ActorRole.ADMIN, ActorStatus.ACTIVE, true, List.of())));

        // Staff
        when(tokenVerificationService.verify("token-staff-pricing")).thenReturn(
                new VerifiedIdentity("uid-staff-pricing", "staff@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor("uid-staff-pricing", true)).thenReturn(Optional.of(
                new CurrentAccountActor(12L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of())));

        // Member
        when(tokenVerificationService.verify("token-member-pricing")).thenReturn(
                new VerifiedIdentity("uid-member-pricing", "member@example.com", true, Instant.now()));
        when(accountFacade.resolveActor("uid-member-pricing", true)).thenReturn(Optional.of(
                new CurrentAccountActor(13L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
    }

    @Test
    void whenManagerRequestsPriceSuggestion_thenReturns200WithAdvisoryFlag() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packageName": "Workshop Nhẫn Sáp Đặc Biệt",
                                  "durationHours": 3,
                                  "rawMaterialCost": 350000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").value(proposalId))
                .andExpect(jsonPath("$.advisory").value(true))
                .andExpect(jsonPath("$.suggestionStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.suggestedPrice").value(850000.0))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.providerCorrelationId").isNotEmpty())
                .andExpect(jsonPath("$.technicalAuditEvidenceOnly").value(false));
    }

    @Test
    void whenAdminAttemptsRequestPriceSuggestion_thenReturns403Forbidden() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .header("Authorization", "Bearer token-admin-pricing"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
    }

    @Test
    void whenStaffAttemptsPriceSuggestion_thenReturns403Forbidden() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .header("Authorization", "Bearer token-staff-pricing"))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenMemberAttemptsPriceSuggestion_thenReturns403Forbidden() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .header("Authorization", "Bearer token-member-pricing"))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenGuestAttemptsPriceSuggestion_thenReturns401Unauthorized() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenInvalidTargetType_thenReturns422FeatureNotApplicable() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "CUSTOM_MANUFACTURING_ORDER")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AI_FEATURE_NOT_APPLICABLE"));
    }

    @Test
    void whenManagerViewsPriceSuggestion_thenReturnsFullSuggestionWithPrices() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        // 1. Request suggestion
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        // 2. View suggestion as Manager
        mockMvc.perform(get("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").value(proposalId))
                .andExpect(jsonPath("$.suggestedPrice").value(850000.0))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.technicalAuditEvidenceOnly").value(false));
    }

    @Test
    void whenAdminViewsPriceSuggestion_thenReturnsTechnicalAuditEvidenceOnly() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        // 1. Manager requests suggestion
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        // 2. Admin views suggestion (Technical/Audit evidence only: no prices or commercial explanations)
        mockMvc.perform(get("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .header("Authorization", "Bearer token-admin-pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").value(proposalId))
                .andExpect(jsonPath("$.targetType").value("WORKSHOP_PACKAGE"))
                .andExpect(jsonPath("$.suggestionStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerCorrelationId").isNotEmpty())
                .andExpect(jsonPath("$.suggestedPrice").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.finalPrice").doesNotExist())
                .andExpect(jsonPath("$.managerDecision").doesNotExist())
                .andExpect(jsonPath("$.technicalAuditEvidenceOnly").value(true));
    }

    @Test
    void whenStaffViewsPriceSuggestion_thenReturns403Forbidden() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .header("Authorization", "Bearer token-staff-pricing"))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenMemberViewsPriceSuggestion_thenReturns403Forbidden() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .header("Authorization", "Bearer token-member-pricing"))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenGuestViewsPriceSuggestion_thenReturns401Unauthorized() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/price-proposals/" + proposalId + "/ai-suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenManagerAcceptsPriceSuggestion_thenRecordsDecisionAndSetsFinalPrice() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        // 1. Request suggestion
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        // 2. Manager accepts suggestion
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions/decisions")
                        .header("Authorization", "Bearer token-manager-pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").value(proposalId))
                .andExpect(jsonPath("$.managerDecision").value("ACCEPTED"))
                .andExpect(jsonPath("$.finalPrice").value(850000.0))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void whenManagerAdjustsPriceSuggestion_thenRecordsDecisionAndCustomFinalPrice() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        // 1. Request suggestion
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        // 2. Manager adjusts price to 950000
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions/decisions")
                        .header("Authorization", "Bearer token-manager-pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ADJUSTED",
                                  "finalPrice": 950000.0,
                                  "note": "Điều chỉnh theo biến động giá phôi bạc"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerDecision").value("ADJUSTED"))
                .andExpect(jsonPath("$.finalPrice").value(950000.0));
    }

    @Test
    void whenManagerRejectsPriceSuggestion_thenRecordsDecisionAndNullFinalPrice() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        // 1. Request suggestion
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        // 2. Manager rejects suggestion
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions/decisions")
                        .header("Authorization", "Bearer token-manager-pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECTED",
                                  "note": "Mức giá AI đề xuất không phản ánh đúng định vị cao cấp của sản phẩm"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerDecision").value("REJECTED"))
                .andExpect(jsonPath("$.finalPrice").doesNotExist());
    }

    @Test
    void whenAdminOrStaffAttemptsToDecidePriceSuggestion_thenReturns403Forbidden() throws Exception {
        String proposalId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions")
                        .param("targetType", "WORKSHOP_PACKAGE")
                        .header("Authorization", "Bearer token-manager-pricing"))
                .andExpect(status().isOk());

        // Admin attempts decision
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions/decisions")
                        .header("Authorization", "Bearer token-admin-pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "ACCEPTED" }
                                """))
                .andExpect(status().isForbidden());

        // Staff attempts decision
        mockMvc.perform(post("/api/v1/price-proposals/" + proposalId + "/ai-suggestions/decisions")
                        .header("Authorization", "Bearer token-staff-pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "ACCEPTED" }
                                """))
                .andExpect(status().isForbidden());
    }
}
