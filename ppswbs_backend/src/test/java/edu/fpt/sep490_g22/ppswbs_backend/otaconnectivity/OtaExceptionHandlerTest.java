package edu.fpt.sep490_g22.ppswbs_backend.otaconnectivity;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OtaExceptionHandlerTest {

    private final OtaExceptionHandler handler = new OtaExceptionHandler();

    @Test
    void unauthorizedErrorHasSafeTypedBodyAndGeneratedCorrelationId() {
        var response = handler.unauthorized(new OtaUnauthorizedException("do not expose"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("Identity authentication failed");
        assertThat(UUID.fromString(response.getBody().correlationId())).isNotNull();
    }

    @Test
    void forbiddenAndLocationScopeErrorsAreMappedSeparately() {
        var forbidden = handler.forbidden(new OtaForbiddenException("sensitive internal detail"), request());
        var location = handler.locationScope(new OtaLocationScopeException("private location"), request());

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(forbidden.getBody().message()).doesNotContain("sensitive");
        assertThat(location.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(location.getBody().code()).isEqualTo("LOCATION_SCOPE_DENIED");
    }

    private static HttpServletRequest request() {
        return new MockHttpServletRequest();
    }
}
