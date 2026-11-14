package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeAllowanceResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.GrantChangeAllowanceRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketChangeControllerTest {

    private final TicketChangeService changeService = mock(TicketChangeService.class);
    private final TicketChangeController controller = new TicketChangeController(changeService);

    private static final String TICKET_CODE = "GRP-01-L1-T1";
    private static final String IDEMPOTENCY_KEY = "idempotency-key-ticket-change-123";
    private static final UUID TARGET_SESSION = UUID.randomUUID();

    @Test
    void changeTicketSessions_success_returns200() {
        CurrentAccountActor actor = new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(actor, null, List.of());

        TicketChangeRequestDto request = new TicketChangeRequestDto(
                TicketChangeType.ADVANCE,
                List.of(TARGET_SESSION),
                null,
                null,
                null
        );
        ChangeResultDto expected = new ChangeResultDto(
                TICKET_CODE,
                true,
                List.of(TARGET_SESSION),
                "0",
                false,
                "100000",
                true
        );

        when(changeService.changeTicketSessions(eq(TICKET_CODE), eq(request), eq(IDEMPOTENCY_KEY), eq("CORR-01"), eq(actor)))
                .thenReturn(expected);

        ResponseEntity<ChangeResultDto> response = controller.changeTicketSessions(
                TICKET_CODE, IDEMPOTENCY_KEY, "CORR-01", request, auth
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void grantExtraAllowance_success_returns201() {
        CurrentAccountActor actor = new CurrentAccountActor(300L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(actor, null, List.of());

        GrantChangeAllowanceRequestDto request = new GrantChangeAllowanceRequestDto("Manager exception");
        ChangeAllowanceResponseDto expected = new ChangeAllowanceResponseDto(
                "CHG-ALW-01",
                TICKET_CODE,
                "Manager exception",
                Instant.now()
        );

        when(changeService.grantExtraAllowance(eq(TICKET_CODE), eq(request), eq(IDEMPOTENCY_KEY), eq("CORR-02"), eq(actor)))
                .thenReturn(expected);

        ResponseEntity<ChangeAllowanceResponseDto> response = controller.grantExtraAllowance(
                TICKET_CODE, IDEMPOTENCY_KEY, "CORR-02", request, auth
        );

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(expected);
    }
}

