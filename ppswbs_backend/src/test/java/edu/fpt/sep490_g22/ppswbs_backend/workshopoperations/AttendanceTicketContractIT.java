package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopOperationsExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CapacityHoldResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInResultType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.Ticket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.TicketIssueResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.TicketResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingTicketProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityCommitOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityReleaseOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CommitBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReleaseBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopCheckInController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceTicketService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CheckInService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T027: Contract verification test for Workshop Operations User Story 3
 * (capacity hold, ticket issue, and normal check-in behavior) against contracts/workshop-operations.openapi.yaml.
 * <p>
 * Verifies request/response schema shape, Bean Validation rules (oneOf qrToken or ticketCode),
 * privacy boundary (non-disclosure of PII, raw QR payload, or JPA entities),
 * and security scheme boundaries without prematurely implementing T028–T033.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AttendanceTicketContractIT {

    private static final Logger log = LoggerFactory.getLogger(AttendanceTicketContractIT.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private WorkshopOperationsFacade operationsFacade;

    @Autowired(required = false)
    private WorkshopLocationRepository locationRepository;

    @Autowired(required = false)
    private WorkshopSlotRepository slotRepository;

    @Autowired(required = false)
    private WorkshopSessionRepository sessionRepository;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("T027-1: OpenAPI metadata, server base path, and bearer security scheme contract")
    @SuppressWarnings("unchecked")
    void verifyOpenApiMetadataAndSecurityContract() {
        assertThat(openApiSpec.get("openapi")).isEqualTo("3.0.3");

        // Server URL contract: /api/v1
        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        assertThat(servers).isNotEmpty();
        assertThat(servers.getFirst().get("url")).isEqualTo("/api/v1");

        // Global security: bearerAuth
        List<Map<String, Object>> security = (List<Map<String, Object>>) openApiSpec.get("security");
        assertThat(security).isNotEmpty();
        assertThat(security.getFirst()).containsKey("bearerAuth");

        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
        assertThat(securitySchemes).containsKey("bearerAuth");

        Map<String, Object> bearerAuth = (Map<String, Object>) securitySchemes.get("bearerAuth");
        assertThat(bearerAuth.get("type")).isEqualTo("http");
        assertThat(bearerAuth.get("scheme")).isEqualTo("bearer");
        assertThat(bearerAuth.get("bearerFormat")).isEqualTo("Firebase-ID-token");
    }

    // =========================================================================
    // 2. Check-In & Booking Lookup Paths in OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T027-2: OpenAPI endpoints for normal check-in and booking lookup")
    @SuppressWarnings("unchecked")
    void verifyCheckInAndBookingLookupPathsInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");

        // Check-in endpoint: POST /workshop-sessions/{sessionId}/check-ins
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/check-ins");
        Map<String, Object> checkInPathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/check-ins");
        assertThat(checkInPathItem).containsKey("post");

        Map<String, Object> checkInPost = (Map<String, Object>) checkInPathItem.get("post");
        Map<String, Object> reqBody = (Map<String, Object>) checkInPost.get("requestBody");
        Map<String, Object> content = (Map<String, Object>) reqBody.get("content");
        Map<String, Object> jsonContent = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schemaRef = (Map<String, Object>) jsonContent.get("schema");
        assertThat(schemaRef.get("$ref")).isEqualTo("#/components/schemas/CheckInRequest");

        Map<String, Object> checkInResponses = (Map<String, Object>) checkInPost.get("responses");
        assertThat(checkInResponses).containsKeys("200", "400", "403", "409");

        Map<String, Object> res200 = (Map<String, Object>) checkInResponses.get("200");
        assertThat(res200.get("$ref")).isEqualTo("#/components/responses/CheckInResult");

        // Booking lookup endpoint: GET /workshop-sessions/{sessionId}/booking-lookups/{bookingCode}
        assertThat(paths).containsKey("/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}");
        Map<String, Object> lookupPathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}");
        assertThat(lookupPathItem).containsKey("get");

        Map<String, Object> lookupGet = (Map<String, Object>) lookupPathItem.get("get");
        Map<String, Object> lookupResponses = (Map<String, Object>) lookupGet.get("responses");
        assertThat(lookupResponses).containsKeys("200", "403", "404");

        Map<String, Object> lookup200 = (Map<String, Object>) lookupResponses.get("200");
        assertThat(lookup200.get("$ref")).isEqualTo("#/components/responses/TicketList");
    }

    // =========================================================================
    // 3. CheckInRequest Schema and DTO Validation Contract (oneOf: qrToken | ticketCode)
    // =========================================================================

    @Test
    @DisplayName("T027-3: CheckInRequest schema and Bean Validation oneOf qrToken or ticketCode")
    @SuppressWarnings("unchecked")
    void verifyCheckInRequestSchemaAndValidation() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> checkInSchema = (Map<String, Object>) schemas.get("CheckInRequest");
        assertThat(checkInSchema).isNotNull();

        Map<String, Object> properties = (Map<String, Object>) checkInSchema.get("properties");
        assertThat(properties).containsKeys("qrToken", "ticketCode");
        assertThat(properties).doesNotContainKey("branchId");
        assertThat(properties).doesNotContainKey("personnelId");

        List<Map<String, Object>> oneOf = (List<Map<String, Object>>) checkInSchema.get("oneOf");
        assertThat(oneOf).isNotNull();
        assertThat(oneOf).hasSize(2);

        // DTO Validation: valid case 1 - only qrToken provided
        CheckInRequest qrOnlyReq = CheckInRequest.builder()
                .qrToken("QR-SAMPLE-TOKEN-XYZ")
                .build();
        Set<ConstraintViolation<CheckInRequest>> qrViolations = validator.validate(qrOnlyReq);
        assertThat(qrViolations).isEmpty();

        // DTO Validation: valid case 2 - only ticketCode provided
        CheckInRequest codeOnlyReq = CheckInRequest.builder()
                .ticketCode("TK-ABCD1234")
                .build();
        Set<ConstraintViolation<CheckInRequest>> codeViolations = validator.validate(codeOnlyReq);
        assertThat(codeViolations).isEmpty();

        // DTO Validation: invalid case 1 - both null/blank
        CheckInRequest emptyReq = CheckInRequest.builder().build();
        Set<ConstraintViolation<CheckInRequest>> emptyViolations = validator.validate(emptyReq);
        assertThat(emptyViolations).hasSize(1);
        assertThat(emptyViolations.iterator().next().getMessage())
                .contains("Exactly one of qrToken or ticketCode must be provided");

        // DTO Validation: invalid case 2 - both provided
        CheckInRequest bothReq = CheckInRequest.builder()
                .qrToken("QR-SAMPLE-TOKEN-XYZ")
                .ticketCode("TK-ABCD1234")
                .build();
        Set<ConstraintViolation<CheckInRequest>> bothViolations = validator.validate(bothReq);
        assertThat(bothViolations).hasSize(1);
        assertThat(bothViolations.iterator().next().getMessage())
                .contains("Exactly one of qrToken or ticketCode must be provided");
    }

    // =========================================================================
    // 4. Ticket Schema and DTO Shape Verification
    // =========================================================================

    @Test
    @DisplayName("T027-4: Ticket schema and TicketResponse/Ticket DTO verification")
    @SuppressWarnings("unchecked")
    void verifyTicketSchemaAndDtoShape() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> ticketSchema = (Map<String, Object>) schemas.get("Ticket");
        assertThat(ticketSchema).isNotNull();

        List<String> requiredFields = (List<String>) ticketSchema.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("ticketCode", "sessionId", "locationId", "validityState", "attendanceState");

        Map<String, Object> properties = (Map<String, Object>) ticketSchema.get("properties");
        assertThat(properties).containsKeys("ticketCode", "sessionId", "locationId", "validityState", "attendanceState", "checkedInAt");

        // Verify locationId is int64 (canonical location identity)
        Map<String, Object> locationIdProp = (Map<String, Object>) properties.get("locationId");
        assertThat(locationIdProp.get("type")).isEqualTo("integer");
        assertThat(locationIdProp.get("format")).isEqualTo("int64");

        // Verify validityState enum values
        Map<String, Object> validityProp = (Map<String, Object>) properties.get("validityState");
        List<String> validityEnums = (List<String>) validityProp.get("enum");
        assertThat(validityEnums).containsExactlyInAnyOrder("ACTIVE", "CANCELLED", "REVOKED", "REFUNDED");

        // Verify attendanceState enum values
        Map<String, Object> attendanceProp = (Map<String, Object>) properties.get("attendanceState");
        List<String> attendanceEnums = (List<String>) attendanceProp.get("enum");
        assertThat(attendanceEnums).containsExactlyInAnyOrder("NOT_CHECKED_IN", "CHECKED_IN", "NO_SHOW");

        // DTO validation: valid TicketResponse instance
        TicketResponse validTicket = TicketResponse.builder()
                .ticketCode("TK-12345678")
                .sessionId(UUID.randomUUID().toString())
                .locationId(1L)
                .validityState(TicketValidityState.ACTIVE)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build();
        assertThat(validator.validate(validTicket)).isEmpty();

        // DTO validation: null ticketCode rejected
        TicketResponse nullCodeTicket = TicketResponse.builder()
                .sessionId(UUID.randomUUID().toString())
                .locationId(1L)
                .validityState(TicketValidityState.ACTIVE)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build();
        assertThat(validator.validate(nullCodeTicket)).isNotEmpty();

        // Direct schema alias check
        Ticket ticketAlias = Ticket.builder()
                .ticketCode("TK-ALIAS-01")
                .sessionId(UUID.randomUUID().toString())
                .locationId(2L)
                .validityState(TicketValidityState.ACTIVE)
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkedInAt(Instant.now())
                .build();
        assertThat(validator.validate(ticketAlias)).isEmpty();
    }

    // =========================================================================
    // 5. CheckInResult Schema and Normal Scan Output Contract
    // =========================================================================

    @Test
    @DisplayName("T027-5: CheckInResult schema, inheritance from Ticket, and normal scan privacy-safe fields")
    @SuppressWarnings("unchecked")
    void verifyCheckInResultSchemaAndDtoContract() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> resultSchema = (Map<String, Object>) schemas.get("CheckInResult");
        assertThat(resultSchema).isNotNull();

        List<Map<String, Object>> allOf = (List<Map<String, Object>>) resultSchema.get("allOf");
        assertThat(allOf).hasSize(2);
        assertThat(allOf.get(0).get("$ref")).isEqualTo("#/components/schemas/Ticket");

        Map<String, Object> extension = allOf.get(1);
        List<String> requiredFields = (List<String>) extension.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("checkInResult", "checkedInAt");

        Map<String, Object> extProps = (Map<String, Object>) extension.get("properties");
        Map<String, Object> checkInResultProp = (Map<String, Object>) extProps.get("checkInResult");
        List<String> resultEnums = (List<String>) checkInResultProp.get("enum");
        assertThat(resultEnums).containsExactlyInAnyOrder("CHECKED_IN", "ALREADY_CHECKED_IN");

        // DTO Validation: valid CheckInResult
        Instant now = Instant.now();
        CheckInResult validResult = CheckInResult.builder()
                .ticketCode("TK-SCAN-01")
                .sessionId(UUID.randomUUID().toString())
                .locationId(1L)
                .validityState(TicketValidityState.ACTIVE)
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkInResult(CheckInResultType.CHECKED_IN)
                .checkedInAt(now)
                .build();
        assertThat(validator.validate(validResult)).isEmpty();

        // DTO Validation: null checkInResult rejected
        CheckInResult nullTypeResult = CheckInResult.builder()
                .ticketCode("TK-SCAN-01")
                .sessionId(UUID.randomUUID().toString())
                .locationId(1L)
                .validityState(TicketValidityState.ACTIVE)
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkedInAt(now)
                .build();
        assertThat(validator.validate(nullTypeResult)).isNotEmpty();

        // DTO Validation: null checkedInAt rejected
        CheckInResult nullTimeResult = CheckInResult.builder()
                .ticketCode("TK-SCAN-01")
                .sessionId(UUID.randomUUID().toString())
                .locationId(1L)
                .validityState(TicketValidityState.ACTIVE)
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkInResult(CheckInResultType.ALREADY_CHECKED_IN)
                .build();
        assertThat(validator.validate(nullTimeResult)).isNotEmpty();
    }

    // =========================================================================
    // 6. Privacy Boundary and Non-Disclosure Contract
    // =========================================================================

    @Test
    @DisplayName("T027-6: Privacy boundary verification: CheckInResult and TicketResponse never leak PII, payment, or entity internals")
    void verifyPrivacyBoundaryAndNonDisclosure() {
        List<Class<?>> privacyClasses = List.of(
                CheckInResult.class,
                TicketResponse.class,
                Ticket.class,
                CheckInRequest.class,
                CapacityHoldResponse.class,
                TicketIssueResponse.class
        );

        List<String> prohibitedFieldNames = List.of(
                "customerName", "buyerName", "contactName",
                "email", "phone", "phoneNumber", "contactEmail", "contactPhone",
                "price", "paidAmount", "amount", "vnpayRef", "vnpayPayload",
                "qrRawPayload", "rawToken", "rawPayload",
                "firebaseUid", "auditId", "metadata", "internalId"
        );

        for (Class<?> clazz : privacyClasses) {
            Field[] declaredFields = clazz.getDeclaredFields();
            for (Field field : declaredFields) {
                String name = field.getName();
                assertThat(prohibitedFieldNames)
                        .as("Class " + clazz.getSimpleName() + " must not declare sensitive field: " + name)
                        .doesNotContain(name);

                // No entity leakage
                Class<?> fieldType = field.getType();
                assertThat(fieldType.getPackageName())
                        .as("Class " + clazz.getSimpleName() + " field " + name + " must not reference entity package")
                        .doesNotContain("edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity");
            }
        }

        // Verify CheckInResult declared/inherited fields match exactly the OpenAPI normal scan projection
        Set<String> checkInResultPropertyNames = Set.of(
                "ticketCode", "sessionId", "locationId", "validityState", "attendanceState",
                "checkInResult", "checkedInAt"
        );

        List<String> actualFieldNames = Arrays.stream(CheckInResult.class.getMethods())
                .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0 && !m.getName().equals("getClass"))
                .map(m -> {
                    String prop = m.getName().substring(3);
                    return Character.toLowerCase(prop.charAt(0)) + prop.substring(1);
                })
                .toList();

        for (String expectedProp : checkInResultPropertyNames) {
            assertThat(actualFieldNames)
                    .as("CheckInResult must expose property: " + expectedProp)
                    .contains(expectedProp);
        }
    }

    // =========================================================================
    // 7. Capacity Hold and Ticket Issuance Boundary Types
    // =========================================================================

    @Test
    @DisplayName("T027-7: Capacity hold create/confirm/release and ticket issue facade and DTO types contract")
    void verifyCapacityHoldAndTicketIssueTypes() {
        // Facade Command/Outcome types existence and invariant contract
        assertThat(ReserveBookingCapacityCommand.class).isNotNull();
        assertThat(CapacityHoldOutcome.class).isNotNull();
        assertThat(CommitBookingHoldsCommand.class).isNotNull();
        assertThat(CapacityCommitOutcome.class).isNotNull();
        assertThat(ReleaseBookingHoldsCommand.class).isNotNull();
        assertThat(CapacityReleaseOutcome.class).isNotNull();

        assertThat(BookingTicketProjectionQuery.class).isNotNull();
        assertThat(DeliverySafeTicketSet.class).isNotNull();
        assertThat(DeliverySafeTicket.class).isNotNull();
        assertThat(SessionEntitlementProjection.class).isNotNull();

        assertThat(OutcomeStatus.class).isNotNull();
        assertThat(RejectionReason.class).isNotNull();

        // WorkshopOperationsFacade methods existence
        assertThat(WorkshopOperationsFacade.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains(
                        "reserveBookingCapacity",
                        "commitBookingHolds",
                        "releaseBookingHolds",
                        "getBookingTicketProjection"
                );

        // CapacityHoldResponse DTO validation
        CapacityHoldResponse validHold = CapacityHoldResponse.builder()
                .reference("CH-REF-001")
                .sessionId(UUID.randomUUID().toString())
                .bookingReference("BKG-001")
                .requestedTicketCount(2)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        assertThat(validator.validate(validHold)).isEmpty();

        // Minimum capacity hold >= 1
        CapacityHoldResponse invalidHoldCount = CapacityHoldResponse.builder()
                .reference("CH-REF-002")
                .sessionId(UUID.randomUUID().toString())
                .bookingReference("BKG-002")
                .requestedTicketCount(0)
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        assertThat(validator.validate(invalidHoldCount)).isNotEmpty();

        // TicketIssueResponse DTO validation
        TicketIssueResponse validIssue = TicketIssueResponse.builder()
                .ticketCode("TK-ISSUE-01")
                .qrReference("QR-REF-01")
                .sessionId(UUID.randomUUID().toString())
                .locationId(10L)
                .validityState(TicketValidityState.ACTIVE)
                .attendanceState(AttendanceState.NOT_CHECKED_IN)
                .build();
        assertThat(validator.validate(validIssue)).isEmpty();
    }

    // =========================================================================
    // 8. Rejection of Legacy Branch and Personnel Terminology
    // =========================================================================

    @Test
    @DisplayName("T027-8: Prove legacy branch routes/fields and personnel fields are absent and rejected")
    @SuppressWarnings("unchecked")
    void verifyAbsenceOfLegacyBranchAndPersonnelTerminology() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");

        // No branch endpoints in OpenAPI
        assertThat(paths.keySet()).noneMatch(path -> path.contains("/workshop-branches") || path.contains("branch"));

        // Check-in and booking lookup paths use sessionId and bookingCode (no branchId)
        Map<String, Object> checkInPathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/check-ins");
        assertThat(checkInPathItem).isNotNull();

        Map<String, Object> lookupPathItem = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}");
        assertThat(lookupPathItem).isNotNull();

        // Schemas check
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        assertThat(schemas).doesNotContainKey("Branch");
        assertThat(schemas).doesNotContainKey("WorkshopBranch");

        Map<String, Object> ticketSchema = (Map<String, Object>) schemas.get("Ticket");
        Map<String, Object> ticketProps = (Map<String, Object>) ticketSchema.get("properties");
        assertThat(ticketProps).containsKey("locationId");
        assertThat(ticketProps).doesNotContainKey("branchId");

        // DTO reflection: TicketResponse, CheckInResult, CheckInRequest do NOT contain branchId or personnelId
        List<Class<?>> dtos = List.of(CheckInRequest.class, TicketResponse.class, CheckInResult.class, TicketIssueResponse.class);
        for (Class<?> clazz : dtos) {
            List<String> fieldNames = Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).toList();
            assertThat(fieldNames)
                    .as(clazz.getSimpleName() + " must not contain branchId or personnelId")
                    .doesNotContain("branchId", "branch", "personnelId", "staffPersonnelId");
        }
    }

    // =========================================================================
    // 9. RFC 7807 Problem Detail Contract
    // =========================================================================

    @Test
    @DisplayName("T027-9: RFC 7807 Problem Detail schema and exception handler contract")
    @SuppressWarnings("unchecked")
    void verifyProblemDetailContract() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> problemSchema = (Map<String, Object>) schemas.get("Problem");
        assertThat(problemSchema).isNotNull();

        List<String> requiredProblemFields = (List<String>) problemSchema.get("required");
        assertThat(requiredProblemFields).containsExactlyInAnyOrder("type", "title", "status", "code");

        ProblemDetailResponse problem = ProblemDetailResponse.builder()
                .type(URI.create("urn:problem-type:workshop-operations:TICKET_ALREADY_CHECKED_IN"))
                .title("Ticket Already Checked In")
                .status(409)
                .code("TICKET_ALREADY_CHECKED_IN")
                .detail("Ticket has already been checked in at the active session")
                .correlationId("corr-test-chk-01")
                .build();

        assertThat(problem.getType()).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getCode()).isEqualTo("TICKET_ALREADY_CHECKED_IN");

        assertThat(applicationContext.getBean(WorkshopOperationsExceptionHandler.class)).isNotNull();
    }

    // =========================================================================
    // 10. Registered Business Services and Check-In Controllers
    // =========================================================================

    @Test
    @DisplayName("T031-10: Registered business services and check-in controllers in Spring context")
    void verifyBusinessServicesAndCheckInControllersAreRegistered() {
        assertThat(applicationContext.getBean(AttendanceTicketService.class)).isNotNull();
        assertThat(applicationContext.getBean(CheckInService.class)).isNotNull();
        assertThat(applicationContext.getBean(WorkshopCheckInController.class)).isNotNull();
        assertThat(applicationContext.getBean(CapacityHoldService.class)).isNotNull();
        assertThat(applicationContext.getBean(WorkshopOperationsFacade.class)).isNotNull();
    }

    // =========================================================================
    // 11. T032: Booking Caller Hold Confirmation and Delivery-Safe Ticket Projection
    // =========================================================================

    @Test
    @DisplayName("T032: Booking caller confirms active hold set after commercial confirmation and receives delivery-safe tickets")
    void verifyBookingCallerHoldConfirmationAndDeliverySafeTicketProjectionContract() {
        assertThat(operationsFacade).isNotNull();
        assertThat(locationRepository).isNotNull();
        assertThat(sessionRepository).isNotNull();

        // 1. Prior to confirmation, projection query returns empty ticket set
        String bookingRef = "BK-CTR-" + UUID.randomUUID().toString().substring(0, 8);
        DeliverySafeTicketSet preCommitTickets = operationsFacade.getBookingTicketProjection(
                new BookingTicketProjectionQuery(bookingRef));
        assertThat(preCommitTickets).isNotNull();
        assertThat(preCommitTickets.tickets()).isEmpty();

        // 2. Setup Location, Slot, and 2 Sessions (simulating multi-session or Silverclay package)
        WorkshopLocation loc = locationRepository.save(WorkshopLocation.builder()
                .code("LOC-CTR-" + UUID.randomUUID().toString().substring(0, 6))
                .displayName("Contract Verification Hub")
                .addressSnapshot("123 Test Street, D1")
                .active(true)
                .build());

        WorkshopSlot slot = slotRepository.save(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString())
                .location(loc)
                .label("Contract Slot")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .defaultCapacity(10)
                .active(true)
                .build());

        LocalDate workshopDate = LocalDate.now().plusDays(7);
        WorkshopSession session1 = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(loc)
                .slot(slot)
                .workshopDate(workshopDate)
                .scheduledStartAt(workshopDate.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(workshopDate.atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(workshopDate.atTime(8, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .build());

        WorkshopSession session2 = sessionRepository.save(WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(loc)
                .slot(slot)
                .workshopDate(workshopDate.plusDays(1))
                .scheduledStartAt(workshopDate.plusDays(1).atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .scheduledEndAt(workshopDate.plusDays(1).atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpensAt(workshopDate.plusDays(1).atTime(8, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .capacity(10)
                .state(SessionState.PUBLISHED)
                .build());

        // 3. Reserve 2 seats across both sessions (N = 2 participants)
        List<UUID> sessionRefs = List.of(UUID.fromString(session1.getReference()), UUID.fromString(session2.getReference()));
        CapacityHoldOutcome holdOutcome = operationsFacade.reserveBookingCapacity(
                new ReserveBookingCapacityCommand(bookingRef, sessionRefs, 2, "op-ctr-hold-" + UUID.randomUUID(), "corr-ctr-hold"));
        assertThat(holdOutcome.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        List<UUID> holdRefs = holdOutcome.holdReferences();
        assertThat(holdRefs).hasSize(2);

        // 4. Incomplete or invalid hold set commit is rejected
        UUID fakeHoldRef = UUID.randomUUID();
        CapacityCommitOutcome invalidCommit = operationsFacade.commitBookingHolds(
                new CommitBookingHoldsCommand(bookingRef, List.of(holdRefs.get(0), fakeHoldRef), "op-fail-" + UUID.randomUUID(), "corr-fail"));
        assertThat(invalidCommit.status()).isEqualTo(OutcomeStatus.REJECTED);
        assertThat(invalidCommit.reason()).isEqualTo(RejectionReason.HOLD_MISSING_OR_EXPIRED);

        // 5. Verified commercial confirmation commits the complete active hold set
        String commitOpRef = "op-success-" + UUID.randomUUID();
        CapacityCommitOutcome validCommit = operationsFacade.commitBookingHolds(
                new CommitBookingHoldsCommand(bookingRef, holdRefs, commitOpRef, "corr-success"));
        assertThat(validCommit.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(validCommit.reason()).isNull();

        // 6. Post-confirmation delivery-safe ticket projection query
        DeliverySafeTicketSet postCommitTickets = operationsFacade.getBookingTicketProjection(
                new BookingTicketProjectionQuery(bookingRef));
        assertThat(postCommitTickets).isNotNull();
        // N = 2 participants -> exactly 2 individual tickets issued (no one-ticket-per-booking constraint)
        assertThat(postCommitTickets.tickets()).hasSize(2);

        for (DeliverySafeTicket ticket : postCommitTickets.tickets()) {
            assertThat(ticket.ticketCode()).matches("^TK-[A-Z0-9]{8}$");
            assertThat(ticket.ticketReference()).isNotNull();
            // Entitlements span across both required sessions
            assertThat(ticket.sessionEntitlements()).hasSize(2);
            for (SessionEntitlementProjection ent : ticket.sessionEntitlements()) {
                assertThat(ent.locationReference()).isEqualTo(loc.getId());
                assertThat(ent.scheduledStart()).isNotNull();
                assertThat(ent.scheduledEnd()).isNotNull();
            }
        }

        // 7. Non-disclosure boundary: DeliverySafeTicket exposes no buyer PII or JPA entities
        List<String> ticketFieldNames = Arrays.stream(DeliverySafeTicket.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        assertThat(ticketFieldNames)
                .as("DeliverySafeTicket must not expose customer PII or entity types")
                .doesNotContain("buyerEmail", "email", "phone", "buyerPhone", "customerName", "entity", "repository", "dao");

        // 8. Commit is idempotent: re-calling commit with same operationReference returns ACCEPTED and preserves the same ticket projection
        CapacityCommitOutcome replayCommit = operationsFacade.commitBookingHolds(
                new CommitBookingHoldsCommand(bookingRef, holdRefs, commitOpRef, "corr-replay"));
        assertThat(replayCommit.status()).isEqualTo(OutcomeStatus.ACCEPTED);

        DeliverySafeTicketSet replayedTickets = operationsFacade.getBookingTicketProjection(
                new BookingTicketProjectionQuery(bookingRef));
        assertThat(replayedTickets.tickets()).hasSize(2);
        assertThat(replayedTickets.tickets().stream().map(DeliverySafeTicket::ticketCode).toList())
                .containsExactlyInAnyOrderElementsOf(postCommitTickets.tickets().stream().map(DeliverySafeTicket::ticketCode).toList());

        log.info("T032 facade contract verification passed: Booking caller confirmed complete hold set and received delivery-safe projection.");
    }
}
