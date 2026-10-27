package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorkshopBookingDtoTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired
    private WorkshopBookingExceptionHandler exceptionHandler;

    @Autowired
    private WorkshopBookingAuthorizationService authorizationService;

    @Autowired
    private BookingAuditEvidenceRepository auditEvidenceRepository;

    @MockBean
    private AccountFacade accountFacade;

    @MockBean
    private WorkshopOperationsFacade operationsFacade;

    @Test
    @DisplayName("T013-1: CreateBookingRequest Bean Validation checks required fields, bounds, and formats")
    void verifyCreateBookingRequestValidation() {
        // Valid request
        CreateBookingRequest valid = CreateBookingRequest.builder()
                .packageId(1L)
                .sessionIds(List.of(10L, 11L))
                .participantCount(2)
                .contactEmail("test@mland.vn")
                .contactPhone("0901234567")
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        // Missing packageId
        CreateBookingRequest missingPkg = CreateBookingRequest.builder()
                .sessionIds(List.of(10L))
                .participantCount(1)
                .contactEmail("test@mland.vn")
                .contactPhone("0901234567")
                .build();
        Set<ConstraintViolation<CreateBookingRequest>> v1 = validator.validate(missingPkg);
        assertThat(v1).anyMatch(v -> v.getMessage().contains("packageId is required"));

        // Invalid participant count < 1
        CreateBookingRequest zeroParticipants = CreateBookingRequest.builder()
                .packageId(1L)
                .sessionIds(List.of(10L))
                .participantCount(0)
                .contactEmail("test@mland.vn")
                .contactPhone("0901234567")
                .build();
        Set<ConstraintViolation<CreateBookingRequest>> v2 = validator.validate(zeroParticipants);
        assertThat(v2).anyMatch(v -> v.getMessage().contains("at least 1"));

        // Invalid email format
        CreateBookingRequest badEmail = CreateBookingRequest.builder()
                .packageId(1L)
                .sessionIds(List.of(10L))
                .participantCount(1)
                .contactEmail("not-an-email")
                .contactPhone("0901234567")
                .build();
        Set<ConstraintViolation<CreateBookingRequest>> v3 = validator.validate(badEmail);
        assertThat(v3).anyMatch(v -> v.getMessage().contains("valid email"));

        // Too many sessions (> 2)
        CreateBookingRequest threeSessions = CreateBookingRequest.builder()
                .packageId(1L)
                .sessionIds(List.of(10L, 11L, 12L))
                .participantCount(1)
                .contactEmail("test@mland.vn")
                .contactPhone("0901234567")
                .build();
        Set<ConstraintViolation<CreateBookingRequest>> v4 = validator.validate(threeSessions);
        assertThat(v4).anyMatch(v -> v.getMessage().contains("1 or 2 sessions"));
    }

    @Test
    @DisplayName("T013-2: GuestLookupRequest validates that exactly one of contactEmail or contactPhone is provided")
    void verifyGuestLookupRequestValidation() {
        // Valid: with email only
        GuestLookupRequest emailOnly = GuestLookupRequest.builder()
                .bookingCode("BK-001")
                .contactEmail("guest@example.com")
                .build();
        assertThat(validator.validate(emailOnly)).isEmpty();

        // Valid: with phone only
        GuestLookupRequest phoneOnly = GuestLookupRequest.builder()
                .bookingCode("BK-001")
                .contactPhone("0912345678")
                .build();
        assertThat(validator.validate(phoneOnly)).isEmpty();

        // Invalid: both email and phone provided
        GuestLookupRequest both = GuestLookupRequest.builder()
                .bookingCode("BK-001")
                .contactEmail("guest@example.com")
                .contactPhone("0912345678")
                .build();
        Set<ConstraintViolation<GuestLookupRequest>> vBoth = validator.validate(both);
        assertThat(vBoth).anyMatch(v -> v.getMessage().contains("Exactly one of contactEmail or contactPhone"));

        // Invalid: neither provided
        GuestLookupRequest neither = GuestLookupRequest.builder()
                .bookingCode("BK-001")
                .build();
        Set<ConstraintViolation<GuestLookupRequest>> vNeither = validator.validate(neither);
        assertThat(vNeither).anyMatch(v -> v.getMessage().contains("Exactly one of contactEmail or contactPhone"));
    }

    @Test
    @DisplayName("T013-3: MagicLinkIncidentRequest and LatePaymentFollowUpRequest validate action enums and reason length")
    void verifyActionRequestsValidation() {
        // MagicLinkIncidentRequest
        MagicLinkIncidentRequest validMl = MagicLinkIncidentRequest.builder()
                .action("REVOKE")
                .reason("Customer reported lost email")
                .build();
        assertThat(validator.validate(validMl)).isEmpty();

        MagicLinkIncidentRequest badAction = MagicLinkIncidentRequest.builder()
                .action("DELETE")
                .reason("Invalid action")
                .build();
        Set<ConstraintViolation<MagicLinkIncidentRequest>> vMl = validator.validate(badAction);
        assertThat(vMl).anyMatch(v -> v.getMessage().contains("REVOKE or ROTATE"));

        // LatePaymentFollowUpRequest
        LatePaymentFollowUpRequest validLp = LatePaymentFollowUpRequest.builder()
                .action("RECORD")
                .reason("Contacted customer via telephone")
                .build();
        assertThat(validator.validate(validLp)).isEmpty();

        LatePaymentFollowUpRequest badLpAction = LatePaymentFollowUpRequest.builder()
                .action("CANCEL")
                .reason("Invalid action")
                .build();
        Set<ConstraintViolation<LatePaymentFollowUpRequest>> vLp = validator.validate(badLpAction);
        assertThat(vLp).anyMatch(v -> v.getMessage().contains("RECORD or ESCALATE"));
    }

    @Test
    @DisplayName("T013-4: WorkshopBookingExceptionHandler maps exceptions into typed ProblemDetailResponse")
    void verifyExceptionHandlerMapping() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "CORR-TEST-123");

        // 401 Unauthorized
        ResponseEntity<ProblemDetailResponse> resp401 = exceptionHandler.handleUnauthorized(
                new WorkshopBookingUnauthorizedException("Token expired"), request);
        assertThat(resp401.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp401.getBody().getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(resp401.getBody().getCorrelationId()).isEqualTo("CORR-TEST-123");

        // 403 Location Scope
        ResponseEntity<ProblemDetailResponse> respScope = exceptionHandler.handleLocationScope(
                new WorkshopBookingLocationScopeException("Location outside scope"), request);
        assertThat(respScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respScope.getBody().getCode()).isEqualTo("LOCATION_SCOPE_DENIED");

        // 403 Forbidden
        ResponseEntity<ProblemDetailResponse> resp403 = exceptionHandler.handleForbidden(
                new WorkshopBookingForbiddenException("Insufficient role"), request);
        assertThat(resp403.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp403.getBody().getCode()).isEqualTo("FORBIDDEN");

        // 404 Not Found
        ResponseEntity<ProblemDetailResponse> resp404 = exceptionHandler.handleNotFound(
                new BookingNotFoundException("Booking BK-999 not found"), request);
        assertThat(resp404.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp404.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");

        // 409 Idempotency Conflict
        ResponseEntity<ProblemDetailResponse> resp409 = exceptionHandler.handleIdempotencyConflict(
                new BookingIdempotencyConflictException("Mismatched payload"), request);
        assertThat(resp409.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp409.getBody().getCode()).isEqualTo("IDEMPOTENCY_CONFLICT");

        // 429 Rate Limit
        ResponseEntity<ProblemDetailResponse> resp429 = exceptionHandler.handleRateLimit(
                new BookingRateLimitException("Too many resend attempts"), request);
        assertThat(resp429.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp429.getBody().getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @Transactional
    @DisplayName("T011-1: Server-side authorization denial records minimal audit evidence without side effect")
    void verifyAuthorizationDenialAuditRecording() {
        String bookingCode = "BK-DENIED-001";
        Long locationId = 1L;

        authorizationService.recordDeniedAttempt(
                bookingCode, locationId, "STAFF", 50L,
                "MAGIC_LINK_REVOKE", "CORR-DENY-01", "Strictly requires MANAGER role");

        List<BookingAuditEvidence> records = auditEvidenceRepository.findByBookingCodeOrderByOccurredAtDesc(bookingCode);
        assertThat(records).isNotEmpty();

        BookingAuditEvidence record = records.get(0);
        assertThat(record.getBookingCode()).isEqualTo(bookingCode);
        assertThat(record.getOutcome()).isEqualTo("DENIED");
        assertThat(record.getAction()).isEqualTo("MAGIC_LINK_REVOKE");
        assertThat(record.getActorType()).isEqualTo("STAFF");
        assertThat(record.getActorId()).isEqualTo(50L);
        assertThat(record.getCorrelationReference()).isEqualTo("CORR-DENY-01");
        assertThat(record.getManagerReason()).contains("Strictly requires MANAGER role");
    }
}
