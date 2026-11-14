package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetailAuthorizationTest {

    private final RetailAuthorization authorization = new RetailAuthorization();

    @Test
    void resolvesOnlyTheAuthenticatedActiveMember() {
        HttpServletRequest request = requestWith(new CurrentAccountActor(
            91L, ActorRole.MEMBER, ActorStatus.ACTIVE, false, List.of()
        ));

        RetailMemberActor actor = authorization.requireActiveMember(request);

        assertThat(actor.accountId()).isEqualTo(91L);
    }

    @Test
    void rejectsMissingOrMalformedAuthenticatedActor() {
        assertThatThrownBy(() -> authorization.requireActiveMember(requestWith(null)))
            .isInstanceOf(RetailUnauthorizedException.class);
        assertThatThrownBy(() -> authorization.requireActiveMember(requestWith("not-an-actor")))
            .isInstanceOf(RetailUnauthorizedException.class);
    }

    @Test
    void rejectsNonMemberOrInactiveActor() {
        assertThatThrownBy(() -> authorization.requireActiveMember(requestWith(new CurrentAccountActor(
            92L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(10L)
        )))).isInstanceOf(RetailAccessDeniedException.class);

        assertThatThrownBy(() -> authorization.requireActiveMember(requestWith(new CurrentAccountActor(
            93L, ActorRole.MEMBER, ActorStatus.SUSPENDED, true, List.of()
        )))).isInstanceOf(RetailAccessDeniedException.class);

        assertThatThrownBy(() -> authorization.requireActiveMember(requestWith(new CurrentAccountActor(
            94L, ActorRole.MEMBER, ActorStatus.PENDING_SETUP, true, List.of()
        )))).isInstanceOf(RetailAccessDeniedException.class);
    }

    private HttpServletRequest requestWith(Object actor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RetailAuthorization.CURRENT_ACTOR_ATTRIBUTE)).thenReturn(actor);
        return request;
    }
}
