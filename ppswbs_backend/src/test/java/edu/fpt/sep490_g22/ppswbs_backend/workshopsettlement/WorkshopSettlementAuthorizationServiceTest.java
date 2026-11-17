package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopSettlementAuthorizationServiceTest {
    @Mock AccountFacade accounts;
    @Mock SettlementBusinessAuditRepository audits;
    @Mock edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter operations;

    private static edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor staff(java.util.List<Long> locations) {
        return new edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor(
                33L, ActorRole.STAFF, ActorStatus.ACTIVE, true, locations);
    }

    @Test void staffInScopeAndAssignedToTheBookingsSessionIsAllowed() {
        when(operations.staffAssignedToBooking(33L, "BK-1")).thenReturn(true);
        var service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        assertThat(service.requireManagerOrAssignedStaff(staff(java.util.List.of(4L)), 4L, "BK-1").accountId()).isEqualTo(33L);
    }

    @Test void staffInScopeButNotAssignedToTheBookingIsDeniedAndAudited() {
        when(operations.staffAssignedToBooking(33L, "BK-1")).thenReturn(false);
        var service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        assertThatThrownBy(() -> service.requireManagerOrAssignedStaff(staff(java.util.List.of(4L)), 4L, "BK-1"))
                .hasMessage("SESSION_ASSIGNMENT_REQUIRED");
        verify(audits).save(any());
    }

    @Test void staffOutsideTheLocationIsDeniedBeforeAssignmentIsConsulted() {
        var service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        assertThatThrownBy(() -> service.requireManagerOrAssignedStaff(staff(java.util.List.of(9L)), 4L, "BK-1"))
                .hasMessage("LOCATION_SCOPE_DENIED");
        verifyNoInteractions(operations);
    }

    @Test void aManagerNeedsNoAssignment() {
        var manager = new edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor(
                10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, java.util.List.of());
        var service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        assertThat(service.requireManagerOrAssignedStaff(manager, 4L, "BK-1").accountId()).isEqualTo(10L);
        verifyNoInteractions(operations);
    }

    @Test void deniedRoleWritesSafeAuditAndThrows() {
        AccountSummary member = new AccountSummary(7L, ActorRole.MEMBER, ActorStatus.ACTIVE,
                "member@example.test", Instant.now(), Instant.now());
        when(accounts.resolveAccount("uid")).thenReturn(Optional.of(member));
        WorkshopSettlementAuthorizationService service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        assertThatThrownBy(() -> service.requireManager("uid"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("MANAGER_REQUIRED");
        verify(audits).save(argThat(audit -> audit.getActionCode().equals("DENIED_MANAGER_REQUIRED")
                && audit.getActorReference().equals("7") && audit.getEvidenceReference() == null));
    }

    @Test void unresolvedIdentityAuditsWithoutPersistingRawUid() {
        when(accounts.resolveAccount("raw-token-like-value")).thenReturn(Optional.empty());
        WorkshopSettlementAuthorizationService service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        assertThatThrownBy(() -> service.requireActive("raw-token-like-value"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("ACCOUNT_NOT_FOUND");
        verify(audits).save(argThat(audit -> audit.getActorReference().equals("UNKNOWN")));
        verify(audits, never()).save(argThat(audit -> "raw-token-like-value".equals(audit.getActorReference())));
    }

    @Test void canonicalManagerIsGlobalWhileStaffRemainsLocationScoped() {
        WorkshopSettlementAuthorizationService service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        CurrentAccountActor manager = new CurrentAccountActor(42L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, java.util.List.of());
        CurrentAccountActor staff = new CurrentAccountActor(43L, ActorRole.STAFF, ActorStatus.ACTIVE, true, java.util.List.of(9L));

        org.assertj.core.api.Assertions.assertThat(service.requireManagerOrStaff(manager, 700L)).isEqualTo(manager);
        org.assertj.core.api.Assertions.assertThat(service.requireManagerOrStaff(staff, 9L)).isEqualTo(staff);
        assertThatThrownBy(() -> service.requireManagerOrStaff(staff, 700L))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("LOCATION_SCOPE_DENIED");
    }

    @Test void managerAssignedToLocationACanActOnLocationBAndWithUnavailableScope() {
        WorkshopSettlementAuthorizationService service = new WorkshopSettlementAuthorizationService(accounts, audits, operations);
        // Manager assigned to Location A (101L) can perform explicitly manager-authorized action on Location B (202L)
        CurrentAccountActor managerLocationA = new CurrentAccountActor(42L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, java.util.List.of(101L));
        org.assertj.core.api.Assertions.assertThat(service.requireManagerOrStaff(managerLocationA, 202L)).isEqualTo(managerLocationA);

        // Manager with empty/unavailable location scope dependency can still perform action on Location B
        CurrentAccountActor managerUnavailableScope = new CurrentAccountActor(42L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, java.util.List.of());
        org.assertj.core.api.Assertions.assertThat(service.requireManagerOrStaff(managerUnavailableScope, 202L)).isEqualTo(managerUnavailableScope);

        // Staff assigned to Location A (101L) is denied on Location B (202L)
        CurrentAccountActor staffLocationA = new CurrentAccountActor(43L, ActorRole.STAFF, ActorStatus.ACTIVE, true, java.util.List.of(101L));
        assertThatThrownBy(() -> service.requireManagerOrStaff(staffLocationA, 202L))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("LOCATION_SCOPE_DENIED");

        // Staff with unavailable/empty location scope is denied
        CurrentAccountActor staffUnavailableScope = new CurrentAccountActor(43L, ActorRole.STAFF, ActorStatus.ACTIVE, true, java.util.List.of());
        assertThatThrownBy(() -> service.requireManagerOrStaff(staffUnavailableScope, 101L))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("LOCATION_SCOPE_DENIED");
    }
}
