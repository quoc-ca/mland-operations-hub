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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopSettlementAuthorizationServiceTest {
    @Mock AccountFacade accounts;
    @Mock SettlementBusinessAuditRepository audits;

    @Test void deniedRoleWritesSafeAuditAndThrows() {
        AccountSummary member = new AccountSummary(7L, ActorRole.MEMBER, ActorStatus.ACTIVE,
                "member@example.test", Instant.now(), Instant.now());
        when(accounts.resolveAccount("uid")).thenReturn(Optional.of(member));
        WorkshopSettlementAuthorizationService service = new WorkshopSettlementAuthorizationService(accounts, audits);
        assertThatThrownBy(() -> service.requireManager("uid"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("MANAGER_REQUIRED");
        verify(audits).save(argThat(audit -> audit.getActionCode().equals("DENIED_MANAGER_REQUIRED")
                && audit.getActorReference().equals("7") && audit.getEvidenceReference() == null));
    }

    @Test void unresolvedIdentityAuditsWithoutPersistingRawUid() {
        when(accounts.resolveAccount("raw-token-like-value")).thenReturn(Optional.empty());
        WorkshopSettlementAuthorizationService service = new WorkshopSettlementAuthorizationService(accounts, audits);
        assertThatThrownBy(() -> service.requireActive("raw-token-like-value"))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class)
                .hasMessage("ACCOUNT_NOT_FOUND");
        verify(audits).save(argThat(audit -> audit.getActorReference().equals("UNKNOWN")));
        verify(audits, never()).save(argThat(audit -> "raw-token-like-value".equals(audit.getActorReference())));
    }
}
