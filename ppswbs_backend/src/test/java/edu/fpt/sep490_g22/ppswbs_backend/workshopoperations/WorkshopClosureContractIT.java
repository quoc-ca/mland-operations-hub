package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopCheckInController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopSessionController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
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
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T042: Contract verification test for Workshop Operations User Story 5
 * (session attendance finalization, check-in reversal, post-finalization correction,
 * and cancellation request with 200 vs 202 handoff boundary)
 * against contracts/workshop-operations.openapi.yaml.
 * <p>
 * Verifies OpenAPI endpoint contracts, request/response schema composition,
 * Bean Validation rules (mandatory reasons, note/evidence enforcement, post-finalization
 * outcomes limited strictly to CHECKED_IN or NO_SHOW), privacy and terminology isolation,
 * RFC 7807 Problem Detail, and phase boundary gates proving T043–T050 are not yet implemented.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkshopClosureContractIT {

    private static final Logger log = LoggerFactory.getLogger(WorkshopClosureContractIT.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("T042-1: OpenAPI metadata, server base path, and bearer security scheme contract")
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
    // 2. Attendance Finalization Endpoint Contract in OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T042-2: Attendance finalization endpoint path, parameters, and response status codes")
    @SuppressWarnings("unchecked")
    void verifyAttendanceFinalizationEndpointContractInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/attendance-finalizations");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/attendance-finalizations");
        assertThat(pathItem).containsKey("post");

        // Session ID path parameter contract
        List<Map<String, Object>> pathParams = (List<Map<String, Object>>) pathItem.get("parameters");
        assertThat(pathParams).isNotEmpty();
        assertThat(pathParams.get(0).get("$ref")).isEqualTo("#/components/parameters/SessionId");

        Map<String, Object> postOp = (Map<String, Object>) pathItem.get("post");
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("200", "403", "409");

        Map<String, Object> res200 = (Map<String, Object>) responses.get("200");
        assertThat(res200.get("$ref")).isEqualTo("#/components/responses/Session");
    }

    // =========================================================================
    // 3. Check-In Reversal Endpoint Contract in OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T042-3: Check-in reversal endpoint path, parameters, request body, and response status codes")
    @SuppressWarnings("unchecked")
    void verifyCheckInReversalEndpointContractInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/check-in-reversals");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/check-in-reversals");
        assertThat(pathItem).containsKey("post");

        // Session ID path parameter contract
        List<Map<String, Object>> pathParams = (List<Map<String, Object>>) pathItem.get("parameters");
        assertThat(pathParams).isNotEmpty();
        assertThat(pathParams.get(0).get("$ref")).isEqualTo("#/components/parameters/SessionId");

        Map<String, Object> postOp = (Map<String, Object>) pathItem.get("post");
        Map<String, Object> requestBody = (Map<String, Object>) postOp.get("requestBody");
        assertThat(requestBody.get("required")).isEqualTo(true);

        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/CheckInReversalRequest");

        // Response status codes contract: 200 (Ticket), 403 (Problem), 409 (Problem)
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("200", "403", "409");

        Map<String, Object> res200 = (Map<String, Object>) responses.get("200");
        assertThat(res200.get("$ref")).isEqualTo("#/components/responses/Ticket");
    }

    // =========================================================================
    // 4. Attendance Correction Endpoint Contract in OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T042-4: Attendance correction endpoint path, parameters, request body, and response status codes")
    @SuppressWarnings("unchecked")
    void verifyAttendanceCorrectionEndpointContractInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/attendance-corrections");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/attendance-corrections");
        assertThat(pathItem).containsKey("post");

        // Session ID path parameter contract
        List<Map<String, Object>> pathParams = (List<Map<String, Object>>) pathItem.get("parameters");
        assertThat(pathParams).isNotEmpty();
        assertThat(pathParams.get(0).get("$ref")).isEqualTo("#/components/parameters/SessionId");

        Map<String, Object> postOp = (Map<String, Object>) pathItem.get("post");
        Map<String, Object> requestBody = (Map<String, Object>) postOp.get("requestBody");
        assertThat(requestBody.get("required")).isEqualTo(true);

        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/AttendanceCorrectionRequest");

        // Response status codes contract: 200 (Ticket), 403 (Problem), 409 (Problem)
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("200", "403", "409");

        Map<String, Object> res200 = (Map<String, Object>) responses.get("200");
        assertThat(res200.get("$ref")).isEqualTo("#/components/responses/Ticket");
    }

    // =========================================================================
    // 5. Cancellation Requests Endpoint and 200 vs 202 Semantic Boundary in OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T042-5: Cancellation endpoint contract and 200 CANCELLED vs 202 handoff semantic boundary")
    @SuppressWarnings("unchecked")
    void verifyCancellationEndpointAnd200vs202SemanticBoundaryInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/cancellation-requests");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/cancellation-requests");
        assertThat(pathItem).containsKey("post");

        // Session ID path parameter contract
        List<Map<String, Object>> pathParams = (List<Map<String, Object>>) pathItem.get("parameters");
        assertThat(pathParams).isNotEmpty();
        assertThat(pathParams.get(0).get("$ref")).isEqualTo("#/components/parameters/SessionId");

        Map<String, Object> postOp = (Map<String, Object>) pathItem.get("post");
        Map<String, Object> requestBody = (Map<String, Object>) postOp.get("requestBody");
        assertThat(requestBody.get("required")).isEqualTo(true);

        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/ReasonedRequest");

        // Response status codes contract: 200, 202, 403, 409
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("200", "202", "403", "409");

        // 200 OK: direct cancellation returns Session representation (terminal CANCELLED)
        Map<String, Object> res200 = (Map<String, Object>) responses.get("200");
        assertThat(res200.get("$ref")).isEqualTo("#/components/responses/Session");

        // 202 Accepted: cancellation handoff created (delegated to 003.3)
        Map<String, Object> res202 = (Map<String, Object>) responses.get("202");
        assertThat(res202.get("description").toString()).contains("Cancellation handoff created");
        assertThat(res202).doesNotContainKey("content"); // Handoff returns no body / no refund details

        // Verify non-leakage of refund, reschedule, settlement, or notification mechanics in Operations contract
        String specString = openApiSpec.toString().toLowerCase();
        assertThat(specString).doesNotContain("/refunds");
        assertThat(specString).doesNotContain("/settlements");
        assertThat(specString).doesNotContain("/reschedules");
    }

    // =========================================================================
    // 6. CheckInReversalRequest Schema and Bean Validation Contract
    // =========================================================================

    @Test
    @DisplayName("T042-6: CheckInReversalRequest schema composition and Bean Validation contract")
    @SuppressWarnings("unchecked")
    void verifyCheckInReversalRequestValidationAndSchema() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> reversalSchema = (Map<String, Object>) schemas.get("CheckInReversalRequest");
        assertThat(reversalSchema).isNotNull();

        List<Map<String, Object>> allOf = (List<Map<String, Object>>) reversalSchema.get("allOf");
        assertThat(allOf).hasSize(2);
        assertThat(allOf.get(0).get("$ref")).isEqualTo("#/components/schemas/ReasonedRequest");

        // 6.1: Valid reversal request
        CheckInReversalRequest valid = CheckInReversalRequest.builder()
                .ticketCode("TK-REV-001")
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Staff scanned incorrect attendee QR code by mistake")
                .build();
        Set<ConstraintViolation<CheckInReversalRequest>> validViolations = validator.validate(valid);
        assertThat(validViolations).isEmpty();

        // 6.2: Missing ticketCode rejected
        CheckInReversalRequest missingTicket = CheckInReversalRequest.builder()
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Scanned incorrect QR")
                .build();
        Set<ConstraintViolation<CheckInReversalRequest>> missingTicketViolations = validator.validate(missingTicket);
        assertThat(missingTicketViolations).anyMatch(v -> v.getMessage().contains("ticketCode is required"));

        // 6.3: Missing reasonCode rejected
        CheckInReversalRequest missingReason = CheckInReversalRequest.builder()
                .ticketCode("TK-REV-001")
                .build();
        Set<ConstraintViolation<CheckInReversalRequest>> missingReasonViolations = validator.validate(missingReason);
        assertThat(missingReasonViolations).anyMatch(v -> v.getMessage().contains("reasonCode is required"));

        // 6.4: Unrecognized ad-hoc reason code rejected
        CheckInReversalRequest adHocReason = CheckInReversalRequest.builder()
                .ticketCode("TK-REV-001")
                .reasonCode("CUSTOM_UNAPPROVED_REVERSAL_REASON")
                .build();
        Set<ConstraintViolation<CheckInReversalRequest>> adHocViolations = validator.validate(adHocReason);
        assertThat(adHocViolations).anyMatch(v -> v.getMessage().contains("reasonCode must belong to the standardized system catalogue"));

        // 6.5: OTHER_REQUIRES_NOTE requires descriptive note or evidenceReference
        CheckInReversalRequest otherMissingNote = CheckInReversalRequest.builder()
                .ticketCode("TK-REV-001")
                .reasonCode("OTHER_REQUIRES_NOTE")
                .build();
        Set<ConstraintViolation<CheckInReversalRequest>> otherMissingViolations = validator.validate(otherMissingNote);
        assertThat(otherMissingViolations).anyMatch(v -> v.getMessage().contains("descriptive reasonNote or evidenceReference is required"));
    }

    // =========================================================================
    // 7. AttendanceCorrectionRequest Schema and Bean Validation Contract
    // =========================================================================

    @Test
    @DisplayName("T042-7: AttendanceCorrectionRequest schema, required fields, and CHECKED_IN/NO_SHOW restriction")
    @SuppressWarnings("unchecked")
    void verifyAttendanceCorrectionRequestValidationAndSchema() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> correctionSchema = (Map<String, Object>) schemas.get("AttendanceCorrectionRequest");
        assertThat(correctionSchema).isNotNull();

        List<Map<String, Object>> allOf = (List<Map<String, Object>>) correctionSchema.get("allOf");
        assertThat(allOf).hasSize(2);
        assertThat(allOf.get(0).get("$ref")).isEqualTo("#/components/schemas/ReasonedRequest");

        // 7.1: Valid correction to CHECKED_IN
        AttendanceCorrectionRequest validCheckedIn = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-CORR-001")
                .correctedAttendanceState(AttendanceState.CHECKED_IN)
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Customer was present but phone battery died before check-in")
                .build();
        Set<ConstraintViolation<AttendanceCorrectionRequest>> validCheckedInViolations = validator.validate(validCheckedIn);
        assertThat(validCheckedInViolations).isEmpty();

        // 7.2: Valid correction to NO_SHOW
        AttendanceCorrectionRequest validNoShow = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-CORR-002")
                .correctedAttendanceState(AttendanceState.NO_SHOW)
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Accidentally marked checked in during session wrap-up")
                .build();
        Set<ConstraintViolation<AttendanceCorrectionRequest>> validNoShowViolations = validator.validate(validNoShow);
        assertThat(validNoShowViolations).isEmpty();

        // 7.3: Disallowed state NOT_CHECKED_IN is rejected (must only be CHECKED_IN or NO_SHOW)
        AttendanceCorrectionRequest invalidState = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-CORR-003")
                .correctedAttendanceState(AttendanceState.NOT_CHECKED_IN)
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Attempting to reset to not checked in")
                .build();
        Set<ConstraintViolation<AttendanceCorrectionRequest>> invalidStateViolations = validator.validate(invalidState);
        assertThat(invalidStateViolations).anyMatch(v -> v.getMessage().contains("correctedAttendanceState must be either CHECKED_IN or NO_SHOW"));

        // 7.4: Missing ticketCode rejected
        AttendanceCorrectionRequest missingTicket = AttendanceCorrectionRequest.builder()
                .correctedAttendanceState(AttendanceState.CHECKED_IN)
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Valid note")
                .build();
        Set<ConstraintViolation<AttendanceCorrectionRequest>> missingTicketViolations = validator.validate(missingTicket);
        assertThat(missingTicketViolations).anyMatch(v -> v.getMessage().contains("ticketCode is required"));

        // 7.5: Missing correctedAttendanceState rejected
        AttendanceCorrectionRequest missingState = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-CORR-004")
                .reasonCode("RECORDING_ERROR")
                .reasonNote("Valid note")
                .build();
        Set<ConstraintViolation<AttendanceCorrectionRequest>> missingStateViolations = validator.validate(missingState);
        assertThat(missingStateViolations).anyMatch(v -> v.getMessage().contains("correctedAttendanceState is required"));

        // 7.6: Missing reasonCode rejected
        AttendanceCorrectionRequest missingReason = AttendanceCorrectionRequest.builder()
                .ticketCode("TK-CORR-005")
                .correctedAttendanceState(AttendanceState.CHECKED_IN)
                .build();
        Set<ConstraintViolation<AttendanceCorrectionRequest>> missingReasonViolations = validator.validate(missingReason);
        assertThat(missingReasonViolations).anyMatch(v -> v.getMessage().contains("reasonCode is required"));
    }

    // =========================================================================
    // 8. SessionCancellationRequest Schema and Validation Contract
    // =========================================================================

    @Test
    @DisplayName("T042-8: SessionCancellationRequest inherits ReasonedRequest and enforces reason/evidence rules")
    void verifySessionCancellationRequestValidationAndSchema() {
        // 8.1: Valid cancellation request with recognized standardized reason
        SessionCancellationRequest valid = SessionCancellationRequest.builder()
                .reasonCode("INSTRUCTOR_UNAVAILABLE")
                .reasonNote("Lead craftsperson hospitalized due to medical emergency")
                .evidenceReference("MED-CERT-20261028-01")
                .build();
        Set<ConstraintViolation<SessionCancellationRequest>> validViolations = validator.validate(valid);
        assertThat(validViolations).isEmpty();

        // 8.2: Missing reasonCode rejected
        SessionCancellationRequest missingReason = SessionCancellationRequest.builder().build();
        Set<ConstraintViolation<SessionCancellationRequest>> missingReasonViolations = validator.validate(missingReason);
        assertThat(missingReasonViolations).anyMatch(v -> v.getMessage().contains("reasonCode is required"));

        // 8.3: Unrecognized reason code rejected
        SessionCancellationRequest adHocReason = SessionCancellationRequest.builder()
                .reasonCode("UNAUTHORIZED_CANCELLATION_REASON")
                .build();
        Set<ConstraintViolation<SessionCancellationRequest>> adHocViolations = validator.validate(adHocReason);
        assertThat(adHocViolations).anyMatch(v -> v.getMessage().contains("reasonCode must belong to the standardized system catalogue"));

        // 8.4: OTHER_REQUIRES_NOTE requires descriptive note or evidence
        SessionCancellationRequest otherMissing = SessionCancellationRequest.builder()
                .reasonCode("OTHER_REQUIRES_NOTE")
                .build();
        Set<ConstraintViolation<SessionCancellationRequest>> otherMissingViolations = validator.validate(otherMissing);
        assertThat(otherMissingViolations).anyMatch(v -> v.getMessage().contains("descriptive reasonNote or evidenceReference is required"));

        SessionCancellationRequest otherWithNote = SessionCancellationRequest.builder()
                .reasonCode("OTHER_REQUIRES_NOTE")
                .reasonNote("Power grid failure affecting whole shopping mall district")
                .build();
        Set<ConstraintViolation<SessionCancellationRequest>> otherValidViolations = validator.validate(otherWithNote);
        assertThat(otherValidViolations).isEmpty();
    }

    // =========================================================================
    // 9. AttendanceFinalizationRequest Safe Defaults and Validation
    // =========================================================================

    @Test
    @DisplayName("T042-9: AttendanceFinalizationRequest safe defaults and confirmation property")
    void verifyAttendanceFinalizationRequestDefaultsAndValidation() {
        AttendanceFinalizationRequest defaultReq = new AttendanceFinalizationRequest();
        assertThat(defaultReq.getConfirmedSessionEnded()).isTrue();

        AttendanceFinalizationRequest builtReq = AttendanceFinalizationRequest.builder()
                .confirmedSessionEnded(true)
                .note("All participants finished ring polishing")
                .build();
        assertThat(builtReq.getConfirmedSessionEnded()).isTrue();
        assertThat(builtReq.getNote()).isEqualTo("All participants finished ring polishing");
    }

    // =========================================================================
    // 10. Privacy Boundary and Terminology Isolation across all Closure DTOs
    // =========================================================================

    @Test
    @DisplayName("T042-10: Closure DTOs contain NO PII, contact data, tokens, QR values, or legacy terminology")
    void verifyClosureDtosPrivacyAndTerminology() {
        List<Class<?>> closureDtoClasses = List.of(
                AttendanceFinalizationRequest.class,
                CheckInReversalRequest.class,
                AttendanceCorrectionRequest.class,
                SessionCancellationRequest.class
        );

        for (Class<?> dtoClass : closureDtoClasses) {
            List<Field> allFields = new ArrayList<>();
            Class<?> current = dtoClass;
            while (current != null && current != Object.class) {
                allFields.addAll(Arrays.asList(current.getDeclaredFields()));
                current = current.getSuperclass();
            }

            List<String> fieldNames = allFields.stream().map(Field::getName).toList();

            // Explicit non-disclosure assertions
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("email"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("phone"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("contact"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("token"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("qr"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("recipient"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("user"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("password"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("entity"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("metadata"));

            // Legacy branch and personnel terminology strictly forbidden
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("branch"));
            assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("personnel"));
        }
    }

    // =========================================================================
    // 11. Problem Detail RFC 7807 Response Schema Contract
    // =========================================================================

    @Test
    @DisplayName("T042-11: Problem Detail RFC 7807 schema and ProblemDetailResponse DTO contract for closure errors")
    @SuppressWarnings("unchecked")
    void verifyProblemDetailContract() throws Exception {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> problemSchema = (Map<String, Object>) schemas.get("Problem");
        assertThat(problemSchema).isNotNull();

        List<String> required = (List<String>) problemSchema.get("required");
        assertThat(required).containsExactlyInAnyOrder("type", "title", "status", "code");

        // 409 Conflict: Cannot finalize attendance when session is not IN_PROGRESS
        ProblemDetailResponse response409 = ProblemDetailResponse.builder()
                .type(URI.create("https://mland.edu.vn/errors/invalid-session-state"))
                .title("Invalid Session State for Finalization")
                .status(409)
                .code("INVALID_STATE")
                .detail("Session must be IN_PROGRESS to finalize attendance")
                .correlationId("corr-closure-409-test")
                .build();

        String json409 = objectMapper.writeValueAsString(response409);
        ProblemDetailResponse deserialized409 = objectMapper.readValue(json409, ProblemDetailResponse.class);

        assertThat(deserialized409.getStatus()).isEqualTo(409);
        assertThat(deserialized409.getCode()).isEqualTo("INVALID_STATE");

        // 403 Forbidden: Staff not permitted to cancel session or correct finalized attendance
        ProblemDetailResponse response403 = ProblemDetailResponse.builder()
                .type(URI.create("https://mland.edu.vn/errors/forbidden"))
                .title("Access Denied")
                .status(403)
                .code("FORBIDDEN")
                .detail("Only an authorized Manager may request session cancellation or correct finalized attendance")
                .correlationId("corr-closure-403-test")
                .build();

        String json403 = objectMapper.writeValueAsString(response403);
        ProblemDetailResponse deserialized403 = objectMapper.readValue(json403, ProblemDetailResponse.class);

        assertThat(deserialized403.getStatus()).isEqualTo(403);
        assertThat(deserialized403.getCode()).isEqualTo("FORBIDDEN");
    }

    // =========================================================================
    // 12. Phase Boundary: T043–T050 Services and Handlers NOT Prematurely Implemented
    // =========================================================================

    @Test
    @DisplayName("T042-12: Phase boundary proof - T043–T050 services and endpoints are NOT implemented prematurely")
    void verifyPhaseBoundaryNotPrematurelyImplemented() {
        // AttendanceFinalizationService / SessionClosureService (T043+) must NOT be registered beans
        assertThat(applicationContext.containsBean("attendanceFinalizationService"))
                .as("AttendanceFinalizationService must not be registered before T043+")
                .isFalse();

        assertThat(applicationContext.containsBean("sessionClosureService"))
                .as("SessionClosureService must not be registered before T043+")
                .isFalse();

        assertThat(applicationContext.containsBean("sessionCancellationService"))
                .as("SessionCancellationService must not be registered before T043+")
                .isFalse();

        // 003.3 Settlement Facade must NOT be registered in Operations context
        assertThat(applicationContext.containsBean("workshopSettlementFacade"))
                .as("WorkshopSettlementFacade from 003.3 must not exist in this phase")
                .isFalse();

        // WorkshopSessionController must not implement cancellation or finalization endpoints before T044
        Method[] sessionMethods = WorkshopSessionController.class.getDeclaredMethods();
        List<String> sessionMethodNames = Arrays.stream(sessionMethods).map(Method::getName).toList();
        assertThat(sessionMethodNames)
                .as("WorkshopSessionController must not implement cancellation-requests or attendance-finalizations before T044")
                .noneMatch(name -> name.toLowerCase().contains("cancellationrequest"))
                .noneMatch(name -> name.toLowerCase().contains("finalizeattendance"));

        // WorkshopCheckInController must not implement reversal or correction endpoints before T044
        Method[] checkInMethods = WorkshopCheckInController.class.getDeclaredMethods();
        List<String> checkInMethodNames = Arrays.stream(checkInMethods).map(Method::getName).toList();
        assertThat(checkInMethodNames)
                .as("WorkshopCheckInController must not implement check-in-reversals or attendance-corrections before T044")
                .noneMatch(name -> name.toLowerCase().contains("reversal"))
                .noneMatch(name -> name.toLowerCase().contains("correction"));
    }
}
