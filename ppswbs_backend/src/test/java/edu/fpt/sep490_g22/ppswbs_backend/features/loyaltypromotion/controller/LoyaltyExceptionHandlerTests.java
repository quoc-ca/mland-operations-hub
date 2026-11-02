package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.controller;

import edu.fpt.sep490_g22.ppswbs_backend.common.ErrorResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyErrorDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Error contract of interfaces section 3: status per code, safe body, never cached. */
class LoyaltyExceptionHandlerTests {

    private final LoyaltyExceptionHandler handler = new LoyaltyExceptionHandler();

    @Test
    void everyCodeMapsToTheDocumentedStatusFamily() {
        assertThat(LoyaltyErrorDtos.status(LoyaltyErrorCode.INVALID_INPUT)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(LoyaltyErrorDtos.status(LoyaltyErrorCode.AUTHENTICATION_REQUIRED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(LoyaltyErrorDtos.status(LoyaltyErrorCode.ACCESS_DENIED)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(LoyaltyErrorDtos.status(LoyaltyErrorCode.PROMOTION_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        for (LoyaltyErrorCode code : new LoyaltyErrorCode[] {LoyaltyErrorCode.CODE_EXISTS,
                LoyaltyErrorCode.STALE_VERSION, LoyaltyErrorCode.IDEMPOTENCY_CONFLICT,
                LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT, LoyaltyErrorCode.BENEFIT_STATE_CONFLICT,
                LoyaltyErrorCode.QUOTE_CHANGED, LoyaltyErrorCode.POINTS_LIMIT_CHANGED}) {
            assertThat(LoyaltyErrorDtos.status(code)).as(code.name()).isEqualTo(HttpStatus.CONFLICT);
        }
        for (LoyaltyErrorCode code : new LoyaltyErrorCode[] {LoyaltyErrorCode.PROMOTION_INELIGIBLE,
                LoyaltyErrorCode.POINTS_USE_NOT_ALLOWED, LoyaltyErrorCode.WORKSHOP_NET_AMOUNT_INVALID}) {
            assertThat(LoyaltyErrorDtos.status(code)).as(code.name()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertThat(LoyaltyErrorDtos.status(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(LoyaltyErrorDtos.status(LoyaltyErrorCode.RETRYABLE_CONTENTION))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        for (LoyaltyErrorCode code : LoyaltyErrorCode.values()) {
            assertThat(LoyaltyErrorDtos.defaultMessage(code)).as(code.name()).isNotBlank();
        }
    }

    @Test
    void loyaltyFailureKeepsFieldIssuesAndIsNeverCached() {
        ResponseEntity<ErrorResponse> response = handler.handleLoyalty(
                LoyaltyException.invalidField("code", "Control characters are not allowed."),
                new MockHttpServletRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT");
        assertThat(response.getBody().fieldErrors()).singleElement()
                .satisfies(field -> assertThat(field.field()).isEqualTo("code"));
    }

    @Test
    void lockTimeoutIsRetryableContentionWithoutExposingTheDriverMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleContention(
                new CannotAcquireLockException("Lock wait timeout exceeded; SELECT secret FROM x"),
                new MockHttpServletRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("RETRYABLE_CONTENTION");
        assertThat(response.getBody().message()).doesNotContain("secret", "SELECT");
    }
}
