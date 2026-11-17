package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopOperationsExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopCheckInNotOpenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopUnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T126 (CHK014, CHK028): the error catalogue is closed. Extends the T048 drift check: every code the Workshop
 * Operations handler can return is in the OpenAPI {@code Problem.code} enum (and every enum code is returned
 * somewhere), CHECK_IN_NOT_OPEN carries checkInOpensAt, and 401 is reserved for a missing/malformed/invalid token
 * while a missing account, an inactive account, Admin and an unassigned Staff are 403.
 */
class WorkshopErrorCatalogueContractTest {

    private static final String HANDLER_SOURCE =
            "src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/controller/WorkshopOperationsExceptionHandler.java";
    private static final Pattern CODE_LITERAL = Pattern.compile("\"([A-Z][A-Z_]{2,})\"");

    private final WorkshopOperationsExceptionHandler handler = new WorkshopOperationsExceptionHandler();
    private final AccountFacade accounts = mock(AccountFacade.class);
    private final WorkshopOperationsAuthorizationService authorization = new WorkshopOperationsAuthorizationService(
            accounts, mock(SessionStaffAssignmentRepository.class), mock(WorkshopSessionRepository.class));

    @BeforeEach
    void allowTestTokens() {
        ReflectionTestUtils.setField(authorization, "allowTestTokens", true);
    }

    // ---- CHECK_IN_NOT_OPEN --------------------------------------------------------------------------------------

    @Test
    void checkInNotOpenIs409WithCheckInOpensAtInTheBody() {
        Instant opensAt = Instant.parse("2026-12-01T01:30:00Z");

        ResponseEntity<ProblemDetailResponse> response = handler.handleCheckInNotOpen(
                new WorkshopCheckInNotOpenException("opens later", opensAt), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("CHECK_IN_NOT_OPEN");
        assertThat(response.getBody().getCheckInOpensAt()).isEqualTo(opensAt);
        assertThat(response.getBody().getCorrelationId()).isNotBlank();
    }

    @Test
    void invalidStateForANotOpenSessionHasNoCheckInOpensAt() {
        ResponseEntity<ProblemDetailResponse> response = handler.handleLifecycle(
                new WorkshopLifecycleException("Session is in state CANCELLED"), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_STATE");
        assertThat(response.getBody().getCheckInOpensAt()).isNull();
    }

    @Test
    void checkInOpensAtIsOmittedFromEveryOtherProblemBody() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        ProblemDetailResponse other = handler.handleLifecycle(new WorkshopLifecycleException("x"), new MockHttpServletRequest()).getBody();
        ProblemDetailResponse notOpen = handler.handleCheckInNotOpen(
                new WorkshopCheckInNotOpenException("x", Instant.parse("2026-12-01T01:30:00Z")), new MockHttpServletRequest()).getBody();

        assertThat(mapper.readTree(mapper.writeValueAsString(other)).has("checkInOpensAt")).isFalse();
        assertThat(mapper.readTree(mapper.writeValueAsString(notOpen)).has("checkInOpensAt")).isTrue();
    }

    // ---- closed Problem.code enum -------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void everyCodeTheHandlerReturnsIsInTheOpenApiEnumAndViceVersa() throws Exception {
        Map<String, Object> spec;
        try (InputStream in = new FileInputStream(openApiFile())) {
            spec = new Yaml().load(in);
        }
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
        Map<String, Object> problemProps = (Map<String, Object>) ((Map<String, Object>) schemas.get("Problem")).get("properties");
        Set<String> enumCodes = new TreeSet<>((List<String>) ((Map<String, Object>) problemProps.get("code")).get("enum"));
        assertThat(problemProps).containsKey("checkInOpensAt");

        Set<String> handlerCodes = new TreeSet<>();
        Matcher matcher = CODE_LITERAL.matcher(Files.readString(new File(HANDLER_SOURCE).toPath()));
        while (matcher.find()) {
            handlerCodes.add(matcher.group(1));
        }

        assertThat(handlerCodes).as("codes returned by WorkshopOperationsExceptionHandler").isNotEmpty();
        assertThat(enumCodes).as("the catalogue is closed: the handler may not return a code outside Problem.code")
                .containsAll(handlerCodes);
        assertThat(handlerCodes).as("every catalogue code is produced by the handler")
                .containsAll(enumCodes);
    }

    private static File openApiFile() {
        for (String path : new String[] {
                "../ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml",
                "ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml"}) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }
        throw new IllegalStateException("OpenAPI contract file not found");
    }

    // ---- 401 only for a missing / invalid / expired token; 403 for everything else ---------------------------------

    private static AccountSummary account(ActorRole role, ActorStatus status) {
        return new AccountSummary(1L, role, status, "x@x.test", Instant.now(), Instant.now());
    }

    @Test
    void aMissingMalformedEmptyOrInvalidTokenIs401() {
        for (String header : new String[] {null, "", "Basic abc", "Bearer", "Bearer ", "Bearer not-a-real-token"}) {
            assertThatThrownBy(() -> authorization.authenticate(header))
                    .as("header %s", header)
                    .isInstanceOf(WorkshopUnauthorizedException.class);
        }
        ResponseEntity<ProblemDetailResponse> response = handler.handleUnauthorized(
                new WorkshopUnauthorizedException("Invalid, expired or revoked identity token"), new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void aNoAccountInactiveOrAdminCallerIs403NeverA401() {
        when(accounts.resolveAccount("none")).thenReturn(Optional.empty());
        when(accounts.resolveAccount("suspended")).thenReturn(Optional.of(account(ActorRole.STAFF, ActorStatus.SUSPENDED)));
        when(accounts.resolveAccount("admin")).thenReturn(Optional.of(account(ActorRole.ADMIN, ActorStatus.ACTIVE)));
        when(accounts.resolveAccount("staff")).thenReturn(Optional.of(account(ActorRole.STAFF, ActorStatus.ACTIVE)));

        assertThatThrownBy(() -> authorization.authenticate("Bearer test-uid:none")).isInstanceOf(WorkshopForbiddenException.class);
        assertThatThrownBy(() -> authorization.authenticate("Bearer test-uid:suspended")).isInstanceOf(WorkshopForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireGlobalManager("Bearer test-uid:admin")).isInstanceOf(WorkshopForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireGlobalManager("Bearer test-uid:staff")).isInstanceOf(WorkshopForbiddenException.class);

        ResponseEntity<ProblemDetailResponse> response = handler.handleForbidden(
                new WorkshopForbiddenException("denied"), new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
    }
}
