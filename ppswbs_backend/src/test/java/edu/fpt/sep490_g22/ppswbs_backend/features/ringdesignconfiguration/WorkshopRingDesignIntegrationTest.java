package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.GuestDesignSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDesignRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingMaterialRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingShapeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.GuestDesignSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesign;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingMaterial;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingShape;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.AttachDesignRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.CreateDesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.EngravingInputDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.SaveDesignVersionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.GuestDesignSessionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
public class WorkshopRingDesignIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuestDesignSessionService guestDesignSessionService;

    @Autowired
    private GuestDesignSessionRepository guestSessionRepository;

    @Autowired
    private RingDesignRepository ringDesignRepository;

    @Autowired
    private RingMaterialRepository ringMaterialRepository;

    @Autowired
    private RingShapeRepository ringShapeRepository;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    private final String memberToken = "token-member-ring-it";
    private final String memberUid = "uid-member-ring-it";
    private final Long memberAccountId = 4001L;

    private final String memberTokenB = "token-member-b-ring-it";
    private final String memberUidB = "uid-member-b-ring-it";
    private final Long memberAccountIdB = 4002L;

    @BeforeEach
    void setUp() {
        when(tokenVerificationService.verify(memberToken)).thenReturn(
                new VerifiedIdentity(memberUid, "member-ring@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(memberUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(memberUid)).thenReturn(Optional.of(
                new AccountSummary(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, "member-ring@mland.vn", Instant.now(), Instant.now())));

        when(tokenVerificationService.verify(memberTokenB)).thenReturn(
                new VerifiedIdentity(memberUidB, "member-b-ring@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(memberUidB, true)).thenReturn(Optional.of(
                new CurrentAccountActor(memberAccountIdB, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(memberUidB)).thenReturn(Optional.of(
                new AccountSummary(memberAccountIdB, ActorRole.MEMBER, ActorStatus.ACTIVE, "member-b-ring@mland.vn", Instant.now(), Instant.now())));

        if (ringMaterialRepository.findByMaterialCode("MAT-SILVER-925").isEmpty()) {
            ringMaterialRepository.save(RingMaterial.builder()
                    .materialCode("MAT-SILVER-925")
                    .materialName("Bạc Ý 925")
                    .unitPrice(new BigDecimal("250000.00"))
                    .currency("VND")
                    .difficultyScore(new BigDecimal("1.0000"))
                    .isActive(true)
                    .build());
        }
        if (ringMaterialRepository.findByMaterialCode("MAT-GOLD-14K").isEmpty()) {
            ringMaterialRepository.save(RingMaterial.builder()
                    .materialCode("MAT-GOLD-14K")
                    .materialName("Vàng 14K")
                    .unitPrice(new BigDecimal("1800000.00"))
                    .currency("VND")
                    .difficultyScore(new BigDecimal("2.5000"))
                    .isActive(true)
                    .build());
        }
        if (ringShapeRepository.findByShapeCode("SHP-CLASSIC-ROUND").isEmpty()) {
            ringShapeRepository.save(RingShape.builder()
                    .shapeCode("SHP-CLASSIC-ROUND")
                    .shapeName("Dáng tròn cổ điển")
                    .priceAdjustment(BigDecimal.ZERO)
                    .currency("VND")
                    .difficultyScore(new BigDecimal("1.0000"))
                    .isActive(true)
                    .build());
        }
    }

    @Test
    void whenGetOptions_thenReturns200WithOptionsSchema() throws Exception {
        mockMvc.perform(get("/api/v1/workshop-design-options")
                        .param("packageReference", "PKG-COUPLE")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageReference").value("PKG-COUPLE"));
    }

    @Test
    void whenCreateAndGetDesign_thenReturns201And200() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        String idempKey = "idemp-ring-" + UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designId").exists())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        Map<String, Object> map = objectMapper.readValue(responseJson, Map.class);
        String designId = (String) map.get("designId");

        // Retrieve design details
        mockMvc.perform(get("/api/v1/workshop-designs/" + designId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designId").value(designId));
    }

    @Test
    void whenArchiveAndRestoreDesign_thenLifecycleSucceeds() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        String idempKey = "idemp-ring-" + UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designId").exists())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        Map<String, Object> map = objectMapper.readValue(responseJson, Map.class);
        String designId = (String) map.get("designId");

        // Archive
        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/archive")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-archive-" + UUID.randomUUID())
                        .header("If-Match-Version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Restore
        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/restore")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-restore-" + UUID.randomUUID())
                        .header("If-Match-Version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAVED"));
    }

    @Test
    void whenGuestSessionExpiredAfter20Minutes_thenSessionExpired() {
        GuestDesignSession session = guestDesignSessionService.issueGuestSession("corr-test-expiry");
        // Simulate session inactivity past 20 minutes (expired in past)
        session.setExpiresAt(Instant.now().minusSeconds(60));
        guestSessionRepository.save(session);

        // Attempting to touch/resolve expired session throws sessionExpired
        org.junit.jupiter.api.Assertions.assertThrows(
                RingDesignException.class,
                () -> guestDesignSessionService.resolveAndTouchSession(session.getSessionReference())
        );
    }

    @Test
    void whenAnonymousCallsWorkshopDesigns_thenUnauthorized401() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Idempotency-Key", "idemp-anon-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenMemberATriesToAccessOrModifyMemberBDesign_thenAccessDenied403() throws Exception {
        // Member A creates a design
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-member-a-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String designId = (String) objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class).get("designId");

        // Member B attempts to view Member A's design -> 403 Forbidden
        mockMvc.perform(get("/api/v1/workshop-designs/" + designId)
                        .header("Authorization", "Bearer " + memberTokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DESIGN_ACCESS_DENIED"));

        // Member B attempts to save version on Member A's design -> 403 Forbidden
        SaveDesignVersionRequest saveReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-CLASSIC-ROUND")
                .ringSize("8")
                .thicknessMm(new BigDecimal("1.8"))
                .bandWidthMm(new BigDecimal("3.5"))
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build();

        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/versions")
                        .header("Authorization", "Bearer " + memberTokenB)
                        .header("Idempotency-Key", "idemp-hack-b-" + UUID.randomUUID())
                        .header("If-Match-Version", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DESIGN_ACCESS_DENIED"));
    }

    @Test
    void whenDesignCreatedAndEvaluated_thenPriceAndDifficultyAreServerAuthoritative() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-auth-price-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String designId = (String) objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class).get("designId");

        SaveDesignVersionRequest saveReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-CLASSIC-ROUND")
                .ringSize("10")
                .thicknessMm(new BigDecimal("1.8"))
                .bandWidthMm(new BigDecimal("3.5"))
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build();

        // Server calculates evaluation directly
        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/versions")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-save-auth-" + UUID.randomUUID())
                        .header("If-Match-Version", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.estimatedPrice").exists())
                .andExpect(jsonPath("$.evaluation.difficultyScore").exists())
                .andExpect(jsonPath("$.evaluation.rulesVersion").value("2026.11-v1.0"));
    }

    @Test
    void whenBranchLacksMaterial_thenAttachToBookingFailsWithBranchIncompatible() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-branch-check-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String designId = (String) objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class).get("designId");

        // Set design material to GOLD and status to SAVED
        RingDesign design = ringDesignRepository.findLatestByDesignReference(designId).orElseThrow();
        design.setMaterialReference("MAT-GOLD-18K");
        design.setStatus(RingDesignStatus.SAVED);
        ringDesignRepository.save(design);

        // Attempt attach to BRANCH-RESTRICTED-VANG
        AttachDesignRequestDto attachReq = AttachDesignRequestDto.builder()
                .bookingReference("BKG-BRANCH-001")
                .packageReference("PKG-COUPLE")
                .branchReference("BRANCH-RESTRICTED-VANG")
                .build();

        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/attach-to-booking")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-attach-branch-" + UUID.randomUUID())
                        .header("If-Match-Version", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DESIGN_BRANCH_INCOMPATIBLE"));
    }

    @Test
    void whenDesignAttachedToBooking_thenLockedAndResilientToRollback() throws Exception {
        CreateDesignRequest req = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE")
                .startMode("BLANK")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/workshop-designs")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-lock-test-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String designId = (String) objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class).get("designId");

        SaveDesignVersionRequest saveReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-COUPLE")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-CLASSIC-ROUND")
                .ringSize("8")
                .thicknessMm(new BigDecimal("1.8"))
                .bandWidthMm(new BigDecimal("3.5"))
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build();

        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/versions")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-save-before-lock-" + UUID.randomUUID())
                        .header("If-Match-Version", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveReq)))
                .andExpect(status().isOk());

        // Attach design to booking
        AttachDesignRequestDto attachReq = AttachDesignRequestDto.builder()
                .bookingReference("BKG-LOCK-001")
                .packageReference("PKG-COUPLE")
                .branchReference("BRANCH-HANOI-01")
                .build();

        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/attach-to-booking")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-attach-lock-" + UUID.randomUUID())
                        .header("If-Match-Version", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designReference").value(designId));

        // Verify status in DB is ATTACHED_TO_BOOKING
        RingDesign attached = ringDesignRepository.findLatestByDesignReference(designId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(RingDesignStatus.ATTACHED_TO_BOOKING, attached.getStatus());

        // Attempt to archive attached design -> 409 Conflict
        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/archive")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-archive-locked-" + UUID.randomUUID())
                        .header("If-Match-Version", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DESIGN_CANNOT_ARCHIVE"));

        // Attempt to re-attach already attached design -> 409 Conflict
        mockMvc.perform(post("/api/v1/workshop-designs/" + designId + "/attach-to-booking")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "idemp-reattach-locked-" + UUID.randomUUID())
                        .header("If-Match-Version", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DESIGN_NOT_VALIDATED"));

        // Verify data intactness: all design fields in DB preserved without data loss
        RingDesign preserved = ringDesignRepository.findLatestByDesignReference(designId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(preserved.getComponentSnapshot());
        org.junit.jupiter.api.Assertions.assertEquals("MAT-SILVER-925", preserved.getMaterialReference());
        org.junit.jupiter.api.Assertions.assertEquals("BKG-LOCK-001", preserved.getBookingReference());
    }
}
