package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopBookingAuthorizationServiceTest {

    @Mock private AccountFacade accounts;
    @Mock private BookingAuditEvidenceRepository auditEvidence;

    private WorkshopBookingAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        authorization = new WorkshopBookingAuthorizationService(accounts, auditEvidence);
        ReflectionTestUtils.setField(authorization, "allowTestTokens", true);
    }

    @Test
    void anyActiveManagerCanHandleMagicLinkIncidentsGlobally() {
        when(accounts.resolveAccount("manager")).thenReturn(Optional.of(actor(1L, ActorRole.MANAGER)));
        AccountSummary manager = authorization.requireMagicLinkIncidentAuthority(
                token("manager"), 987654L);
        AccountSummary resendManager = authorization.requireConfirmationResendAuthority(
                token("manager"), 987654L);

        assertThat(manager.accountId()).isEqualTo(1L);
        assertThat(manager.role()).isEqualTo(ActorRole.MANAGER);
        assertThat(resendManager.accountId()).isEqualTo(1L);
        verify(accounts, never()).getLocationScope(1L);
    }

    @Test
    void staffAndAdminCannotHandleMagicLinkIncidents() {
        when(accounts.resolveAccount("staff")).thenReturn(Optional.of(actor(2L, ActorRole.STAFF)));
        when(accounts.resolveAccount("admin")).thenReturn(Optional.of(actor(3L, ActorRole.ADMIN)));
        when(accounts.getLocationScope(2L)).thenReturn(java.util.List.of(10L));

        assertThat(authorization.requireConfirmationResendAuthority(token("staff"), 10L).accountId()).isEqualTo(2L);
        assertThatThrownBy(() -> authorization.requireConfirmationResendAuthority(token("staff"), 11L))
                .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingLocationScopeException.class);
        assertThatThrownBy(() -> authorization.requireConfirmationResendAuthority(token("admin"), 10L))
                .isInstanceOf(WorkshopBookingForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireMagicLinkIncidentAuthority(token("staff"), 10L))
                .isInstanceOf(WorkshopBookingForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireMagicLinkIncidentAuthority(token("admin"), 10L))
                .isInstanceOf(WorkshopBookingForbiddenException.class);
    }

    private static String token(String uid) {
        return "Bearer test-uid:" + uid;
    }

    private static AccountSummary actor(Long id, ActorRole role) {
        return new AccountSummary(id, role, ActorStatus.ACTIVE, "test@example.invalid", Instant.EPOCH, Instant.EPOCH);
    }
}
