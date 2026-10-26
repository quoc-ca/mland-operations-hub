package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BulkSessionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SlotRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T019: Real MySQL integration tests for User Story 1 (Manager branch, slot, and session management).
 * <p>
 * Verifies against MySQL persistence:
 * 1. Branch scope denial across Managers.
 * 2. Slot overlap rejection against both active and inactive slots in the branch.
 * 3. 90-day future limit on session creation and bulk generation.
 * 4. Bulk generation duplicate skip and report behavior without duplicate rows.
 * 5. Inherited schedule snapshot immutability (editing slot hours does not alter existing session snapshots).
 * 6. Audited preservation of Manager check_in_opens_at override.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkshopSessionManagementIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountFacade accountFacade;

    @Autowired
    private WorkshopBranchRepository branchRepository;

    @Autowired
    private WorkshopSlotRepository slotRepository;

    @Autowired
    private WorkshopSessionRepository sessionRepository;

    @Autowired
    private OperationsAuditEventRepository auditEventRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final String MGR_A_TOKEN = "Bearer test-uid:mgr-a";
    private static final String MGR_B_TOKEN = "Bearer test-uid:mgr-b";
    private static final String ADMIN_TOKEN = "Bearer test-uid:admin";

    private WorkshopBranch branchA;
    private WorkshopBranch branchB;
    private WorkshopSlot slotMorning;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_actions");
        jdbcTemplate.execute("TRUNCATE TABLE ticket_session_entitlements");
        jdbcTemplate.execute("TRUNCATE TABLE attendance_tickets");
        jdbcTemplate.execute("TRUNCATE TABLE capacity_holds");
        jdbcTemplate.execute("TRUNCATE TABLE session_operational_leads");
        jdbcTemplate.execute("TRUNCATE TABLE session_staff_assignments");
        jdbcTemplate.execute("TRUNCATE TABLE cancellation_handoffs");
        jdbcTemplate.execute("TRUNCATE TABLE operations_audit_events");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_sessions");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_slots");
        jdbcTemplate.execute("TRUNCATE TABLE workshop_branches");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        branchA = branchRepository.save(WorkshopBranch.builder()
                .reference(UUID.randomUUID().toString())
                .code("BR-HCM-01")
                .displayName("District 1 Branch")
                .addressSnapshot("123 Le Loi, D1, HCMC")
                .active(true)
                .build());

        branchB = branchRepository.save(WorkshopBranch.builder()
                .reference(UUID.randomUUID().toString())
                .code("BR-HCM-02")
                .displayName("District 2 Branch")
                .addressSnapshot("456 Thao Dien, D2, HCMC")
                .active(true)
                .build());

        slotMorning = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .branch(branchA)
                .label("Standard Morning Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(25)
                .active(true)
                .build());

        // Setup AccountFacade mocks: Manager A has scope for Branch A (id=branchA.getId())
        when(accountFacade.resolveAccount("mgr-a")).thenReturn(Optional.of(
                new AccountSummary(10L, AccountRole.MANAGER, AccountStatus.ACTIVE, "mgra@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getLocationScope(10L)).thenReturn(List.of(branchA.getId()));

        // Manager B has scope for Branch B
        when(accountFacade.resolveAccount("mgr-b")).thenReturn(Optional.of(
                new AccountSummary(20L, AccountRole.MANAGER, AccountStatus.ACTIVE, "mgrb@mland.vn", Instant.now(), Instant.now())));
        when(accountFacade.getLocationScope(20L)).thenReturn(List.of(branchB.getId()));

        // Admin has universal authority
        when(accountFacade.resolveAccount("admin")).thenReturn(Optional.of(
                new AccountSummary(99L, AccountRole.ADMIN, AccountStatus.ACTIVE, "admin@mland.vn", Instant.now(), Instant.now())));
    }

    // =========================================================================
    // 1. Branch Scope Denial
    // =========================================================================

    @Test
    @DisplayName("Manager A attempting to configure slots or sessions on Branch B is denied with 403 BRANCH_SCOPE_DENIED")
    void managerDeniedOnOutOfScopeBranch() throws Exception {
        // Manager A attempts to list slots on Branch B
        mockMvc.perform(get("/api/v1/workshop-branches/" + branchB.getReference() + "/slots")
                        .header("Authorization", MGR_A_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_SCOPE_DENIED"));

        // Manager A attempts to create a slot on Branch B
        SlotRequest newSlot = SlotRequest.builder()
                .label("Unauthorized Slot")
                .startsAtLocal(LocalTime.of(13, 0))
                .endsAtLocal(LocalTime.of(16, 0))
                .defaultCapacity(20)
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/workshop-branches/" + branchB.getReference() + "/slots")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newSlot)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_SCOPE_DENIED"));
    }

    // =========================================================================
    // 2. Slot Overlap Rejection Against Active AND Inactive Slots
    // =========================================================================

    @Test
    @DisplayName("Slot overlap is rejected (409) against active and inactive configured slots in the branch")
    void slotOverlapRejectedAgainstActiveAndInactiveSlots() throws Exception {
        // 1. Attempt overlapping slot with active slotMorning (09:00-12:00): requested 10:00-13:00
        SlotRequest overlapping = SlotRequest.builder()
                .label("Overlapping Mid-Morning Slot")
                .startsAtLocal(LocalTime.of(10, 0))
                .endsAtLocal(LocalTime.of(13, 0))
                .defaultCapacity(20)
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/workshop-branches/" + branchA.getReference() + "/slots")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapping)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_OVERLAP_CONFLICT"));

        // 2. Deactivate slotMorning (active = false)
        slotMorning.setActive(false);
        slotRepository.save(slotMorning);

        // 3. Attempt overlapping slot (11:00-14:00) with inactive slotMorning (09:00-12:00)
        SlotRequest overlapInactive = SlotRequest.builder()
                .label("Overlap Inactive Slot")
                .startsAtLocal(LocalTime.of(11, 0))
                .endsAtLocal(LocalTime.of(14, 0))
                .defaultCapacity(20)
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/workshop-branches/" + branchA.getReference() + "/slots")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapInactive)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_OVERLAP_CONFLICT"));

        // 4. Non-overlapping slot (13:00-16:00) succeeds
        SlotRequest nonOverlapping = SlotRequest.builder()
                .label("Afternoon Slot")
                .startsAtLocal(LocalTime.of(13, 0))
                .endsAtLocal(LocalTime.of(16, 0))
                .defaultCapacity(30)
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/workshop-branches/" + branchA.getReference() + "/slots")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonOverlapping)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Afternoon Slot"));
    }

    // =========================================================================
    // 3. 90-Day Future Limit Enforcement
    // =========================================================================

    @Test
    @DisplayName("Creating session beyond 90 days in the future is rejected with 400 VALIDATION_ERROR")
    void sessionBeyondNinetyDaysRejected() throws Exception {
        LocalDate dateBeyond90 = LocalDate.now().plusDays(95);

        SessionRequest request = SessionRequest.builder()
                .branchId(branchA.getReference())
                .slotId(slotMorning.getReference())
                .workshopDate(dateBeyond90)
                .capacity(20)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Creating session with past date or start time is rejected with 400 VALIDATION_ERROR")
    void sessionInPastRejected() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        SessionRequest request = SessionRequest.builder()
                .branchId(branchA.getReference())
                .slotId(slotMorning.getReference())
                .workshopDate(yesterday)
                .capacity(20)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // 4. Bulk Generation Duplicate Skip & Report Behavior
    // =========================================================================

    @Test
    @DisplayName("Bulk session generation creates sessions on first run, and skips duplicates on second run without error")
    void bulkGenerationDuplicateSkipAndReport() throws Exception {
        LocalDate from = LocalDate.now().plusDays(5);
        LocalDate to = LocalDate.now().plusDays(12);

        BulkSessionRequest bulkRequest = BulkSessionRequest.builder()
                .branchId(branchA.getReference())
                .slotIds(List.of(slotMorning.getReference()))
                .from(from)
                .to(to)
                .weekdays(List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
                .initialState(SessionState.DRAFT)
                .build();

        // Run 1: Creates new sessions
        String run1Result = mockMvc.perform(post("/api/v1/workshop-sessions/bulk")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(run1Result).contains("createdSessionIds");
        int countAfterRun1 = sessionRepository.findAll().size();
        assertThat(countAfterRun1).isGreaterThan(0);

        // Run 2: Exact same request skips existing duplicates
        mockMvc.perform(post("/api/v1/workshop-sessions/bulk")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdSessionIds").isEmpty())
                .andExpect(jsonPath("$.skippedDuplicates").isNotEmpty());

        int countAfterRun2 = sessionRepository.findAll().size();
        assertThat(countAfterRun2).as("No duplicate rows should be inserted").isEqualTo(countAfterRun1);
    }

    // =========================================================================
    // 5. Inherited Schedule Snapshot Immutability
    // =========================================================================

    @Test
    @DisplayName("Updating a slot's hours later does NOT alter the scheduled start/end timestamps of existing sessions")
    void slotModificationDoesNotAlterExistingSessionSchedule() throws Exception {
        LocalDate sessionDate = LocalDate.now().plusDays(7);

        // 1. Create a session inheriting slotMorning (09:00 - 12:00)
        SessionRequest sessionReq = SessionRequest.builder()
                .branchId(branchA.getReference())
                .slotId(slotMorning.getReference())
                .workshopDate(sessionDate)
                .capacity(20)
                .build();

        String sessionResponseStr = mockMvc.perform(post("/api/v1/workshop-sessions")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionReference = objectMapper.readTree(sessionResponseStr).get("id").asText();
        WorkshopSession createdSession = sessionRepository.findByReference(sessionReference).orElseThrow();
        Instant originalStart = createdSession.getScheduledStartAt();
        Instant originalEnd = createdSession.getScheduledEndAt();

        // 2. Update slotMorning hours to 08:30 - 11:30
        SlotRequest slotUpdate = SlotRequest.builder()
                .label("Early Morning Slot")
                .startsAtLocal(LocalTime.of(8, 30))
                .endsAtLocal(LocalTime.of(11, 30))
                .defaultCapacity(30)
                .active(true)
                .build();

        mockMvc.perform(patch("/api/v1/workshop-slots/" + slotMorning.getReference())
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotUpdate)))
                .andExpect(status().isOk());

        // 3. Verify slot in DB was updated
        WorkshopSlot updatedSlot = slotRepository.findById(slotMorning.getId()).orElseThrow();
        assertThat(updatedSlot.getStartTime()).isEqualTo(LocalTime.of(8, 30));

        // 4. Verify existing session in DB preserved its original scheduledStartAt and scheduledEndAt
        WorkshopSession sessionAfterSlotEdit = sessionRepository.findByReference(sessionReference).orElseThrow();
        assertThat(sessionAfterSlotEdit.getScheduledStartAt())
                .as("Existing session start must remain immutable")
                .isEqualTo(originalStart);
        assertThat(sessionAfterSlotEdit.getScheduledEndAt())
                .as("Existing session end must remain immutable")
                .isEqualTo(originalEnd);
    }

    // =========================================================================
    // 6. Audited Preservation of Manager Check-in Opening Override
    // =========================================================================

    @Test
    @DisplayName("Manager check_in_opens_at override is recorded with MANAGER_SET source and audited")
    void managerCheckInOpensAtOverrideAudited() throws Exception {
        LocalDate sessionDate = LocalDate.now().plusDays(10);
        ZonedDateTime sessionStart = sessionDate.atTime(slotMorning.getStartTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        Instant customCheckIn = sessionStart.minusSeconds(7200).toInstant(); // 2 hours before start instead of default 1 hour

        SessionRequest request = SessionRequest.builder()
                .branchId(branchA.getReference())
                .slotId(slotMorning.getReference())
                .workshopDate(sessionDate)
                .capacity(15)
                .checkInOpensAt(customCheckIn)
                .build();

        mockMvc.perform(post("/api/v1/workshop-sessions")
                        .header("Authorization", MGR_A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkInOpeningSource").value("MANAGER_SET"))
                .andExpect(jsonPath("$.checkInOpensAt").value(customCheckIn.toString()));

        // Verify audit event in DB
        List<OperationsAuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents.stream().anyMatch(e -> "CHECK_IN_OVERRIDE_CONFIGURED".equals(e.getAction()))).isTrue();
    }
}
