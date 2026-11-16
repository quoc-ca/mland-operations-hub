package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopOperationsExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopRateLimitException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopTicketResendRefusedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** T127/T139: the resend refusals map to 429 + Retry-After and 409 INVALID_STATE + reason. */
class WorkshopOperationsResendHandlerTest {
    private final WorkshopOperationsExceptionHandler handler = new WorkshopOperationsExceptionHandler();

    @Test
    void throttleSetsRetryAfterHeaderAndCode() {
        ResponseEntity<ProblemDetailResponse> r = handler.handleRateLimit(
                new WorkshopRateLimitException("limit", 421), new MockHttpServletRequest());
        assertThat(r.getStatusCode().value()).isEqualTo(429);
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("421");
        assertThat(r.getBody().getCode()).isEqualTo("REDELIVERY_THROTTLED");
        assertThat(r.getBody().getReason()).isNull();
    }

    @Test
    void throttleWithoutKnownWaitHasNoRetryAfterHeader() {
        ResponseEntity<ProblemDetailResponse> r = handler.handleRateLimit(
                new WorkshopRateLimitException("limit"), new MockHttpServletRequest());
        assertThat(r.getHeaders().containsKey("Retry-After")).isFalse();
    }

    @Test
    void resendRefusalsAreConflictInvalidStateWithReason() {
        for (WorkshopTicketResendRefusedException.Reason reason : WorkshopTicketResendRefusedException.Reason.values()) {
            ResponseEntity<ProblemDetailResponse> r = handler.handleTicketResendRefused(
                    new WorkshopTicketResendRefusedException(reason, "refused"), new MockHttpServletRequest());
            assertThat(r.getStatusCode().value()).isEqualTo(409);
            assertThat(r.getBody().getCode()).isEqualTo("INVALID_STATE");
            assertThat(r.getBody().getReason()).isEqualTo(reason.name());
        }
    }
}
