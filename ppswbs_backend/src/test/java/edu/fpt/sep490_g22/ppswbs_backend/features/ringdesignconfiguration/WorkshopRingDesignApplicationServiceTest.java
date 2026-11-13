package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingCatalogueModelRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDesignRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.DesignOptionRulesPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.GuestDesignSessionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignException;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.RingDesignIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopRingDesignApplicationServiceTest {

    @Mock
    private RingDesignRepository designRepository;
    @Mock
    private RingCatalogueModelRepository catalogueModelRepository;
    @Mock
    private GuestDesignSessionService guestSessionService;
    @Mock
    private RingDesignIdempotencyService idempotencyService;
    @Mock
    private DesignOptionRulesPort rulesPort;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkshopRingDesignApplicationService applicationService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("createDraft for Guest should issue session if missing, create initial DRAFT, and record idempotency")
    void createDraft_Guest_Success() {
        CreateDesignRequest request = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE-01")
                .startMode("BLANK")
                .build();

        GuestDesignSession guestSession = GuestDesignSession.builder()
                .sessionReference("GDS-new-session")
                .sessionDigest("digest-new-session")
                .status(GuestSessionStatus.ACTIVE)
                .issuedAt(Instant.now())
                .lastActivityAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1200))
                .build();

        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(guestSessionService.issueGuestSession(any())).thenReturn(guestSession);
        when(rulesPort.evaluate(any())).thenReturn(EvaluationResultDto.builder()
                .estimatedPrice("250000.00").currency("VND").difficultyScore("1.0000").rulesVersion("v1").advisory(true).build());
        when(rulesPort.validateCompatibility(any(), any())).thenReturn(ValidationResultDto.builder().valid(true).reasons(Collections.emptyList()).build());
        when(designRepository.save(any(RingDesign.class))).thenAnswer(inv -> inv.getArgument(0));

        DesignResponseDto response = applicationService.createDraft(request, null, null, "IDEMP-CREATE-1");

        assertThat(response.getDesignId()).startsWith("DSG-");
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getDesignVersion()).isEqualTo(1);
        assertThat(response.getPackageReference()).isEqualTo("PKG-COUPLE-01");
        verify(idempotencyService, times(1)).record(eq("IDEMP-CREATE-1"), eq("CREATE_DESIGN"), any(), eq(request), eq(201), any());
    }

    @Test
    @DisplayName("createDraft for Member should create design with MEMBER ownerType and account ID")
    void createDraft_Member_Success() {
        CreateDesignRequest request = CreateDesignRequest.builder()
                .packageReference("PKG-COUPLE-01")
                .startMode("BLANK")
                .build();

        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(rulesPort.evaluate(any())).thenReturn(EvaluationResultDto.builder()
                .estimatedPrice("250000.00").currency("VND").difficultyScore("1.0000").rulesVersion("v1").advisory(true).build());
        when(rulesPort.validateCompatibility(any(), any())).thenReturn(ValidationResultDto.builder().valid(true).reasons(Collections.emptyList()).build());
        when(designRepository.save(any(RingDesign.class))).thenAnswer(inv -> inv.getArgument(0));

        DesignResponseDto response = applicationService.createDraft(request, 101L, null, "IDEMP-MEMBER-CREATE");

        assertThat(response.getDesignId()).startsWith("DSG-");
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        verify(designRepository, times(1)).save(argThat(design ->
                design.getOwnerType() == RingDesignOwnerType.MEMBER &&
                design.getOwnerReference().equals("101")
        ));
    }

    @Test
    @DisplayName("saveVersion should create new immutable row with incremented version and VALIDATED status")
    void saveVersion_Success() {
        String designId = "DSG-existing-01";
        RingDesign latestVersion = RingDesign.builder()
                .id(1L)
                .designReference(designId)
                .designVersion(1)
                .ownerType(RingDesignOwnerType.MEMBER)
                .ownerReference("101")
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .status(RingDesignStatus.DRAFT)
                .build();

        SaveDesignVersionRequest saveReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-GOLD-14K")
                .shapeReference("SHP-ROUND")
                .ringSize("9")
                .components(Collections.emptyList())
                .attachments(Collections.emptyList())
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build();

        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(designRepository.findLatestByDesignReference(designId)).thenReturn(Optional.of(latestVersion));
        when(rulesPort.validateCompatibility(saveReq, null)).thenReturn(ValidationResultDto.builder().valid(true).reasons(Collections.emptyList()).build());
        when(rulesPort.evaluate(saveReq)).thenReturn(EvaluationResultDto.builder()
                .estimatedPrice("1800000.00").currency("VND").difficultyScore("2.5000").rulesVersion("v1").advisory(true).build());
        when(designRepository.save(any(RingDesign.class))).thenAnswer(inv -> inv.getArgument(0));

        DesignResponseDto response = applicationService.saveVersion(designId, saveReq, 101L, null, 1L, "IDEMP-SAVE-1");

        assertThat(response.getDesignId()).isEqualTo(designId);
        assertThat(response.getDesignVersion()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo("VALIDATED");
        assertThat(response.getEvaluation().getEstimatedPrice()).isEqualTo("1800000.00");

        verify(designRepository, times(1)).save(argThat(d ->
                d.getDesignVersion() == 2 &&
                d.getSourceDesign() == latestVersion &&
                d.getStatus() == RingDesignStatus.VALIDATED
        ));
    }

    @Test
    @DisplayName("saveVersion should throw 409 DESIGN_VERSION_CONFLICT on optimistic version mismatch")
    void saveVersion_StaleVersion_Throws409() {
        String designId = "DSG-existing-01";
        RingDesign latestVersion = RingDesign.builder()
                .id(2L)
                .designReference(designId)
                .designVersion(2) // current version is 2
                .ownerType(RingDesignOwnerType.MEMBER)
                .ownerReference("101")
                .packageReference("PKG-01")
                .status(RingDesignStatus.VALIDATED)
                .build();

        SaveDesignVersionRequest saveReq = SaveDesignVersionRequest.builder()
                .packageReference("PKG-01")
                .materialReference("MAT-GOLD-14K")
                .shapeReference("SHP-ROUND")
                .ringSize("9")
                .components(Collections.emptyList())
                .attachments(Collections.emptyList())
                .engraving(EngravingInputDto.builder().enabled(false).build())
                .build();

        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(designRepository.findLatestByDesignReference(designId)).thenReturn(Optional.of(latestVersion));

        // Client sends If-Match-Version = 1 (stale)
        assertThatThrownBy(() -> applicationService.saveVersion(designId, saveReq, 101L, null, 1L, "IDEMP-SAVE-STALE"))
                .isInstanceOf(RingDesignException.class)
                .satisfies(ex -> {
                    RingDesignException rde = (RingDesignException) ex;
                    assertThat(rde.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rde.getCode()).isEqualTo("DESIGN_VERSION_CONFLICT");
                });
    }

    @Test
    @DisplayName("getDesign should throw 403 DESIGN_ACCESS_DENIED when accessed by different member")
    void getDesign_WrongOwner_Throws403() {
        String designId = "DSG-private-01";
        RingDesign design = RingDesign.builder()
                .designReference(designId)
                .designVersion(1)
                .ownerType(RingDesignOwnerType.MEMBER)
                .ownerReference("999") // Owner is 999
                .packageReference("PKG-01")
                .status(RingDesignStatus.VALIDATED)
                .build();

        when(designRepository.findLatestByDesignReference(designId)).thenReturn(Optional.of(design));

        // Member 101 attempts to view
        assertThatThrownBy(() -> applicationService.getDesign(designId, 101L, null))
                .isInstanceOf(RingDesignException.class)
                .satisfies(ex -> {
                    RingDesignException rde = (RingDesignException) ex;
                    assertThat(rde.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rde.getCode()).isEqualTo("DESIGN_ACCESS_DENIED");
                });
    }

    @Test
    @DisplayName("archive should set status to ARCHIVED for unsubmitted design")
    void archive_Success() {
        String designId = "DSG-archive-01";
        RingDesign design = RingDesign.builder()
                .designReference(designId)
                .designVersion(1)
                .ownerType(RingDesignOwnerType.MEMBER)
                .ownerReference("101")
                .packageReference("PKG-01")
                .status(RingDesignStatus.VALIDATED)
                .build();

        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(designRepository.findLatestByDesignReference(designId)).thenReturn(Optional.of(design));
        when(designRepository.save(any(RingDesign.class))).thenAnswer(inv -> inv.getArgument(0));

        DesignResponseDto response = applicationService.archive(designId, 101L, null, 1L, "IDEMP-ARCHIVE-1");

        assertThat(response.getStatus()).isEqualTo("ARCHIVED");
        assertThat(design.getStatus()).isEqualTo(RingDesignStatus.ARCHIVED);
    }

    @Test
    @DisplayName("attachToBooking should revalidate branch compatibility and transition to ATTACHED_TO_BOOKING")
    void attachToBooking_Success() {
        String designId = "DSG-attach-01";
        RingDesign design = RingDesign.builder()
                .designReference(designId)
                .designVersion(2)
                .ownerType(RingDesignOwnerType.MEMBER)
                .ownerReference("101")
                .packageReference("PKG-01")
                .materialReference("MAT-SILVER-925")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .componentSnapshot("{\"components\":[]}")
                .status(RingDesignStatus.VALIDATED)
                .build();

        AttachDesignRequestDto req = AttachDesignRequestDto.builder()
                .bookingReference("BKG-999")
                .packageReference("PKG-01")
                .branchReference("BRANCH-HCM-01")
                .build();

        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(designRepository.findLatestByDesignReference(designId)).thenReturn(Optional.of(design));
        when(rulesPort.validateCompatibility(any(), eq("BRANCH-HCM-01")))
                .thenReturn(ValidationResultDto.builder().valid(true).reasons(Collections.emptyList()).build());
        when(designRepository.save(any(RingDesign.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDesignSnapshotDto snapshot = applicationService.attachToBooking(designId, req, 101L, null, 2L, "IDEMP-ATTACH-1");

        assertThat(snapshot.getDesignReference()).isEqualTo(designId);
        assertThat(snapshot.getDesignVersion()).isEqualTo(2);
        assertThat(design.getStatus()).isEqualTo(RingDesignStatus.ATTACHED_TO_BOOKING);
        assertThat(design.getBookingReference()).isEqualTo("BKG-999");
    }

    @Test
    @DisplayName("attachToBooking should reject with 422 DESIGN_BRANCH_INCOMPATIBLE when branch does not support material")
    void attachToBooking_BranchIncompatible_Throws422() {
        String designId = "DSG-attach-incompatible";
        RingDesign design = RingDesign.builder()
                .designReference(designId)
                .designVersion(1)
                .ownerType(RingDesignOwnerType.MEMBER)
                .ownerReference("101")
                .packageReference("PKG-01")
                .materialReference("MAT-GOLD-14K")
                .shapeReference("SHP-ROUND")
                .ringSize("8")
                .status(RingDesignStatus.VALIDATED)
                .build();

        AttachDesignRequestDto req = AttachDesignRequestDto.builder()
                .bookingReference("BKG-999")
                .packageReference("PKG-01")
                .branchReference("BRANCH-RESTRICTED-VANG")
                .build();

        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(designRepository.findLatestByDesignReference(designId)).thenReturn(Optional.of(design));
        when(rulesPort.validateCompatibility(any(), eq("BRANCH-RESTRICTED-VANG")))
                .thenReturn(ValidationResultDto.builder()
                        .valid(false)
                        .reasons(List.of("Chi nhánh được chọn hiện chưa hỗ trợ lò nung nhiệt độ cao gia công vật liệu vàng."))
                        .build());

        assertThatThrownBy(() -> applicationService.attachToBooking(designId, req, 101L, null, 1L, "IDEMP-ATTACH-INCOMPAT"))
                .isInstanceOf(RingDesignException.class)
                .satisfies(ex -> {
                    RingDesignException rde = (RingDesignException) ex;
                    assertThat(rde.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rde.getCode()).isEqualTo("DESIGN_BRANCH_INCOMPATIBLE");
                    assertThat(rde.getDetails()).hasSize(1);
                });
    }
}

