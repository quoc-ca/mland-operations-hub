package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswer;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswerRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswerResponseDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketMlandRemedyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketMlandRemedyControllerTest {

    private final TicketMlandRemedyService remedyService = mock(TicketMlandRemedyService.class);
    private final TicketMlandRemedyController controller = new TicketMlandRemedyController(remedyService);

    private static final String TICKET_CODE = "TK-100";
    private static final String IDEMPOTENCY_KEY = "idempotency-key-mland-remedy-123";

    @Test
    void answerOffer_success_returns200() {
        CurrentAccountActor actor = new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(actor, null, List.of());

        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.ACCEPT_REPLACEMENT, null);
        TicketRemedyAnswerResponseDto expected = new TicketRemedyAnswerResponseDto(
                "MREMEDY-01", TICKET_CODE, "GRP-01", TicketRemedyAnswer.ACCEPT_REPLACEMENT,
                "MOVED", Instant.now(), null, 0L, false
        );

        when(remedyService.answerOffer(eq(TICKET_CODE), eq(request), eq(IDEMPOTENCY_KEY), eq("CORR-01"), eq(actor)))
                .thenReturn(expected);

        ResponseEntity<TicketRemedyAnswerResponseDto> response = controller.answerOffer(
                TICKET_CODE, IDEMPOTENCY_KEY, "CORR-01", request, auth
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void answerOffer_shortIdempotencyKey_throwsValidationException() {
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, null);

        assertThatThrownBy(() -> controller.answerOffer(TICKET_CODE, "short-key", "CORR", request, null))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void answerOffer_nullIdempotencyKey_throwsValidationException() {
        TicketRemedyAnswerRequestDto request = new TicketRemedyAnswerRequestDto(TicketRemedyAnswer.REFUND, null);

        assertThatThrownBy(() -> controller.answerOffer(TICKET_CODE, null, "CORR", request, null))
                .isInstanceOf(WorkshopSettlementValidationException.class)
                .hasMessageContaining("IDEMPOTENCY_KEY_REQUIRED");
    }
}

