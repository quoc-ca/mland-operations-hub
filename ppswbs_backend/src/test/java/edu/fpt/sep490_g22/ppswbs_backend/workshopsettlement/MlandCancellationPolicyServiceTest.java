package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundPolicyRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicyRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MlandCancellationPolicyServiceTest {
    @Mock SettlementRefundPolicyRevisionRepository revisions;
    @Mock WorkshopSettlementAuthorizationService authorization;

    @Test void managerCreatesFutureGlobalRevision() {
        var manager = new AccountSummary(7L, ActorRole.MANAGER, ActorStatus.ACTIVE,
                "manager@example.test", Instant.now(), Instant.now());
        when(authorization.requireManager("uid")).thenReturn(manager);
        when(revisions.existsByEffectiveAt(any())).thenReturn(false);
        when(revisions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = new MlandCancellationPolicyService(revisions, authorization)
                .create("uid", Instant.now().plusSeconds(3600), 80);
        assertThat(result.getWholeBookingRefundPercent()).isEqualTo(80);
        assertThat(result.getCreatedByAccountId()).isEqualTo(7L);
    }

    @Test void nonFutureAndInvalidPercentFailClosed() {
        when(authorization.requireManager("uid")).thenReturn(new AccountSummary(7L, ActorRole.MANAGER,
                ActorStatus.ACTIVE, "manager@example.test", Instant.now(), Instant.now()));
        var service = new MlandCancellationPolicyService(revisions, authorization);
        assertThatThrownBy(() -> service.create("uid", Instant.now().minusSeconds(1), 80))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        assertThatThrownBy(() -> service.create("uid", Instant.now().plusSeconds(3600), 101))
                .isInstanceOf(WorkshopSettlementValidationException.class);
        verify(authorization, times(2)).requireManager("uid");
        verifyNoInteractions(revisions);
    }

    @Test void listRequiresManagerAndReturnsGlobalHistory() {
        when(revisions.findAllByOrderByEffectiveAtAsc()).thenReturn(List.of());
        var service = new MlandCancellationPolicyService(revisions, authorization);
        assertThat(service.list("uid")).isEmpty();
        verify(authorization).requireManager("uid");
    }
}
