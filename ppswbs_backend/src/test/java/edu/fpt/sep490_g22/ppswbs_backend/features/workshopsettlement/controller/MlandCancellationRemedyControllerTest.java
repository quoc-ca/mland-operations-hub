package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.MlandRemedyCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.MlandCancellationRemedyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MlandCancellationRemedyControllerTest {
    @Test
    void openDelegatesManagerActorAndReturnsCreated() {
        var service = mock(MlandCancellationRemedyService.class);
        var controller = new MlandCancellationRemedyController(service);
        var actor = manager();
        var auth = new UsernamePasswordAuthenticationToken(actor, "n/a");
        var command = new MlandRemedyCommandDto("reason", "UNAVAILABLE", "HANDOFF-1");
        var result = new SettlementCaseProjectionDto("CASE-1", "MLAND_CANCELLATION_REMEDY",
                "PENDING_PROVIDER", "BK-1", "CORR-1");
        when(service.open("BK-1", "key-1", "corr-1", command, actor)).thenReturn(result);

        var response = controller.open("BK-1", "key-1", "corr-1", command, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(result);
        verify(service).open("BK-1", "key-1", "corr-1", command, actor);
    }

    @Test
    void readPassesGuestProofAndDoesNotRequireBearerPrincipalAtController() {
        var service = mock(MlandCancellationRemedyService.class);
        var controller = new MlandCancellationRemedyController(service);
        var result = new SettlementCaseProjectionDto("CASE-1", "MLAND_CANCELLATION_REMEDY",
                "PENDING_PROVIDER", "BK-1", "CORR-1");
        when(service.read("BK-1", "CASE-1", "guest@example.test", null, null)).thenReturn(result);

        var response = controller.read("BK-1", "CASE-1", "guest@example.test", null, null);

        assertThat(response).isEqualTo(result);
        verify(service).read("BK-1", "CASE-1", "guest@example.test", null, null);
    }

    private static CurrentAccountActor manager() {
        return new CurrentAccountActor(9L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    }
}
