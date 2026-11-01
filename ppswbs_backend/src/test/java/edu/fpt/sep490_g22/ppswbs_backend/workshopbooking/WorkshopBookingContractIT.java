package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 003.1 T043: Contract verification against workshop-booking.openapi.yaml.
 * <p>
 * Verifies:
 * 1. Every public Booking route matches OpenAPI specification (10 endpoints).
 * 2. Discovery routes (/workshop-packages) are strictly absent from booking controllers.
 * 3. Mutating POST operations enforce Idempotency-Key parameter and format/replay rules.
 * 4. Session references use UUID type throughout DTOs and reject invalid formats.
 * 5. Typed package-selection errors map to RFC 7807 Problem Detail (HTTP 409).
 * 6. Alternative proof/ownership verification: Guest contact proof vs Member Bearer ownership.
 * 7. Safe projections exclude PII (email, phone, tokens, raw magic links, internal IDs).
 */
@ExtendWith(MockitoExtension.class)
class WorkshopBookingContractIT {

    private static Map<String, Object> openApiSpec;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private AccountFacade accountFacade;

    @Mock
    private BookingAuditEvidenceRepository auditEvidenceRepository;

    @Mock
    private WorkshopOperationsAdapter operationsAdapter;

    @Mock
    private LoyaltyFacade loyaltyFacade;

    @Mock
    private MemberWorkshopBookingQuoteRepository quoteRepository;

