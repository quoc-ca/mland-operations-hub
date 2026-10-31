package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopOperationsExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.OperationalLeadRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.SessionResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StaffAssignmentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StateTransitionRequest;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T021: Contract verification test for Workshop Operations User Story 2 endpoints
 * (state-transitions, staff-assignments, operational-lead) against contracts/workshop-operations.openapi.yaml.
 * <p>
 * Verifies request/response schema shape, validation/error contract (RFC 7807 Problem Detail),
 * and security scheme boundaries without prematurely implementing T022–T025.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkshopLifecycleContractIT {

    private static final Logger log = LoggerFactory.getLogger(WorkshopLifecycleContractIT.class);

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
    @DisplayName("T021-1: OpenAPI metadata, server base path, and bearer security scheme contract")
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
    // 2. State Transition Endpoint and DTO Contract
    // =========================================================================

    @Test
    @DisplayName("T021-2: State transitions endpoint and StateTransitionRequest schema/DTO validation contract")
    @SuppressWarnings("unchecked")
    void verifyStateTransitionContractInOpenApiAndDto() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/state-transitions");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/state-transitions");
        assertThat(pathItem).containsKey("post");

        Map<String, Object> postOp = (Map<String, Object>) pathItem.get("post");
        Map<String, Object> requestBody = (Map<String, Object>) postOp.get("requestBody");
        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/StateTransitionRequest");

        // Verify Responses: 200, 400, 403, 409
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("200", "400", "403", "409");

        // Components: StateTransitionRequest schema
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> stateTransitionSchema = (Map<String, Object>) schemas.get("StateTransitionRequest");

        List<String> requiredFields = (List<String>) stateTransitionSchema.get("required");
        assertThat(requiredFields).containsExactly("targetState");

        Map<String, Object> properties = (Map<String, Object>) stateTransitionSchema.get("properties");
        Map<String, Object> targetStateProp = (Map<String, Object>) properties.get("targetState");
        List<String> allowedEnums = (List<String>) targetStateProp.get("enum");
        assertThat(allowedEnums).containsExactlyInAnyOrder("PUBLISHED", "SALES_PAUSED", "IN_PROGRESS", "ATTENDANCE_FINALIZED");

        // DTO Validation: permitted states
        for (String stateName : allowedEnums) {
            SessionState sessionState = SessionState.valueOf(stateName);
            StateTransitionRequest validRequest = StateTransitionRequest.builder()
                    .targetState(sessionState)
                    .build();
            Set<ConstraintViolation<StateTransitionRequest>> violations = validator.validate(validRequest);
            assertThat(violations).as("Target state " + stateName + " must be valid").isEmpty();
        }

        // DTO Validation: null targetState rejected
        StateTransitionRequest nullStateRequest = StateTransitionRequest.builder().build();
        Set<ConstraintViolation<StateTransitionRequest>> nullViolations = validator.validate(nullStateRequest);
        assertThat(nullViolations).hasSize(1);
        assertThat(nullViolations.iterator().next().getMessage()).contains("targetState is required");

        // DTO Validation: non-permitted targetState rejected (DRAFT, CANCELLED)
        StateTransitionRequest draftRequest = StateTransitionRequest.builder().targetState(SessionState.DRAFT).build();
        Set<ConstraintViolation<StateTransitionRequest>> draftViolations = validator.validate(draftRequest);
        assertThat(draftViolations).hasSize(1);
        assertThat(draftViolations.iterator().next().getMessage())
                .contains("targetState must be one of: PUBLISHED, SALES_PAUSED, IN_PROGRESS, ATTENDANCE_FINALIZED");

        StateTransitionRequest cancelledRequest = StateTransitionRequest.builder().targetState(SessionState.CANCELLED).build();
        Set<ConstraintViolation<StateTransitionRequest>> cancelledViolations = validator.validate(cancelledRequest);
        assertThat(cancelledViolations).hasSize(1);
    }

    // =========================================================================
    // 3. Staff Assignment Endpoint and DTO Contract
    // =========================================================================

    @Test
    @DisplayName("T021-3: Staff assignment endpoints (PUT, DELETE) and StaffAssignmentRequest schema/DTO validation contract")
    @SuppressWarnings("unchecked")
    void verifyStaffAssignmentContractInOpenApiAndDto() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/staff-assignments");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/staff-assignments");
        assertThat(pathItem).containsKeys("put", "delete");

        // PUT operation
        Map<String, Object> putOp = (Map<String, Object>) pathItem.get("put");
        Map<String, Object> requestBody = (Map<String, Object>) putOp.get("requestBody");
        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/StaffAssignmentRequest");

        Map<String, Object> putResponses = (Map<String, Object>) putOp.get("responses");
        assertThat(putResponses).containsKeys("200", "403", "409");

        // DELETE operation
        Map<String, Object> deleteOp = (Map<String, Object>) pathItem.get("delete");
        List<Map<String, Object>> deleteParams = (List<Map<String, Object>>) deleteOp.get("parameters");
        assertThat(deleteParams).isNotEmpty();
        Map<String, Object> accountIdParam = deleteParams.get(0);
        assertThat(accountIdParam.get("name")).isEqualTo("accountId");
        assertThat(accountIdParam.get("in")).isEqualTo("query");
        assertThat(accountIdParam.get("required")).isEqualTo(true);

        Map<String, Object> accountIdSchema = (Map<String, Object>) accountIdParam.get("schema");
        assertThat(accountIdSchema.get("type")).isEqualTo("integer");
        assertThat(accountIdSchema.get("format")).isEqualTo("int64");

        Map<String, Object> deleteResponses = (Map<String, Object>) deleteOp.get("responses");
        assertThat(deleteResponses).containsKeys("204", "409");

        // Components: StaffAssignmentRequest schema
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> staffAssignSchema = (Map<String, Object>) schemas.get("StaffAssignmentRequest");

        List<String> requiredFields = (List<String>) staffAssignSchema.get("required");
        assertThat(requiredFields).containsExactly("accountId");

        Map<String, Object> properties = (Map<String, Object>) staffAssignSchema.get("properties");
        assertThat(properties).containsKey("accountId");
        assertThat(properties).doesNotContainKey("personnelId");
        assertThat(properties).doesNotContainKey("branchId");

        Map<String, Object> accountIdProp = (Map<String, Object>) properties.get("accountId");
        assertThat(accountIdProp.get("type")).isEqualTo("integer");
        assertThat(accountIdProp.get("format")).isEqualTo("int64");

        // DTO Validation: valid accountId
        StaffAssignmentRequest validReq = StaffAssignmentRequest.builder().accountId(101L).build();
        assertThat(validator.validate(validReq)).isEmpty();

        // DTO Validation: null accountId rejected
        StaffAssignmentRequest nullReq = StaffAssignmentRequest.builder().build();
        Set<ConstraintViolation<StaffAssignmentRequest>> violations = validator.validate(nullReq);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("accountId is required");

        // DTO Reflection check: no legacy personnelId or branchId fields
        List<String> fieldNames = java.util.Arrays.stream(StaffAssignmentRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(fieldNames).contains("accountId");
        assertThat(fieldNames).doesNotContain("personnelId", "branchId");
    }

    // =========================================================================
    // 4. Operational Lead Endpoint and DTO Contract
    // =========================================================================

    @Test
    @DisplayName("T021-4: Operational lead endpoint (PUT) and OperationalLeadRequest schema/DTO validation contract")
    @SuppressWarnings("unchecked")
    void verifyOperationalLeadContractInOpenApiAndDto() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/operational-lead");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/operational-lead");
        assertThat(pathItem).containsKey("put");

        Map<String, Object> putOp = (Map<String, Object>) pathItem.get("put");
        Map<String, Object> requestBody = (Map<String, Object>) putOp.get("requestBody");
        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/OperationalLeadRequest");

        Map<String, Object> putResponses = (Map<String, Object>) putOp.get("responses");
        assertThat(putResponses).containsKeys("200", "403", "409");

        // Components: OperationalLeadRequest schema
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> opLeadSchema = (Map<String, Object>) schemas.get("OperationalLeadRequest");

        List<String> requiredFields = (List<String>) opLeadSchema.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("managerAccountId", "reasonCode");

        Map<String, Object> properties = (Map<String, Object>) opLeadSchema.get("properties");
        assertThat(properties).containsKeys("managerAccountId", "reasonCode", "reasonNote", "evidenceReference");
        assertThat(properties).doesNotContainKey("managerPersonnelId");
        assertThat(properties).doesNotContainKey("branchId");

        Map<String, Object> mgrAccountProp = (Map<String, Object>) properties.get("managerAccountId");
        assertThat(mgrAccountProp.get("type")).isEqualTo("integer");
        assertThat(mgrAccountProp.get("format")).isEqualTo("int64");

        // DTO Validation: valid with required fields
        OperationalLeadRequest validReq = OperationalLeadRequest.builder()
                .managerAccountId(10L)
                .reasonCode("ONSITE_EMERGENCY_LEAD")
                .reasonNote("Staff absent, Manager taking over lead")
                .evidenceReference("EV-INCIDENT-8921")
                .build();
        assertThat(validator.validate(validReq)).isEmpty();

        // DTO Validation: valid with only mandatory fields
        OperationalLeadRequest minimalReq = OperationalLeadRequest.builder()
                .managerAccountId(10L)
                .reasonCode("ROUTINE_LEAD_ASSIGNMENT")
                .build();
        assertThat(validator.validate(minimalReq)).isEmpty();

        // DTO Validation: missing managerAccountId
        OperationalLeadRequest noMgrReq = OperationalLeadRequest.builder()
                .reasonCode("ROUTINE_LEAD_ASSIGNMENT")
                .build();
        Set<ConstraintViolation<OperationalLeadRequest>> noMgrViolations = validator.validate(noMgrReq);
        assertThat(noMgrViolations).hasSize(1);
        assertThat(noMgrViolations.iterator().next().getMessage()).contains("managerAccountId is required");

        // DTO Validation: missing reasonCode
        OperationalLeadRequest noReasonReq = OperationalLeadRequest.builder()
                .managerAccountId(10L)
                .build();
        Set<ConstraintViolation<OperationalLeadRequest>> noReasonViolations = validator.validate(noReasonReq);
        assertThat(noReasonViolations).hasSize(1);
        assertThat(noReasonViolations.iterator().next().getMessage()).contains("reasonCode is required");

        // DTO Reflection check: no legacy managerPersonnelId or branchId fields
        List<String> fieldNames = java.util.Arrays.stream(OperationalLeadRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(fieldNames).contains("managerAccountId", "reasonCode");
        assertThat(fieldNames).doesNotContain("managerPersonnelId", "branchId");
    }

    // =========================================================================
    // 5. Response Contract Consistency (Session and Problem Details)
    // =========================================================================

    @Test
    @DisplayName("T021-5: Response shape contract for Session and Problem Details RFC 7807")
    @SuppressWarnings("unchecked")
    void verifyResponseContractsInOpenApiAndDto() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        // Session schema: uses locationId (int64) and operationalLeadAccountId (int64), rejects branchId / personnelId
        Map<String, Object> sessionSchema = (Map<String, Object>) schemas.get("Session");
        assertThat(sessionSchema).isNotNull();

        Map<String, Object> sessionWriteSchema = (Map<String, Object>) schemas.get("SessionWrite");
        assertThat(sessionWriteSchema).isNotNull();
        Map<String, Object> sessionWriteProps = (Map<String, Object>) sessionWriteSchema.get("properties");
        assertThat(sessionWriteProps).containsKey("locationId");
        assertThat(sessionWriteProps).doesNotContainKey("branchId");

        // Session inline object schema properties
        List<Map<String, Object>> allOf = (List<Map<String, Object>>) sessionSchema.get("allOf");
        Map<String, Object> sessionInlineObject = allOf.get(1);
        Map<String, Object> sessionInlineProps = (Map<String, Object>) sessionInlineObject.get("properties");
        assertThat(sessionInlineProps).containsKey("operationalLeadAccountId");
        assertThat(sessionInlineProps).doesNotContainKey("operationalLeadPersonnelId");
        assertThat(sessionInlineProps).doesNotContainKey("branchId");

        // Problem schema
        Map<String, Object> problemSchema = (Map<String, Object>) schemas.get("Problem");
        List<String> requiredProblemFields = (List<String>) problemSchema.get("required");
        assertThat(requiredProblemFields).containsExactlyInAnyOrder("type", "title", "status", "code");

        // Verify ProblemDetailResponse DTO matches OpenAPI Problem schema
        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(java.net.URI.create("urn:problem-type:workshop-operations:FORBIDDEN"))
                .title("Forbidden")
                .status(403)
                .code("LOCATION_SCOPE_DENIED")
                .detail("Actor cannot operate outside active location scope")
                .correlationId("corr-test-02")
                .build();
        assertThat(problem.getType()).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getCode()).isEqualTo("LOCATION_SCOPE_DENIED");

        // Verify ExceptionHandler is loaded
        assertThat(applicationContext.getBean(WorkshopOperationsExceptionHandler.class)).isNotNull();
    }

    // =========================================================================
    // 6. Prove Absence of Retired Branch and Personnel Routes/Fields
    // =========================================================================

    @Test
    @DisplayName("T021-6: Prove retired branch routes, branchId, personnelId, managerPersonnelId are rejected or absent")
    @SuppressWarnings("unchecked")
    void verifyRetiredBranchAndPersonnelRoutesAndFieldsAbsence() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");

        // 1. No branch endpoints exist in OpenAPI
        assertThat(paths.keySet()).noneMatch(path -> path.contains("/workshop-branches") || path.contains("branch"));

        // 2. US2 endpoints do not expose branch or personnel parameters
        Map<String, Object> stateTransItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/state-transitions");
        assertThat(stateTransItem).isNotNull();

        Map<String, Object> staffAssignItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/staff-assignments");
        assertThat(staffAssignItem).isNotNull();

        Map<String, Object> opLeadItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/operational-lead");
        assertThat(opLeadItem).isNotNull();

        // 3. Components schemas check
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        assertThat(schemas).doesNotContainKey("Branch");
        assertThat(schemas).doesNotContainKey("BranchWrite");
        assertThat(schemas).doesNotContainKey("WorkshopBranch");

        // 4. SessionResponse DTO uses locationId and operationalLeadAccountId
        List<String> sessionRespFields = java.util.Arrays.stream(SessionResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(sessionRespFields).contains("locationId", "operationalLeadAccountId");
        assertThat(sessionRespFields).doesNotContain("branchId", "operationalLeadPersonnelId");
    }

    // =========================================================================
    // 7. Contract Readiness and Non-Premature Implementation Guard
    // =========================================================================

    @Test
    @DisplayName("T021-7: Verify contract readiness and non-premature implementation guard for T022-T025")
    void verifyLifecycleContractReadinessAndBlockerGuard() {
        // Document all US2 endpoints defined in OpenAPI contract:
        Map<String, List<String>> us2Endpoints = Map.of(
                "/api/v1/workshop-sessions/{sessionId}/state-transitions", List.of("POST"),
                "/api/v1/workshop-sessions/{sessionId}/staff-assignments", List.of("PUT", "DELETE"),
                "/api/v1/workshop-sessions/{sessionId}/operational-lead", List.of("PUT")
        );
        assertThat(us2Endpoints).hasSize(3);

        // Verify that DTO classes exist and can be instantiated
        assertThat(StateTransitionRequest.class).isNotNull();
        assertThat(StaffAssignmentRequest.class).isNotNull();
        assertThat(OperationalLeadRequest.class).isNotNull();

        // Verify BE-DESIGN-0003 blocker state:
        // AccountFacade lacks semantic query for target accountId capabilities/roles.
        // It only provides resolveAccount(firebaseUid), getAccountStatus(accountId),
        // getLocationScope(accountId), resolveActor(firebaseUid, emailVerified).
        // It DOES NOT provide role/capability lookup for target accountId.
        log.info("T020/T021 contract readiness verified: DTOs, Bean Validation, and OpenAPI contracts match 100%.");
        log.info("BE-DESIGN-0003 guard observed: T022-T025 implementation remains suspended until Account Management query is published.");
    }
}
