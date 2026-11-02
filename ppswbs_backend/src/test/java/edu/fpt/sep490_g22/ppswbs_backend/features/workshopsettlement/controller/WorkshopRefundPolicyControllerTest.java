package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RefundPolicyRevisionCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicyRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.MlandCancellationPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkshopRefundPolicyControllerTest {
    @Test
    void listUsesResolvedActorInsteadOfAuthorizationHeader() {
        var service = mock(MlandCancellationPolicyService.class);
        var controller = new WorkshopRefundPolicyController(service);
        var actor = manager();
        when(service.list(actor)).thenReturn(List.of());

        assertThat(controller.list(new UsernamePasswordAuthenticationToken(actor, "n/a"))).isEmpty();
        verify(service).list(actor);
    }

    @Test
    void createPassesManagerActorAndIdempotencyKey() {
        var service = mock(MlandCancellationPolicyService.class);
        var controller = new WorkshopRefundPolicyController(service);
        var actor = manager();
        Instant effectiveAt = Instant.parse("2030-01-01T00:00:00Z");
        var request = new RefundPolicyRevisionCommandDto(effectiveAt, 80);
        var revision = SettlementRefundPolicyRevision.builder().policyReference("POLICY-1")
                .effectiveAt(effectiveAt).wholeBookingRefundPercent(80).createdByAccountId(9L).build();
        when(service.create(actor, effectiveAt, 80, "key-1")).thenReturn(revision);

        var response = controller.create(new UsernamePasswordAuthenticationToken(actor, "n/a"), "key-1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().policyReference()).isEqualTo("POLICY-1");
        verify(service).create(actor, effectiveAt, 80, "key-1");
    }

    private static CurrentAccountActor manager() {
        return new CurrentAccountActor(9L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    }
}
