package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopOperationsExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopDependencyUnavailableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLastStaffAssignmentException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopStaffNotEligibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 003.2 T128 (fail closed on Account facade failure) and T129 (no Admin business authority), plus handler mapping. */
class WorkshopOperationsAuthorizationServiceTest {

    private final AccountFacade accounts = mock(AccountFacade.class);
    private final WorkshopOperationsAuthorizationService authorization = new WorkshopOperationsAuthorizationService(
            accounts, mock(SessionStaffAssignmentRepository.class), mock(WorkshopSessionRepository.class));

    @BeforeEach
    void allowTestTokens() {
        ReflectionTestUtils.setField(authorization, "allowTestTokens", true);
    }

    private static AccountSummary account(ActorRole role) {
        return new AccountSummary(1L, role, ActorStatus.ACTIVE, "x@x.test", Instant.now(), Instant.now());
    }

    // ---- T128 -------------------------------------------------------------------------------------------------

    @Test
    void aFailingResolveAccountFailsClosedAsDependencyUnavailable() {
        when(accounts.resolveAccount("uid-1")).thenThrow(new IllegalStateException("account module down"));

        assertThatThrownBy(() -> authorization.authenticate("Bearer test-uid:uid-1"))
                .isInstanceOf(WorkshopDependencyUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailingLocationScopeReadFailsClosed() {
        when(accounts.getLocationScope(1L)).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> authorization.getLocationScope(1L)).isInstanceOf(WorkshopDependencyUnavailableException.class);
    }

    @Test
    void aFailingTargetAccountReadFailsClosed() {
        when(accounts.getTargetAccount(5L)).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> authorization.getTargetAccount(5L)).isInstanceOf(WorkshopDependencyUnavailableException.class);
    }

    @Test
    void aFailingStatusReadFailsClosed() {
        when(accounts.getAccountStatus(5L)).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> authorization.getAccountStatus(5L)).isInstanceOf(WorkshopDependencyUnavailableException.class);
    }

    @Test
    void anUnknownAccountIsStillAPlain403NotA503() {
        when(accounts.resolveAccount("uid-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorization.authenticate("Bearer test-uid:uid-2")).isInstanceOf(WorkshopForbiddenException.class);
    }

    @Test
    void theHandlerMapsDependencyFailureTo503WithoutLeakingTheCause() {
        ResponseEntity<ProblemDetailResponse> response = new WorkshopOperationsExceptionHandler().handleDependencyUnavailable(
                new WorkshopDependencyUnavailableException("The account service is temporarily unavailable", new RuntimeException("jdbc:secret")),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(response.getBody().getDetail()).doesNotContain("jdbc");
    }

    // ---- T129 -------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"ADMIN", "STAFF", "MEMBER"})
    void onlyAnActiveManagerIsAGlobalWorkshopActor(ActorRole role) {
        assertThat(authorization.isGlobalWorkshopActor(account(role))).isFalse();
        assertThat(authorization.isGlobalWorkshopActor(account(ActorRole.MANAGER))).isTrue();
    }

    @Test
    void requireGlobalManagerRefusesAnAdmin() {
        when(accounts.resolveAccount("admin")).thenReturn(Optional.of(account(ActorRole.ADMIN)));

        assertThatThrownBy(() -> authorization.requireGlobalManager("Bearer test-uid:admin")).isInstanceOf(WorkshopForbiddenException.class);
    }

    // ---- handler mapping of the T115 / T130 exceptions ----------------------------------------------------------

    @Test
    void theHandlerReturnsStaffNotEligibleWithItsReason() {
        ResponseEntity<ProblemDetailResponse> response = new WorkshopOperationsExceptionHandler().handleStaffNotEligible(
                new WorkshopStaffNotEligibleException(WorkshopStaffNotEligibleException.Reason.NOT_STAFF, "not staff"),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("STAFF_NOT_ELIGIBLE");
        assertThat(response.getBody().getReason()).isEqualTo("NOT_STAFF");
    }

    @Test
    void theHandlerReturnsTheLastAssignmentConfirmationCode() {
        ResponseEntity<ProblemDetailResponse> response = new WorkshopOperationsExceptionHandler().handleLastStaffAssignment(
                new WorkshopLastStaffAssignmentException("confirm"), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED");
        assertThat(response.getBody().getReason()).isNull();
    }
}
