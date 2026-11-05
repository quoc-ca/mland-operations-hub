package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdentityGateFilterTest {

    private final ExposedIdentityGateFilter filter = new ExposedIdentityGateFilter();

    @Test
    void guestCanLoadPackageLocationsWithoutBearerToken() {
        assertThat(filter.skips("GET", "/api/v1/workshop-packages/145/locations")).isTrue();
    }

    @Test
    void guestCanLoadPackageSessionsWithoutBearerToken() {
        assertThat(filter.skips("GET", "/api/v1/workshop-packages/145/sessions")).isTrue();
    }

    @Test
    void guestCanLoadPackageDiscoveryRouteWithTrailingSlashWithoutBearerToken() {
        assertThat(filter.skips("GET", "/api/v1/workshop-packages/145/locations/")).isTrue();
    }

    @Test
    void guestCanLoadPackageDiscoveryRouteBelowContextPathWithoutBearerToken() {
        assertThat(filter.skips("GET", "/mland/api/v1/workshop-packages/145/sessions", "/mland")).isTrue();
    }

    @Test
    void packageMutationStillRequiresBearerToken() {
        assertThat(filter.skips("POST", "/api/v1/workshop-packages/145/sessions")).isFalse();
    }

    private static final class ExposedIdentityGateFilter extends IdentityGateFilter {
        private ExposedIdentityGateFilter() {
            super(mock(TokenVerificationService.class), mock(AccountFacade.class), new ObjectMapper());
        }

        private boolean skips(String method, String uri) {
            return skips(method, uri, "");
        }

        private boolean skips(String method, String uri, String contextPath) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
            request.setRequestURI(uri);
            request.setContextPath(contextPath);
            return shouldNotFilter(request);
        }
    }
}
