package edu.fpt.sep490_g22.ppswbs_backend.otaconnectivity;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtaAuthorizationServiceTest {

    @Mock private AccountFacade accountFacade;

    private OtaAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        authorization = new OtaAuthorizationService(accountFacade);
        ReflectionTestUtils.setField(authorization, "allowTestTokens", true);
        lenient().when(accountFacade.resolveAccount("manager"))
                .thenReturn(Optional.of(actor(1L, ActorRole.MANAGER, ActorStatus.ACTIVE)));
        lenient().when(accountFacade.resolveAccount("admin"))
                .thenReturn(Optional.of(actor(2L, ActorRole.ADMIN, ActorStatus.ACTIVE)));
        lenient().when(accountFacade.resolveAccount("staff"))
                .thenReturn(Optional.of(actor(3L, ActorRole.STAFF, ActorStatus.ACTIVE)));
        lenient().when(accountFacade.resolveAccount("suspended"))
                .thenReturn(Optional.of(actor(4L, ActorRole.MANAGER, ActorStatus.SUSPENDED)));
    }

    @Test
    void managerBusinessAndFinanceActionsAreGlobalRegardlessOfLocationAssignments() {
        assertThat(authorization.requireManager(token("manager")).accountId()).isEqualTo(1L);
        assertThat(authorization.requireManagerOrAdmin(token("manager")).accountId()).isEqualTo(1L);
        assertThat(authorization.requireMappingAdministration(token("manager")).accountId()).isEqualTo(1L);
        verify(accountFacade, never()).getLocationScope(1L);
    }

    @Test
    void adminCanUseAdminActionsButCannotUseManagerOnlyFinanceActions() {
        assertThat(authorization.requireAdmin(token("admin")).accountId()).isEqualTo(2L);
        assertThat(authorization.requireManagerOrAdmin(token("admin")).accountId()).isEqualTo(2L);
        assertThatThrownBy(() -> authorization.requireManager(token("admin")))
                .isInstanceOf(OtaForbiddenException.class);
    }

    @Test
    void staffIsLocationScopedAndCannotUseManagerOrAdminActions() {
        when(accountFacade.getLocationScope(3L)).thenReturn(List.of(10L));
        assertThat(authorization.requireStaffLocation(token("staff"), 10L).accountId()).isEqualTo(3L);
        assertThatThrownBy(() -> authorization.requireStaffLocation(token("staff"), 20L))
                .isInstanceOf(OtaLocationScopeException.class);
        assertThatThrownBy(() -> authorization.requireManager(token("staff")))
                .isInstanceOf(OtaForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireAdmin(token("staff")))
                .isInstanceOf(OtaForbiddenException.class);
    }

    @Test
    void inactiveAccountAndMissingBearerAreRejected() {
        assertThatThrownBy(() -> authorization.requireManager(token("suspended")))
                .isInstanceOf(OtaForbiddenException.class);
        assertThatThrownBy(() -> authorization.authenticate(null))
                .isInstanceOf(OtaUnauthorizedException.class);
    }

    private static String token(String uid) {
        return "Bearer test-uid:" + uid;
    }

    private static AccountSummary actor(Long id, ActorRole role, ActorStatus status) {
        return new AccountSummary(id, role, status, "test@example.invalid", Instant.EPOCH, Instant.EPOCH);
    }
}
