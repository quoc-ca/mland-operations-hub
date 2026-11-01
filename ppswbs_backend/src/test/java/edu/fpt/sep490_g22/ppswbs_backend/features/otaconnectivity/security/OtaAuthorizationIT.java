package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.AuthenticatedOtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Security-boundary acceptance checks for the implemented OTA authorization and safe evidence types. */
class OtaAuthorizationIT {

    private final AccountFacade accounts = mock(AccountFacade.class);
    private final OtaAuthorizationService authorization = new OtaAuthorizationService(accounts);
    private final OtaAuditCorrelationService audit = new OtaAuditCorrelationService();
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(OtaAuditCorrelationService.class);
    private final ListAppender<ILoggingEvent> logCapture = new ListAppender<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authorization, "allowTestTokens", true);
        when(accounts.resolveAccount("manager")).thenReturn(Optional.of(actor(1L, ActorRole.MANAGER, ActorStatus.ACTIVE)));
        when(accounts.resolveAccount("staff")).thenReturn(Optional.of(actor(2L, ActorRole.STAFF, ActorStatus.ACTIVE)));
        when(accounts.resolveAccount("admin")).thenReturn(Optional.of(actor(3L, ActorRole.ADMIN, ActorStatus.ACTIVE)));
        when(accounts.resolveAccount("inactive")).thenReturn(Optional.of(actor(4L, ActorRole.MANAGER, ActorStatus.SUSPENDED)));
        logCapture.start();
        auditLogger.addAppender(logCapture);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(logCapture);
        logCapture.stop();
    }

    @Test
    void managerBusinessAndFinanceAuthorizationIsGlobal() {
        String bearer = bearer("manager");

        assertThat(authorization.requireManager(bearer).accountId()).isEqualTo(1L);
        assertThat(authorization.requireManagerOrAdmin(bearer).accountId()).isEqualTo(1L);
        assertThat(authorization.requireMappingAdministration(bearer).accountId()).isEqualTo(1L);
        verify(accounts, never()).getLocationScope(1L);
    }

    @Test
    void staffAuthorizationRequiresAnAssignedLocationAndDoesNotGrantManagerActions() {
        when(accounts.getLocationScope(2L)).thenReturn(List.of(10L, 11L));

        assertThat(authorization.requireStaffLocation(bearer("staff"), 11L).accountId()).isEqualTo(2L);
        assertThatThrownBy(() -> authorization.requireStaffLocation(bearer("staff"), 12L))
                .isInstanceOf(OtaLocationScopeException.class);
        assertThatThrownBy(() -> authorization.requireManager(bearer("staff")))
                .isInstanceOf(OtaForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireAdmin(bearer("staff")))
                .isInstanceOf(OtaForbiddenException.class);
    }

    @Test
    void adminOnlyAndActiveAccountRulesAreEnforced() {
        assertThat(authorization.requireAdmin(bearer("admin")).accountId()).isEqualTo(3L);
        assertThatThrownBy(() -> authorization.requireManager(bearer("admin")))
                .isInstanceOf(OtaForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireManager(bearer("inactive")))
                .isInstanceOf(OtaForbiddenException.class);
    }

    @Test
    void typedErrorsUseSafeMessagesAndTheRequestCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CorrelationIdFilter.ATTRIBUTE, correlationId);

        var response = new OtaExceptionHandler().forbidden(
                new OtaForbiddenException("credential-sentinel-must-not-leak"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).doesNotContain("credential-sentinel");
        assertThat(response.getBody().correlationId()).isEqualTo(correlationId);
    }

    @Test
    void integrationAuditDoesNotEchoUntrustedCorrelationOrSensitivePayloadValues() {
        String sentinel = "private-callback-sentinel-7f8a";

        audit.record(OtaAuditCorrelationService.IntegrationAction.INBOUND_EVENT_RECEIVED,
                OtaProvider.KLOOK, null, sentinel, OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED);

        assertThat(logCapture.list).hasSize(1);
        assertThat(logCapture.list.getFirst().getFormattedMessage()).doesNotContain(sentinel);
        assertThat(logCapture.list.getFirst().getFormattedMessage()).contains("correlationReference=");
    }

    @Test
    void inboundPersistenceAndErrorBoundariesHaveNoRawProviderPayloadField() {
        assertThat(List.of(AuthenticatedOtaInboundEvent.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .containsExactly("provider", "environment", "externalEventReference", "payloadFingerprint", "eventKind");
        assertThat(List.of(OtaInboundEvent.class.getDeclaredFields()))
                .filteredOn(field -> !Modifier.isStatic(field.getModifiers()))
                .extracting(field -> field.getName())
                .doesNotContain("rawPayload", "requestBody", "responseBody", "customerData", "credentialValue");
    }

    private static String bearer(String uid) {
        return "Bearer test-uid:" + uid;
    }

    private static AccountSummary actor(Long id, ActorRole role, ActorStatus status) {
        return new AccountSummary(id, role, status, "test@example.invalid", Instant.EPOCH, Instant.EPOCH);
    }
}
