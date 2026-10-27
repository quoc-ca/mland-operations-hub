package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalRetailActorResolverTest {

    private final LocalRetailActorResolver resolver = new LocalRetailActorResolver();

    @Test
    void resolvesAPositiveDebugAccountId() {
        HttpServletRequest request = requestWithAccountId("99");

        RetailMemberActor actor = resolver.requireActiveMember(request);

        assertThat(actor.accountId()).isEqualTo(99L);
    }

    @Test
    void rejectsAMissingDebugAccountId() {
        HttpServletRequest request = requestWithAccountId(null);

        assertThatThrownBy(() -> resolver.requireActiveMember(request))
            .isInstanceOf(RetailUnauthorizedException.class);
    }

    @Test
    void rejectsANonPositiveOrMalformedDebugAccountId() {
        assertThatThrownBy(() -> resolver.requireActiveMember(requestWithAccountId("0")))
            .isInstanceOf(RetailUnauthorizedException.class);
        assertThatThrownBy(() -> resolver.requireActiveMember(requestWithAccountId("not-a-number")))
            .isInstanceOf(RetailUnauthorizedException.class);
    }

    private HttpServletRequest requestWithAccountId(String accountId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(LocalRetailActorResolver.DEBUG_ACCOUNT_ID_HEADER)).thenReturn(accountId);
        return request;
    }
}
