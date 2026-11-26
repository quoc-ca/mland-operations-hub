package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeOptionsDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GuestChangeOptionLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.GroupTicketChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeOptionsApiService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeRequestService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FR-044a / T177: both listing routes are no-store and pass the actor or the guest proof straight to the service. */
class TicketChangeOptionsControllerTest {

    private final TicketChangeOptionsApiService options = mock(TicketChangeOptionsApiService.class);
    private final TicketChangeController controller = new TicketChangeController(
            mock(TicketChangeService.class), mock(TicketChangeRequestService.class),
            mock(GroupTicketChangeService.class), options);
    private final ChangeOptionsDto body = new ChangeOptionsDto("T1", TicketChangeType.ADVANCE, null, null, null,
            true, true, List.of(), null);

    @Test
    @DisplayName("GET change-options passes the bearer actor, type and from, and is Cache-Control no-store")
    void actorRoute() {
        CurrentAccountActor actor = new CurrentAccountActor(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, false, List.of());
        UUID from = UUID.randomUUID();
        when(options.forActor("T1", TicketChangeType.LATE_ARRIVAL, List.of(from), actor)).thenReturn(body);

        ResponseEntity<ChangeOptionsDto> response = controller.changeOptions("T1", TicketChangeType.LATE_ARRIVAL,
                List.of(from), new UsernamePasswordAuthenticationToken(actor, "n/a"));

        assertThat(response.getBody()).isSameAs(body);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    @DisplayName("POST change-option-lookups passes the guest proof and is Cache-Control no-store")
    void guestRoute() {
        GuestChangeOptionLookupRequest request = new GuestChangeOptionLookupRequest(
                "buyer@example.com", null, TicketChangeType.ADVANCE, null);
        when(options.forGuest("T1", request)).thenReturn(body);

        ResponseEntity<ChangeOptionsDto> response = controller.lookupChangeOptions("T1", request);

        assertThat(response.getBody()).isSameAs(body);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        verify(options).forGuest("T1", request);
    }

    @Test
    @DisplayName("an anonymous call to the bearer route reaches the service with no actor, which refuses it")
    void anonymousActorIsNull() {
        controller.changeOptions("T1", TicketChangeType.ADVANCE, null, null);
        verify(options).forActor("T1", TicketChangeType.ADVANCE, null, null);
    }
}