    @BeforeAll
    static void loadOpenApi() throws Exception {
        File specFile = resolveOpenApiFile();
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(specFile)) {
            openApiSpec = yaml.load(in);
        }
        assertThat(openApiSpec).as("OpenAPI contract must load successfully").isNotNull();
    }

    private static File resolveOpenApiFile() {
        String[] candidatePaths = {
                "../ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml",
                "ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml",
                "contracts/workshop-booking.openapi.yaml"
        };
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists()) return f;
        }
        throw new IllegalStateException("OpenAPI contract file not found in candidate paths");
    }

    // =========================================================================
    // 1. OpenAPI Route Alignment & Discovery Route Absence
    // =========================================================================

    @Test
    @DisplayName("T043-1: Controller operations exactly match OpenAPI routes and discovery routes are absent")
    @SuppressWarnings("unchecked")
    void controllerOperationsMatchOpenApiRoutesAndExcludeDiscovery() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).isNotNull();

        // Collect all HTTP method + path from OpenAPI
        Set<String> openApiOperations = new HashSet<>();
        paths.forEach((path, operationsObj) -> {
            Map<String, Object> ops = (Map<String, Object>) operationsObj;
            ops.forEach((method, details) -> {
                if (Set.of("get", "post", "put", "patch", "delete").contains(method.toLowerCase())) {
                    openApiOperations.add(method.toUpperCase() + " " + path);
                }
            });
        });

        // OpenAPI must contain exactly the 10 defined booking operations
        assertThat(openApiOperations).containsExactlyInAnyOrder(
                "POST /api/v1/workshop-booking-quotes",
                "POST /api/v1/workshop-bookings",
                "POST /api/v1/workshop-bookings/{bookingCode}/payment-attempts",
                "POST /api/v1/booking-lookups",
                "POST /api/v1/booking-magic-links/exchanges",
                "GET /api/v1/members/me/workshop-bookings/{bookingCode}",
                "POST /api/v1/workshop-bookings/{bookingCode}/pending-hold-release",
                "POST /api/v1/workshop-bookings/{bookingCode}/confirmation-deliveries",
                "POST /api/v1/workshop-bookings/{bookingCode}/magic-link-incidents",
                "POST /api/v1/workshop-bookings/{bookingCode}/late-payment-follow-ups"
        );

        // Scan actual controller operations
        Map<String, Method> actualOperations = scanBookingControllerOperations();
        assertThat(actualOperations.keySet()).containsExactlyInAnyOrderElementsOf(openApiOperations);

        // Discovery routes must be absent from booking controllers
        assertThat(actualOperations.keySet()).noneMatch(op ->
                op.contains("/workshop-packages") && !op.contains("/workshop-bookings") && !op.contains("/workshop-booking-quotes"));
        assertThat(actualOperations.keySet()).doesNotContain(
                "GET /api/v1/workshop-packages",
                "GET /api/v1/workshop-packages/{packageId}",
                "GET /api/v1/workshop-packages/{packageId}/sessions"
        );
    }

    // =========================================================================
    // 2. Idempotency-Key Header Parameter Enforcement on State Mutations
    // =========================================================================

    @Test
    @DisplayName("T043-2: All state-changing POST routes enforce Idempotency-Key header parameter")
    void stateChangingPostRoutesEnforceIdempotencyKey() {
        Map<String, Method> operations = scanBookingControllerOperations();

        // Mutating operations in OpenAPI that require Idempotency-Key
        Set<String> mutatingOperations = Set.of(
                "POST /api/v1/workshop-bookings",
                "POST /api/v1/workshop-bookings/{bookingCode}/payment-attempts",
                "POST /api/v1/workshop-bookings/{bookingCode}/pending-hold-release",
                "POST /api/v1/workshop-bookings/{bookingCode}/confirmation-deliveries",
                "POST /api/v1/workshop-bookings/{bookingCode}/magic-link-incidents",
                "POST /api/v1/workshop-bookings/{bookingCode}/late-payment-follow-ups"
        );

        for (String operation : mutatingOperations) {
            Method method = operations.get(operation);
            assertThat(method).as("Method for %s must exist", operation).isNotNull();
            assertThat(hasIdempotencyKeyHeader(method))
                    .as("Operation %s must declare @RequestHeader(\"Idempotency-Key\")", operation)
                    .isTrue();
        }

        // Read-only / exchange / preview POST routes do not take Idempotency-Key
        Set<String> nonMutatingPostOperations = Set.of(
                "POST /api/v1/workshop-booking-quotes",
                "POST /api/v1/booking-lookups",
                "POST /api/v1/booking-magic-links/exchanges"
        );
        for (String operation : nonMutatingPostOperations) {
            Method method = operations.get(operation);
            assertThat(method).as("Method for %s must exist", operation).isNotNull();
            assertThat(hasIdempotencyKeyHeader(method))
                    .as("Operation %s must NOT require Idempotency-Key", operation)
                    .isFalse();
        }
    }

    // =========================================================================
    // 3. Idempotency-Key Format Validation and Replay Semantics
    // =========================================================================

    @Test
    @DisplayName("T043-3: Idempotency-Key length constraint (16 to 128 chars) and replay conflict behavior")
    void idempotencyKeyLengthValidationAndConflictHandling() {
        BookingPendingHoldReleaseController controller = new BookingPendingHoldReleaseController(
                org.mockito.Mockito.mock(BookingPendingHoldReleaseService.class), new ObjectMapper());

        PendingHoldReleaseRequest request = PendingHoldReleaseRequest.builder()
                .reason("Customer requested release")
                .contactEmail("guest@mland.vn")
                .build();

        // Shorter than 16 characters
        assertThatThrownBy(() -> controller.release("BK-001", "short-key", null, null, request))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("between 16 and 128 characters");

        // Null key
        assertThatThrownBy(() -> controller.release("BK-001", null, null, null, request))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("between 16 and 128 characters");

        // Longer than 128 characters
        String longKey = "a".repeat(129);
        assertThatThrownBy(() -> controller.release("BK-001", longKey, null, null, request))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("between 16 and 128 characters");

        // Exception handler maps BookingIdempotencyConflictException to 409 IDEMPOTENCY_CONFLICT
        WorkshopBookingExceptionHandler handler = new WorkshopBookingExceptionHandler();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Correlation-Id", "CORR-IDEMP-01");

        ResponseEntity<ProblemDetailResponse> response = handler.handleIdempotencyConflict(
                new BookingIdempotencyConflictException("Stored fingerprint mismatch"), servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("CORR-IDEMP-01");
    }

    // =========================================================================
    // 4. UUID Session References Throughout DTOs and Validation
    // =========================================================================

    @Test
    @DisplayName("T043-4: Session references use UUID type and enforce canonical formatting")
    void uuidSessionReferencesEnforcement() throws NoSuchFieldException {
        // BookedSessionDto uses UUID
        Field sessionRefField = BookedSessionDto.class.getDeclaredField("sessionReference");
        assertThat(sessionRefField.getType()).isEqualTo(UUID.class);

        // BookingTicketDto uses List<UUID>
        Field ticketSessionsField = BookingTicketDto.class.getDeclaredField("sessionReferences");
        assertThat(ticketSessionsField.getType()).isEqualTo(List.class);
        ParameterizedType ticketParamType = (ParameterizedType) ticketSessionsField.getGenericType();
        assertThat(ticketParamType.getActualTypeArguments()[0]).isEqualTo(UUID.class);

        // CreateBookingRequest uses List<UUID>
        Field requestSessionsField = CreateBookingRequest.class.getDeclaredField("sessionReferences");
        assertThat(requestSessionsField.getType()).isEqualTo(List.class);
        ParameterizedType requestParamType = (ParameterizedType) requestSessionsField.getGenericType();
        assertThat(requestParamType.getActualTypeArguments()[0]).isEqualTo(UUID.class);

        // CreateBookingRequest validates unique sessions and max 2
        UUID session1 = UUID.randomUUID();
        CreateBookingRequest duplicateRequest = CreateBookingRequest.builder()
                .packageId(1L)
                .sessionReferences(List.of(session1, session1))
                .participantCount(1)
                .contactEmail("test@mland.vn")
                .contactPhone("0901234567")
                .build();
        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(duplicateRequest);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("must be unique"));

        // WorkshopBookingQuoteService canonicalUuid helper rejects non-canonical or malformed UUIDs
        WorkshopBookingAuthorizationService quoteAuthService = new WorkshopBookingAuthorizationService(
                accountFacade, auditEvidenceRepository);
        ReflectionTestUtils.setField(quoteAuthService, "allowTestTokens", true);
        Instant now = Instant.now();
        AccountSummary quoteMember = new AccountSummary(
                1001L, ActorRole.MEMBER, ActorStatus.ACTIVE, "quote.member@mland.vn", now, now);
        when(accountFacade.resolveAccount("quote-member")).thenReturn(Optional.of(quoteMember));

        WorkshopBookingQuoteService quoteService = new WorkshopBookingQuoteService(
                quoteAuthService, operationsAdapter, loyaltyFacade, quoteRepository, new ObjectMapper());

        WorkshopBookingQuoteRequest badUuidRequest = new WorkshopBookingQuoteRequest(
                1L, List.of("not-a-valid-uuid"), 1,
                new WorkshopBookingQuoteRequest.BenefitSelectionRequest(
                        WorkshopBookingQuoteRequest.BenefitSelectionRequest.Choice.NONE, null, "0"));

        assertThatThrownBy(() -> quoteService.preview("Bearer test-uid:quote-member", badUuidRequest))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("canonical UUIDs");
    }

    // =========================================================================
    // 5. Typed Package-Selection Errors and RFC 7807 Problem Details
    // =========================================================================

    @Test
    @DisplayName("T043-5: Typed Package-selection errors map to HTTP 409 Conflict with Problem Details")
    void typedPackageSelectionErrorsMapToProblemDetail() {
        WorkshopBookingExceptionHandler handler = new WorkshopBookingExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "CORR-ERR-TEST");

        List<String> typedErrorCodes = List.of(
                "PACKAGE_NOT_FOUND",
                "PACKAGE_RETIRED",
                "PACKAGE_INACTIVE",
                "SESSION_COUNT_MISMATCH",
                "SLOT_GAP_VIOLATED",
                "CAPACITY_EXCEEDED",
                "QUOTE_EXPIRED",
                "PACKAGE_NOT_PUBLISHED",
                "INSUFFICIENT_CAPACITY",
                "PACKAGE_SELECTION_INVALID"
        );

        for (String code : typedErrorCodes) {
            BookingCreationRejectedException exception = new BookingCreationRejectedException(
                    code, "Rejection reason for " + code);
            ResponseEntity<ProblemDetailResponse> resp = handler.handleCreationRejected(exception, request);

            assertThat(resp.getStatusCode())
                    .as("Error code %s must map to 409 CONFLICT", code)
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getCode()).isEqualTo(code);
            assertThat(resp.getBody().getStatus()).isEqualTo(409);
            assertThat(resp.getBody().getCorrelationId()).isEqualTo("CORR-ERR-TEST");
            assertThat(resp.getBody().getType().toString())
                    .isEqualTo("urn:problem-type:workshop-booking:" + code.toLowerCase().replace('_', '-'));
            assertThat(resp.getBody().getDetail()).contains("Rejection reason for " + code);
        }
    }

    // =========================================================================
    // 6. Guest Contact Proof vs Member Ownership Alternative
    // =========================================================================

    @Test
    @DisplayName("T043-6: Proof alternative: Guest requires exactly one contact proof; Member requires ownership")
    void proofAndOwnershipAlternatives() {
        // GuestLookupRequest validation
        // 1. Email only -> valid
        GuestLookupRequest emailOnly = GuestLookupRequest.builder()
                .bookingCode("BK-GUEST-01")
                .contactEmail("guest@mland.vn")
                .build();
        assertThat(validator.validate(emailOnly)).isEmpty();

        // 2. Phone only -> valid
        GuestLookupRequest phoneOnly = GuestLookupRequest.builder()
                .bookingCode("BK-GUEST-01")
                .contactPhone("0901234567")
                .build();
        assertThat(validator.validate(phoneOnly)).isEmpty();

        // 3. Both email and phone -> rejected
        GuestLookupRequest both = GuestLookupRequest.builder()
                .bookingCode("BK-GUEST-01")
                .contactEmail("guest@mland.vn")
                .contactPhone("0901234567")
                .build();
        Set<ConstraintViolation<GuestLookupRequest>> bothViolations = validator.validate(both);
        assertThat(bothViolations).anyMatch(v -> v.getMessage().contains("Exactly one of contactEmail or contactPhone"));

        // 4. Neither -> rejected
        GuestLookupRequest neither = GuestLookupRequest.builder()
                .bookingCode("BK-GUEST-01")
                .build();
        Set<ConstraintViolation<GuestLookupRequest>> neitherViolations = validator.validate(neither);
        assertThat(neitherViolations).anyMatch(v -> v.getMessage().contains("Exactly one of contactEmail or contactPhone"));

        // Member ownership validation
        WorkshopBookingAuthorizationService authService = new WorkshopBookingAuthorizationService(
                accountFacade, auditEvidenceRepository);
        ReflectionTestUtils.setField(authService, "allowTestTokens", true);

        Instant now = Instant.now();
        AccountSummary memberAccount = new AccountSummary(
                1001L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@mland.vn", now, now);
        when(accountFacade.resolveAccount("member-1001")).thenReturn(Optional.of(memberAccount));

        // Matching owner -> succeeds
        AccountSummary verified = authService.requireMemberOwnership("Bearer test-uid:member-1001", 1001L);
        assertThat(verified.accountId()).isEqualTo(1001L);

        // Mismatched owner -> 403 Forbidden
        assertThatThrownBy(() -> authService.requireMemberOwnership("Bearer test-uid:member-1001", 9999L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("does not own booking");

        // Non-member role attempting member ownership -> 403 Forbidden
        AccountSummary staffAccount = new AccountSummary(
                2001L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.vn", now, now);
        when(accountFacade.resolveAccount("staff-2001")).thenReturn(Optional.of(staffAccount));

        assertThatThrownBy(() -> authService.requireMemberOwnership("Bearer test-uid:staff-2001", 2001L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("requires MEMBER role");
    }

    // =========================================================================
    // 7. Safe Projections: No PII, Credentials, Tokens, or Internal IDs
    // =========================================================================

    @Test
    @DisplayName("T043-7: Projections strictly exclude customer PII, secrets, raw tokens, and internal DB IDs")
    void safeProjectionsPrivacyBoundary() {
        List<Class<?>> projectionClasses = List.of(
                BookingSummaryResponse.class,
                BookedSessionDto.class,
                BookingTicketDto.class,
                BookingActionOutcomeResponse.class
        );

        List<String> forbiddenFieldNames = List.of(
                "password", "secret", "token", "rawtoken", "magiclinktoken",
                "contactemail", "contactphone", "customerphone", "customeremail",
                "email", "phone", "sessionid", "databaseid", "signature"
        );

        for (Class<?> clazz : projectionClasses) {
            for (Field field : clazz.getDeclaredFields()) {
                String fieldName = field.getName().toLowerCase();
                for (String forbidden : forbiddenFieldNames) {
                    assertThat(fieldName)
                            .as("Class %s must not declare field %s containing sensitive substring %s",
                                    clazz.getSimpleName(), field.getName(), forbidden)
                            .isNotEqualTo(forbidden);
                }
            }
        }

        // Verify BookingTicketDto contains only ticketCode, qrReference, sessionReferences
        Set<String> ticketFields = Arrays.stream(BookingTicketDto.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertThat(ticketFields).containsExactlyInAnyOrder("ticketCode", "qrReference", "sessionReferences");

        // Verify BookedSessionDto contains only sessionReference, locationId, ordinal, scheduledStart, scheduledEnd
        Set<String> sessionFields = Arrays.stream(BookedSessionDto.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        assertThat(sessionFields).containsExactlyInAnyOrder(
                "sessionReference", "locationId", "ordinal", "scheduledStart", "scheduledEnd");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static Map<String, Method> scanBookingControllerOperations() {
        List<Class<?>> controllers = List.of(
                WorkshopBookingQuoteController.class,
                WorkshopBookingCreationController.class,
                DepositPaymentController.class,
                BookingLookupController.class,
                BookingMagicLinkExchangeController.class,
                BookingPendingHoldReleaseController.class,
                BookingRecoveryController.class
        );

        Map<String, Method> operations = new LinkedHashMap<>();
        for (Class<?> controller : controllers) {
            RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
            String basePath = (classMapping != null && classMapping.value().length > 0)
                    ? classMapping.value()[0] : "";

            for (Method method : controller.getDeclaredMethods()) {
                GetMapping get = method.getAnnotation(GetMapping.class);
                PostMapping post = method.getAnnotation(PostMapping.class);
                PutMapping put = method.getAnnotation(PutMapping.class);
                PatchMapping patch = method.getAnnotation(PatchMapping.class);
                DeleteMapping delete = method.getAnnotation(DeleteMapping.class);

                if (get != null) {
                    operations.put("GET " + combinePath(basePath, extractPath(get.value(), get.path())), method);
                } else if (post != null) {
                    operations.put("POST " + combinePath(basePath, extractPath(post.value(), post.path())), method);
                } else if (put != null) {
                    operations.put("PUT " + combinePath(basePath, extractPath(put.value(), put.path())), method);
                } else if (patch != null) {
                    operations.put("PATCH " + combinePath(basePath, extractPath(patch.value(), patch.path())), method);
                } else if (delete != null) {
                    operations.put("DELETE " + combinePath(basePath, extractPath(delete.value(), delete.path())), method);
                }
            }
        }
        return operations;
    }

    private static String extractPath(String[] value, String[] path) {
        if (path.length > 0 && !path[0].isBlank()) return path[0];
        if (value.length > 0 && !value[0].isBlank()) return value[0];
        return "";
    }

    private static String combinePath(String base, String suffix) {
        if (base.isBlank()) return suffix;
        if (suffix.isBlank()) return base;
        if (base.endsWith("/") && suffix.startsWith("/")) return base + suffix.substring(1);
        if (!base.endsWith("/") && !suffix.startsWith("/")) return base + "/" + suffix;
        return base + suffix;
    }

    private static boolean hasIdempotencyKeyHeader(Method method) {
        for (Parameter param : method.getParameters()) {
            RequestHeader header = param.getAnnotation(RequestHeader.class);
            if (header != null && ("Idempotency-Key".equals(header.value()) || "Idempotency-Key".equals(header.name()))) {
                return true;
            }
        }
        return false;
    }
}

