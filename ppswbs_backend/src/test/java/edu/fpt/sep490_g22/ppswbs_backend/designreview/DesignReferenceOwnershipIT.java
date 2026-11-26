package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AnalysisStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.CustomDesignReferenceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDesignRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesign;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignOwnerType;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignStatus;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 004 2.1: only an owned, non-archived Feature-012 design can be attached. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DesignReferenceOwnershipIT {

    private static final long OWNER = 7101L;
    private static final List<String> REFUSED =
            List.of("DSG-FOREIGN", "DSG-GUEST", "DSG-ARCHIVED", "DSG-UNKNOWN");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RingDesignRepository designRepository;

    @Autowired
    private DesignRequestRepository requestRepository;

    @Autowired
    private CustomDesignReferenceSnapshotRepository snapshotRepository;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    @BeforeEach
    void setUp() {
        when(tokenVerificationService.verify("owner"))
                .thenReturn(new VerifiedIdentity("uid-ref-owner", "owner@example.test", true, Instant.now()));
        when(accountFacade.resolveActor("uid-ref-owner", true)).thenReturn(Optional.of(
                new CurrentAccountActor(OWNER, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount("uid-ref-owner")).thenReturn(Optional.of(new AccountSummary(
                OWNER, ActorRole.MEMBER, ActorStatus.ACTIVE, "owner@example.test", Instant.now(), Instant.now())));

        design("DSG-DRAFT", RingDesignOwnerType.MEMBER, String.valueOf(OWNER), RingDesignStatus.DRAFT);
        design("DSG-SAVED", RingDesignOwnerType.MEMBER, String.valueOf(OWNER), RingDesignStatus.SAVED);
        design("DSG-ARCHIVED", RingDesignOwnerType.MEMBER, String.valueOf(OWNER), RingDesignStatus.ARCHIVED);
        design("DSG-FOREIGN", RingDesignOwnerType.MEMBER, "7102", RingDesignStatus.SAVED);
        design("DSG-GUEST", RingDesignOwnerType.GUEST_SESSION, "guest-session", RingDesignStatus.SAVED);
    }

    @Test
    void ownDraftAndSavedDesignsAreAttachedWithASnapshot() throws Exception {
        for (String reference : List.of("DSG-DRAFT", "DSG-SAVED")) {
            submit(reference).andExpect(status().isAccepted());
            assertThat(snapshotRepository.findTopByDesignReferenceOrderByCreatedAtDesc(reference)).isPresent();
        }
    }

    @Test
    void foreignGuestArchivedAndUnknownDesignsAreRefusedAndNothingIsSaved() throws Exception {
        long requests = requestRepository.count();
        long snapshots = snapshotRepository.count();

        for (String reference : REFUSED) {
            submit(reference).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DESIGN_REFERENCE_INVALID"));
        }

        assertThat(requestRepository.count()).isEqualTo(requests);
        assertThat(snapshotRepository.count()).isEqualTo(snapshots);
    }

    @Test
    void revisionRefusesADesignTheMemberMayNotAttach() throws Exception {
        DesignRequest request = clarificationRequest();
        long snapshots = snapshotRepository.count();

        for (String reference : REFUSED) {
            revise(request.getId(), reference).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DESIGN_REFERENCE_INVALID"));
        }

        DesignRequest stored = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(stored.getReviewStatus()).isEqualTo(ReviewStatus.CLARIFICATION_REQUIRED);
        assertThat(stored.getRequestVersion()).isEqualTo(1);
        assertThat(stored.getDesignReference()).isNull();
        assertThat(snapshotRepository.count()).isEqualTo(snapshots);
    }

    @Test
    void revisionAttachesAnOwnDesign() throws Exception {
        DesignRequest request = clarificationRequest();

        revise(request.getId(), "DSG-SAVED").andExpect(status().isOk());

        assertThat(snapshotRepository.findTopByRequestIdOrderByCreatedAtDesc(request.getId())).isPresent();
    }

    private ResultActions submit(String reference) throws Exception {
        return mockMvc.perform(post("/api/v1/custom-design-requests")
                .header("Authorization", "Bearer owner")
                .header("Idempotency-Key", "ref-" + reference)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Ring from my design\",\"designReference\":\"" + reference + "\"}"));
    }

    private ResultActions revise(String requestId, String reference) throws Exception {
        return mockMvc.perform(post("/api/v1/custom-design-requests/" + requestId)
                .header("Authorization", "Bearer owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clarificationResponse\":\"See design\",\"designReference\":\"" + reference + "\"}"));
    }

    private DesignRequest clarificationRequest() {
        return requestRepository.save(DesignRequest.builder()
                .id("ref-revision-request")
                .requestReference("DR-REFREV")
                .ownerAccountId(OWNER)
                .ownershipType("MEMBER")
                .description("Needs clarification")
                .requestVersion(1)
                .idempotencyKey("ref-revision-key")
                .requestStatus("ACTIVE")
                .analysisStatus(AnalysisStatus.SUCCEEDED)
                .reviewStatus(ReviewStatus.CLARIFICATION_REQUIRED)
                .nextAction("NEW_VERSION_REQUIRED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void design(String reference, RingDesignOwnerType ownerType, String owner, RingDesignStatus state) {
        designRepository.save(RingDesign.builder()
                .designReference(reference)
                .ownerType(ownerType)
                .ownerReference(owner)
                .packageReference("PKG-SILVER-01")
                .componentSnapshot("{\"materialReference\":\"SILVER\"}")
                .status(state)
                .build());
    }
}
