package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopCheckInController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInOverrideRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ReasonedRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.StandardizedReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.TicketResendRequest;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T035: Contract verification test for Workshop Operations User Story 4
 * (ticket resend, check-in override, standardized reason catalogue, and privacy boundary)
 * against contracts/workshop-operations.openapi.yaml.
 * <p>
 * Verifies request/response schema shape, Bean Validation rules (oneOf credentials, mandatory reason,
 * note/evidence requirements, failed-redelivery correlation), and RFC 7807 Problem Detail,
 * while strictly establishing phase boundaries preventing premature implementation of T036–T040.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TicketRecoveryContractIT {

    private static final Logger log = LoggerFactory.getLogger(TicketRecoveryContractIT.class);

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
    @DisplayName("T035-1: OpenAPI metadata, server base path, and bearer security scheme contract")
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
    // 2. Ticket Resend Endpoint Contract in OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T035-2: Ticket resend endpoint path, parameters, request body, and response status codes")
    @SuppressWarnings("unchecked")
    void verifyTicketResendEndpointContractInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/ticket-resends");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/ticket-resends");
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
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/TicketResendRequest");

        // Allowed response codes: 202 (accepted), 403 (Problem), 429 (Problem - rate limited)
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("202", "403", "429");
    }

    // =========================================================================
    // 3. TicketResendRequest DTO Validation and Schema Contract
    // =========================================================================

    @Test
    @DisplayName("T035-3: TicketResendRequest schema and Bean Validation contract")
    @SuppressWarnings("unchecked")
    void verifyTicketResendRequestValidationAndSchema() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> resendSchema = (Map<String, Object>) schemas.get("TicketResendRequest");
        assertThat(resendSchema).isNotNull();

        List<String> required = (List<String>) resendSchema.get("required");
        assertThat(required).containsExactly("bookingCode");

        Map<String, Object> props = (Map<String, Object>) resendSchema.get("properties");
        assertThat(props).containsKey("bookingCode");

        // Valid request with bookingCode
        TicketResendRequest valid = TicketResendRequest.builder()
                .bookingCode("BK-RESEND-001")
                .build();
        Set<ConstraintViolation<TicketResendRequest>> violations = validator.validate(valid);
        assertThat(violations).isEmpty();

        // Null bookingCode rejected
        TicketResendRequest nullCode = TicketResendRequest.builder().build();
        Set<ConstraintViolation<TicketResendRequest>> nullViolations = validator.validate(nullCode);
        assertThat(nullViolations).hasSize(1);
        assertThat(nullViolations.iterator().next().getMessage()).contains("bookingCode is required");

        // Blank bookingCode rejected
        TicketResendRequest blankCode = TicketResendRequest.builder().bookingCode("   ").build();
        Set<ConstraintViolation<TicketResendRequest>> blankViolations = validator.validate(blankCode);
        assertThat(blankViolations).hasSize(1);
        assertThat(blankViolations.iterator().next().getMessage()).contains("bookingCode is required");
    }

    // =========================================================================
    // 4. TicketResendRequest Privacy Boundary and Terminology Isolation
    // =========================================================================

    @Test
    @DisplayName("T035-4: TicketResendRequest contains no PII, contact info, tokens, or legacy terminology")
    void verifyTicketResendRequestPrivacyAndTerminology() {
        Field[] fields = TicketResendRequest.class.getDeclaredFields();
        List<String> fieldNames = Arrays.stream(fields).map(Field::getName).toList();

        // Must ONLY contain bookingCode
        assertThat(fieldNames).containsExactly("bookingCode");

        // Explicit non-disclosure assertions
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("email"));
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("phone"));
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("contact"));
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("token"));
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("qr"));
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("recipient"));

        // Legacy branch and personnel terminology forbidden
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("branch"));
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("personnel"));
    }

    // =========================================================================
    // 5. Check-In Override Endpoint Contract in OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T035-5: Check-in override endpoint path, parameters, request body, and response status codes")
    @SuppressWarnings("unchecked")
    void verifyCheckInOverrideEndpointContractInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/check-in-overrides");

        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/check-in-overrides");
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
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/CheckInOverrideRequest");

        // Allowed response codes: 200 (Ticket), 403 (Problem), 409 (Problem)
        Map<String, Object> responses = (Map<String, Object>) postOp.get("responses");
        assertThat(responses).containsKeys("200", "403", "409");

        Map<String, Object> res200 = (Map<String, Object>) responses.get("200");
        assertThat(res200.get("$ref")).isEqualTo("#/components/responses/Ticket");
    }

    // =========================================================================
    // 6. CheckInOverrideRequest Schema, Reason Catalogue, and Bean Validation Contract
    // =========================================================================

    @Test
    @DisplayName("T035-6: CheckInOverrideRequest schema, oneOf credential, standardized reason catalogue, and evidence validation contract")
    @SuppressWarnings("unchecked")
    void verifyCheckInOverrideRequestValidationAndSchema() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        // Schema composition check
        Map<String, Object> overrideSchema = (Map<String, Object>) schemas.get("CheckInOverrideRequest");
        assertThat(overrideSchema).isNotNull();
        List<Map<String, Object>> allOf = (List<Map<String, Object>>) overrideSchema.get("allOf");
        assertThat(allOf).hasSize(2);
        assertThat(allOf.get(0).get("$ref")).isEqualTo("#/components/schemas/CheckInRequest");
        assertThat(allOf.get(1).get("$ref")).isEqualTo("#/components/schemas/ReasonedRequest");

        // 6.1: OneOf credential validation (qrToken XOR ticketCode)
        CheckInOverrideRequest neitherCred = CheckInOverrideRequest.builder()
                .reasonCode("CUSTOMER_DEVICE_UNAVAILABLE")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> neitherViolations = validator.validate(neitherCred);
        assertThat(neitherViolations).anyMatch(v -> v.getMessage().contains("Exactly one of qrToken or ticketCode must be provided"));

        CheckInOverrideRequest bothCred = CheckInOverrideRequest.builder()
                .qrToken("QR-SAMPLE-001")
                .ticketCode("TK-12345678")
                .reasonCode("CUSTOMER_DEVICE_UNAVAILABLE")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> bothViolations = validator.validate(bothCred);
        assertThat(bothViolations).anyMatch(v -> v.getMessage().contains("Exactly one of qrToken or ticketCode must be provided"));

        // 6.2: ReasonCode required validation
        CheckInOverrideRequest missingReason = CheckInOverrideRequest.builder()
                .ticketCode("TK-12345678")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> missingReasonViolations = validator.validate(missingReason);
        assertThat(missingReasonViolations).anyMatch(v -> v.getMessage().contains("reasonCode is required"));

        // 6.3: Standardized Reason Catalogue check (unrecognized ad-hoc code rejected)
        CheckInOverrideRequest adHocReason = CheckInOverrideRequest.builder()
                .ticketCode("TK-12345678")
                .reasonCode("MY_CUSTOM_UNAPPROVED_REASON")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> adHocViolations = validator.validate(adHocReason);
        assertThat(adHocViolations).anyMatch(v -> v.getMessage().contains("reasonCode must belong to the standardized system catalogue"));

        // 6.4: TICKET_REDELIVERY_UNAVAILABLE requires note AND evidenceReference (failed-redelivery correlation reference)
        CheckInOverrideRequest redeliveryMissingAll = CheckInOverrideRequest.builder()
                .ticketCode("TK-12345678")
                .reasonCode("TICKET_REDELIVERY_UNAVAILABLE")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> redelivMissingAllViolations = validator.validate(redeliveryMissingAll);
        assertThat(redelivMissingAllViolations).anyMatch(v -> v.getMessage().contains("failed-redelivery correlation reference"));

        CheckInOverrideRequest redeliveryMissingEvidence = CheckInOverrideRequest.builder()
                .ticketCode("TK-12345678")
                .reasonCode("TICKET_REDELIVERY_UNAVAILABLE")
                .reasonNote("Customer confirms non-receipt of resend email")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> redelivMissingEvViolations = validator.validate(redeliveryMissingEvidence);
        assertThat(redelivMissingEvViolations).anyMatch(v -> v.getMessage().contains("failed-redelivery correlation reference"));

        CheckInOverrideRequest redeliveryValid = CheckInOverrideRequest.builder()
                .ticketCode("TK-12345678")
                .reasonCode("TICKET_REDELIVERY_UNAVAILABLE")
                .reasonNote("Customer confirms non-receipt of resend email")
                .evidenceReference("resend-delivery-failed-correlation-12345")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> redelivValidViolations = validator.validate(redeliveryValid);
        assertThat(redelivValidViolations).isEmpty();

        // 6.5: OTHER_REQUIRES_NOTE requires a descriptive note or evidence reference
        CheckInOverrideRequest otherMissingNote = CheckInOverrideRequest.builder()
                .ticketCode("TK-12345678")
                .reasonCode("OTHER_REQUIRES_NOTE")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> otherMissingViolations = validator.validate(otherMissingNote);
        assertThat(otherMissingViolations).anyMatch(v -> v.getMessage().contains("descriptive reasonNote or evidenceReference is required"));

        CheckInOverrideRequest otherWithNote = CheckInOverrideRequest.builder()
                .ticketCode("TK-12345678")
                .reasonCode("OTHER_REQUIRES_NOTE")
                .reasonNote("VIP guest escorted by Manager without mobile device")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> otherValidViolations = validator.validate(otherWithNote);
        assertThat(otherValidViolations).isEmpty();

        // 6.6: Valid with qrToken instead of ticketCode
        CheckInOverrideRequest qrValid = CheckInOverrideRequest.builder()
                .qrToken("QR-VALID-OPAQUE-TOKEN")
                .reasonCode("CUSTOMER_DEVICE_UNAVAILABLE")
                .build();
        Set<ConstraintViolation<CheckInOverrideRequest>> qrValidViolations = validator.validate(qrValid);
        assertThat(qrValidViolations).isEmpty();
    }

    // =========================================================================
    // 7. CheckInOverrideRequest Privacy and Terminology Boundary
    // =========================================================================

    @Test
    @DisplayName("T035-7: CheckInOverrideRequest contains no PII, contact info, raw entities, or legacy terminology")
    void verifyCheckInOverrideRequestPrivacyAndTerminology() {
        Field[] subclassFields = CheckInOverrideRequest.class.getDeclaredFields();
        Field[] superclassFields = ReasonedRequest.class.getDeclaredFields();

        List<String> allFieldNames = Arrays.stream(subclassFields).map(Field::getName).toList();
        List<String> superFieldNames = Arrays.stream(superclassFields).map(Field::getName).toList();

        assertThat(allFieldNames).containsExactlyInAnyOrder("qrToken", "ticketCode");
        assertThat(superFieldNames).containsExactlyInAnyOrder("reasonCode", "reasonNote", "evidenceReference");

        // Non-disclosure checks
        List<String> combined = new java.util.ArrayList<>(allFieldNames);
        combined.addAll(superFieldNames);

        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("email"));
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("phone"));
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("contact"));
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("user"));
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("password"));
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("entity"));
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("metadata"));

        // Legacy branch and personnel terminology forbidden
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("branch"));
        assertThat(combined).noneMatch(name -> name.toLowerCase().contains("personnel"));
    }

    // =========================================================================
    // 8. Problem Detail RFC 7807 Response Schema Contract
    // =========================================================================

    @Test
    @DisplayName("T035-8: Problem Detail RFC 7807 schema and ProblemDetailResponse DTO contract")
    @SuppressWarnings("unchecked")
    void verifyProblemDetailContract() throws Exception {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> problemSchema = (Map<String, Object>) schemas.get("Problem");
        assertThat(problemSchema).isNotNull();

        List<String> required = (List<String>) problemSchema.get("required");
        assertThat(required).containsExactlyInAnyOrder("type", "title", "status", "code");

        Map<String, Object> props = (Map<String, Object>) problemSchema.get("properties");
        assertThat(props).containsKeys("type", "title", "status", "code", "detail");

        // DTO mapping validation
        ProblemDetailResponse response = ProblemDetailResponse.builder()
                .type(URI.create("https://mland.edu.vn/errors/rate-limit-exceeded"))
                .title("Too Many Resend Requests")
                .status(429)
                .code("RATE_LIMIT_EXCEEDED")
                .detail("At most 3 resend requests per booking in 15 minutes are permitted")
                .correlationId("corr-resend-test-01")
                .build();

        String json = objectMapper.writeValueAsString(response);
        ProblemDetailResponse deserialized = objectMapper.readValue(json, ProblemDetailResponse.class);

        assertThat(deserialized.getStatus()).isEqualTo(429);
        assertThat(deserialized.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(deserialized.getTitle()).isEqualTo("Too Many Resend Requests");
        assertThat(deserialized.getType()).isEqualTo(URI.create("https://mland.edu.vn/errors/rate-limit-exceeded"));
    }

    // =========================================================================
    // 9. Phase Boundary: T036–T040 Unbuilt Services and Gate Verification
    // =========================================================================

    @Test
    @DisplayName("T035-9: Phase boundary proof - T036–T040 services and endpoints are NOT implemented prematurely")
    void verifyPhaseBoundaryNotPrematurelyImplemented() {
        // TicketResendService (T037) must NOT be a registered bean
        assertThat(applicationContext.containsBean("ticketResendService"))
                .as("TicketResendService must not be registered before T037")
                .isFalse();

        // CheckInOverrideService (T038) must NOT be a registered bean
        assertThat(applicationContext.containsBean("checkInOverrideService"))
                .as("CheckInOverrideService must not be registered before T038")
                .isFalse();

        // WorkshopBookingFacade (003.1 cross-feature facade) must NOT be a registered bean
        assertThat(applicationContext.containsBean("workshopBookingFacade"))
                .as("WorkshopBookingFacade from 003.1 must not exist in this phase")
                .isFalse();

        // WorkshopCheckInController (T039) must not have resend or override endpoint handler methods
        Method[] controllerMethods = WorkshopCheckInController.class.getDeclaredMethods();
        List<String> methodNames = Arrays.stream(controllerMethods).map(Method::getName).toList();
        assertThat(methodNames)
                .as("WorkshopCheckInController must not implement resend or override methods before T039")
                .noneMatch(name -> name.toLowerCase().contains("resend"))
                .noneMatch(name -> name.toLowerCase().contains("override"));
    }
}
