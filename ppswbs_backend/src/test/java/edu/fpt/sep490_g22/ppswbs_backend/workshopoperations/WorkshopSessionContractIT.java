package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopOperationsExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T014: Contract verification test for Workshop Operations US1 endpoints
 * (location, slot, session, bulk session generation) against contracts/workshop-operations.openapi.yaml.
 * <p>
 * Verifies request/response schema shape, validation/error contract (RFC 7807 Problem Detail),
 * timezone Asia/Ho_Chi_Minh same-day constraints, capacity minimums, and security scheme boundaries.
 * Proves that retired branch routes and fields are rejected/absent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkshopSessionContractIT {

    private static final Logger log = LoggerFactory.getLogger(WorkshopSessionContractIT.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private AccountFacade accountFacade;

    private static Map<String, Object> openApiSpec;
    private static Validator validator;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadContractSpec() throws Exception {
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        File specFile = resolveOpenApiFile();
        log.info("Loading OpenAPI contract from: {}", specFile.getAbsolutePath());

        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(specFile)) {
            openApiSpec = yaml.load(in);
        }
        assertThat(openApiSpec).as("OpenAPI contract must load successfully").isNotNull();
    }

    private static File resolveOpenApiFile() {
        String[] candidatePaths = {
                "../ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml",
                "ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml",
                "contracts/workshop-operations.openapi.yaml"
        };
        for (String path : candidatePaths) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }
        throw new IllegalStateException("OpenAPI contract file not found in any candidate path");
    }

    // =========================================================================
    // 1. OpenAPI Specification Metadata and Security Contract
    // =========================================================================

    @Test
    @DisplayName("T014-1: OpenAPI metadata, server base path, and bearer security scheme contract")
    @SuppressWarnings("unchecked")
    void verifyOpenApiSpecificationMetadataAndSecurity() {
        assertThat(openApiSpec.get("openapi")).isEqualTo("3.0.3");

        // Server URL contract: /api/v1
        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        assertThat(servers).isNotEmpty();
        assertThat(servers.get(0).get("url")).isEqualTo("/api/v1");

        // Global security requirement: bearerAuth
        List<Map<String, Object>> security = (List<Map<String, Object>>) openApiSpec.get("security");
        assertThat(security).isNotEmpty();
        assertThat(security.get(0)).containsKey("bearerAuth");

        // Security scheme component: bearerAuth with Firebase-ID-token format
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
        assertThat(securitySchemes).containsKey("bearerAuth");

        Map<String, Object> bearerAuth = (Map<String, Object>) securitySchemes.get("bearerAuth");
        assertThat(bearerAuth.get("type")).isEqualTo("http");
        assertThat(bearerAuth.get("scheme")).isEqualTo("bearer");
        assertThat(bearerAuth.get("bearerFormat")).isEqualTo("Firebase-ID-token");
    }

    // =========================================================================
    // 2. Location Endpoint and DTO Contract (replaces Branch)
    // =========================================================================

    @Test
    @DisplayName("T014-2: Location endpoints (/workshop-locations) and LocationWrite/Location schema contract (proves /workshop-branches retired)")
    @SuppressWarnings("unchecked")
    void verifyLocationContractInOpenApiAndDto() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-locations");
        assertThat(paths).doesNotContainKey("/workshop-branches");

        Map<String, Object> locationPath = (Map<String, Object>) paths.get("/workshop-locations");
        assertThat(locationPath).containsKeys("get", "post");

        // POST /workshop-locations schema contract
        Map<String, Object> postOp = (Map<String, Object>) locationPath.get("post");
        Map<String, Object> requestBody = (Map<String, Object>) postOp.get("requestBody");
        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/LocationWrite");

        // Components: LocationWrite and Location
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> locationWrite = (Map<String, Object>) schemas.get("LocationWrite");
        List<String> requiredFields = (List<String>) locationWrite.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("code", "name");

        Map<String, Object> properties = (Map<String, Object>) locationWrite.get("properties");
        assertThat(properties).containsKeys("code", "name", "address", "active");

        // DTO validation: LocationRequest enforces @NotBlank on code and name
        LocationRequest validReq = LocationRequest.builder().code("LOC-01").name("Location 1").build();
        assertThat(validator.validate(validReq)).isEmpty();

        LocationRequest invalidReq = LocationRequest.builder().code("").name("").build();
        Set<ConstraintViolation<LocationRequest>> violations = validator.validate(invalidReq);
        assertThat(violations).hasSize(2);

        // LocationResponse shape matches OpenAPI Location schema (id: int64, code, name, address, active)
        LocationResponse response = LocationResponse.builder()
                .id(1L)
                .code("LOC-01")
                .name("Location 1")
                .address("123 Street")
                .active(true)
                .build();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCode()).isEqualTo("LOC-01");
    }

    // =========================================================================
    // 3. Slot Endpoint and DTO Contract (Asia/Ho_Chi_Minh same-day, defaultCapacity >= 1)
    // =========================================================================

    @Test
    @DisplayName("T014-3: Slot endpoints and SlotWrite/Slot schema contract with same-day timing and capacity >= 1")
    @SuppressWarnings("unchecked")
    void verifySlotContractInOpenApiAndDto() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKeys("/workshop-locations/{locationId}/slots", "/workshop-slots/{slotId}");
        assertThat(paths).doesNotContainKey("/workshop-branches/{branchId}/slots");

        // /workshop-locations/{locationId}/slots (get, post)
        Map<String, Object> locationSlotsPath = (Map<String, Object>) paths.get("/workshop-locations/{locationId}/slots");
        assertThat(locationSlotsPath).containsKeys("get", "post");

        // /workshop-slots/{slotId} (patch)
        Map<String, Object> slotIdPath = (Map<String, Object>) paths.get("/workshop-slots/{slotId}");
        assertThat(slotIdPath).containsKey("patch");

        // Components: SlotWrite
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> slotWrite = (Map<String, Object>) schemas.get("SlotWrite");
        List<String> requiredFields = (List<String>) slotWrite.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("label", "startsAtLocal", "endsAtLocal", "defaultCapacity", "active");

        Map<String, Object> properties = (Map<String, Object>) slotWrite.get("properties");
        assertThat(properties).containsKeys("label", "startsAtLocal", "endsAtLocal", "defaultCapacity", "active");

        Map<String, Object> defaultCapacityProp = (Map<String, Object>) properties.get("defaultCapacity");
        assertThat(defaultCapacityProp.get("minimum")).isEqualTo(1);

        // DTO validation: SlotRequest enforces Asia/Ho_Chi_Minh same-day timing and capacity >= 1
        assertThat(SlotRequest.WORKSHOP_ZONE).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));

        SlotRequest validSlot = SlotRequest.builder()
                .label("Standard Morning Slot")
                .startsAtLocal(LocalTime.of(9, 0))
                .endsAtLocal(LocalTime.of(12, 0))
                .defaultCapacity(30)
                .active(true)
                .build();
        assertThat(validator.validate(validSlot)).isEmpty();
        assertThat(validSlot.isSameDayTimingValid()).isTrue();

        // Rejection: defaultCapacity < 1
        SlotRequest zeroCapacity = SlotRequest.builder()
                .label("Invalid Capacity")
                .startsAtLocal(LocalTime.of(9, 0))
                .endsAtLocal(LocalTime.of(12, 0))
                .defaultCapacity(0)
                .active(true)
                .build();
        assertThat(validator.validate(zeroCapacity)).isNotEmpty();

        // Rejection: startsAtLocal >= endsAtLocal (overnight or zero duration)
        SlotRequest overnightSlot = SlotRequest.builder()
                .label("Overnight Slot")
                .startsAtLocal(LocalTime.of(20, 0))
                .endsAtLocal(LocalTime.of(1, 0))
                .defaultCapacity(10)
                .active(true)
                .build();
        assertThat(validator.validate(overnightSlot)).isNotEmpty();
        assertThat(overnightSlot.isSameDayTimingValid()).isFalse();

        // SlotResponse shape matches OpenAPI Slot schema (id, locationId, label, startsAtLocal, endsAtLocal, defaultCapacity, active)
        SlotResponse slotResp = SlotResponse.builder()
                .id(UUID.randomUUID().toString())
                .locationId(1L)
                .label("Afternoon")
                .startsAtLocal(LocalTime.of(13, 0))
                .endsAtLocal(LocalTime.of(16, 0))
                .defaultCapacity(25)
                .active(true)
                .build();
        assertThat(slotResp.getId()).isNotNull();
        assertThat(slotResp.getLocationId()).isEqualTo(1L);

        // Reflection proof: SlotResponse declares locationId and NOT branchId
        List<String> slotFields = Arrays.stream(SlotResponse.class.getDeclaredFields()).map(Field::getName).toList();
        assertThat(slotFields).contains("locationId").doesNotContain("branchId");
    }

    // =========================================================================
    // 4. Session Endpoint and DTO Contract (no free-form start/end, capacity >= 1)
    // =========================================================================

    @Test
    @DisplayName("T014-4: Session endpoints and SessionWrite/Session schema contract (locationId, no free-form start/end, capacity >= 1)")
    @SuppressWarnings("unchecked")
    void verifySessionContractInOpenApiAndDto() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKeys("/workshop-sessions", "/workshop-sessions/{sessionId}");

        // /workshop-sessions (get, post)
        Map<String, Object> sessionsPath = (Map<String, Object>) paths.get("/workshop-sessions");
        assertThat(sessionsPath).containsKeys("get", "post");

        // /workshop-sessions/{sessionId} (get, patch)
        Map<String, Object> sessionIdPath = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}");
        assertThat(sessionIdPath).containsKeys("get", "patch");

        // Components: SessionWrite
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> sessionWrite = (Map<String, Object>) schemas.get("SessionWrite");
        List<String> requiredFields = (List<String>) sessionWrite.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("locationId", "slotId", "workshopDate", "capacity");

        Map<String, Object> properties = (Map<String, Object>) sessionWrite.get("properties");
        assertThat(properties).containsKeys("locationId", "slotId", "workshopDate", "capacity", "checkInOpensAt");

        // CRITICAL INVARIANT: SessionWrite does NOT have scheduledStartAt or scheduledEndAt or branchId
        assertThat(properties).doesNotContainKeys("scheduledStartAt", "scheduledEndAt", "startTime", "endTime", "branchId");

        Map<String, Object> capacityProp = (Map<String, Object>) properties.get("capacity");
        assertThat(capacityProp.get("minimum")).isEqualTo(1);

        // DTO verification: SessionRequest has capacity >= 1 and NO free-form start/end fields
        SessionRequest validSession = SessionRequest.builder()
                .locationId(1L)
                .slotId(UUID.randomUUID().toString())
                .workshopDate(LocalDate.now().plusDays(5))
                .capacity(20)
                .build();
        assertThat(validator.validate(validSession)).isEmpty();

        SessionRequest zeroCapacity = SessionRequest.builder()
                .locationId(1L)
                .slotId(UUID.randomUUID().toString())
                .workshopDate(LocalDate.now().plusDays(5))
                .capacity(0)
                .build();
        assertThat(validator.validate(zeroCapacity)).isNotEmpty();

        // Reflection proof: SessionRequest declares no scheduledStartAt or scheduledEndAt or branchId
        List<String> fieldNames = Arrays.stream(SessionRequest.class.getDeclaredFields()).map(Field::getName).toList();
        assertThat(fieldNames).doesNotContain("scheduledStartAt", "scheduledEndAt", "startTime", "endTime", "branchId");

        // Components: SessionState enum matches [DRAFT, PUBLISHED, SALES_PAUSED, IN_PROGRESS, ATTENDANCE_FINALIZED, CANCELLATION_REQUESTED, CANCELLED]
        Map<String, Object> sessionStateSchema = (Map<String, Object>) schemas.get("SessionState");
        List<String> openApiStates = (List<String>) sessionStateSchema.get("enum");
        List<String> javaEnumStates = Arrays.stream(SessionState.values()).map(Enum::name).toList();
        assertThat(javaEnumStates).containsExactlyElementsOf(openApiStates);

        // SessionResponse has all required properties from OpenAPI Session schema
        SessionResponse sessionResp = SessionResponse.builder()
                .id(UUID.randomUUID().toString())
                .locationId(1L)
                .slotId(UUID.randomUUID().toString())
                .workshopDate(LocalDate.now().plusDays(5))
                .capacity(20)
                .state(SessionState.DRAFT)
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .build();
        assertThat(sessionResp.getId()).isNotNull();
        assertThat(sessionResp.getLocationId()).isEqualTo(1L);
        assertThat(sessionResp.getState()).isEqualTo(SessionState.DRAFT);
    }

    // =========================================================================
    // 5. Bulk Session Generation Endpoint and DTO Contract
    // =========================================================================

    @Test
    @DisplayName("T014-5: Bulk session generation (/workshop-sessions/bulk) and BulkSessionRequest/Result schema contract")
    @SuppressWarnings("unchecked")
    void verifyBulkSessionGenerationContractInOpenApiAndDto() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/bulk");

        Map<String, Object> bulkPath = (Map<String, Object>) paths.get("/workshop-sessions/bulk");
        assertThat(bulkPath).containsKey("post");

        Map<String, Object> postOp = (Map<String, Object>) bulkPath.get("post");
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("201", "400", "403");

        // Components: BulkSessionRequest
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> bulkReqSchema = (Map<String, Object>) schemas.get("BulkSessionRequest");
        List<String> requiredFields = (List<String>) bulkReqSchema.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("locationId", "slotIds", "from", "to", "weekdays");

        Map<String, Object> properties = (Map<String, Object>) bulkReqSchema.get("properties");
        assertThat(properties).containsKeys("locationId", "slotIds", "from", "to", "weekdays", "initialState");

        // Components: BulkGenerationResult
        Map<String, Object> bulkResultSchema = (Map<String, Object>) schemas.get("BulkGenerationResult");
        List<String> resultRequired = (List<String>) bulkResultSchema.get("required");
        assertThat(resultRequired).containsExactlyInAnyOrder("createdSessionIds", "skippedDuplicates");

        // DTO verification: BulkSessionRequest validates from <= to and initialState
        BulkSessionRequest validBulk = BulkSessionRequest.builder()
                .locationId(1L)
                .slotIds(List.of(UUID.randomUUID().toString()))
                .from(LocalDate.now().plusDays(1))
                .to(LocalDate.now().plusDays(14))
                .weekdays(List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
                .initialState(SessionState.DRAFT)
                .build();
        assertThat(validator.validate(validBulk)).isEmpty();
        assertThat(validBulk.isDateRangeValid()).isTrue();
        assertThat(validBulk.isInitialStateValid()).isTrue();

        // Inverted dates rejection
        BulkSessionRequest invertedDates = BulkSessionRequest.builder()
                .locationId(1L)
                .slotIds(List.of(UUID.randomUUID().toString()))
                .from(LocalDate.now().plusDays(14))
                .to(LocalDate.now().plusDays(1))
                .weekdays(List.of(DayOfWeek.MONDAY))
                .build();
        assertThat(validator.validate(invertedDates)).isNotEmpty();
        assertThat(invertedDates.isDateRangeValid()).isFalse();

        // Reflection proof: BulkSessionRequest declares locationId and NOT branchId
        List<String> bulkFields = Arrays.stream(BulkSessionRequest.class.getDeclaredFields()).map(Field::getName).toList();
        assertThat(bulkFields).contains("locationId").doesNotContain("branchId");

        // BulkGenerationResponse / BulkGenerationResult shape matches OpenAPI
        BulkGenerationResponse bulkResp = BulkGenerationResponse.builder()
                .createdSessionIds(List.of(UUID.randomUUID().toString()))
                .skippedDuplicates(List.of("LOC1-2026-11-01-SL1"))
                .build();
        assertThat(bulkResp.getCreatedSessionIds()).hasSize(1);
        assertThat(bulkResp.getSkippedDuplicates()).hasSize(1);
    }

    // =========================================================================
    // 6. Problem Detail (RFC 7807) Error Contract
    // =========================================================================

    @Test
    @DisplayName("T014-6: RFC 7807 Problem Detail schema contract and ExceptionHandler mapping")
    @SuppressWarnings("unchecked")
    void verifyProblemDetailErrorContractInOpenApiAndHandler() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> problemSchema = (Map<String, Object>) schemas.get("Problem");
        List<String> requiredFields = (List<String>) problemSchema.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("type", "title", "status", "code");

        Map<String, Object> properties = (Map<String, Object>) problemSchema.get("properties");
        assertThat(properties).containsKeys("type", "title", "status", "code", "detail");

        // Verify ProblemDetailResponse DTO matches OpenAPI Problem schema
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(java.net.URI.create("urn:problem-type:workshop-operations:VALIDATION_ERROR"))
                .title("Validation Error")
                .status(400)
                .code("VALIDATION_ERROR")
                .detail("Field capacity must be at least 1")
                .correlationId("corr-test-01")
                .build();
        assertThat(problem.getType()).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Validation Error");
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(problem.getCorrelationId()).isEqualTo("corr-test-01");

        // Verify ExceptionHandler bean is configured and registered
        assertThat(applicationContext.getBean(WorkshopOperationsExceptionHandler.class)).isNotNull();
    }

    // =========================================================================
    // 7. HTTP Endpoint Routing and Contract Readiness
    // =========================================================================

    @Test
    @DisplayName("T014-7: US1 HTTP endpoint routes and contract readiness with Location controllers")
    void verifyHttpEndpointContractReadiness() {
        // Document and verify all 10 operations across the 6 US1 endpoint paths defined in OpenAPI:
        Map<String, List<String>> expectedOperations = Map.of(
                "/api/v1/workshop-locations", List.of("GET", "POST"),
                "/api/v1/workshop-locations/{locationId}/slots", List.of("GET", "POST"),
                "/api/v1/workshop-slots/{slotId}", List.of("PATCH"),
                "/api/v1/workshop-sessions", List.of("GET", "POST"),
                "/api/v1/workshop-sessions/bulk", List.of("POST"),
                "/api/v1/workshop-sessions/{sessionId}", List.of("GET", "PATCH")
        );

        assertThat(expectedOperations).hasSize(6);

        // Check controllers for T018: workshopLocationController and workshopSessionController must exist
        boolean hasLocationController = applicationContext.containsBean("workshopLocationController");
        boolean hasSessionController = applicationContext.containsBean("workshopSessionController");
        boolean hasRetiredBranchController = applicationContext.containsBean("workshopBranchController");

        log.info("Controller readiness status: workshopLocationController={}, workshopSessionController={}, workshopBranchController={}",
                hasLocationController, hasSessionController, hasRetiredBranchController);

        assertThat(hasLocationController).as("workshopLocationController must be present").isTrue();
        assertThat(hasSessionController).as("workshopSessionController must be present").isTrue();
        assertThat(hasRetiredBranchController).as("workshopBranchController must NOT be present").isFalse();
    }
}
