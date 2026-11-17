package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AccountManagerAlertRecipientSourceTest {

    private final AccountFacade accountFacade = mock(AccountFacade.class);
    private final AccountManagerAlertRecipientSource source = new AccountManagerAlertRecipientSource(accountFacade);

    @Test
    @DisplayName("T094: Manager alert recipients come from FE-001 listActiveManagerEmails")
    void returnsTheActiveManagersFromAccountFacade() {
        when(accountFacade.listActiveManagerEmails()).thenReturn(List.of("a@mland.test", "b@mland.test"));

        assertThat(source.activeManagerEmails()).containsExactly("a@mland.test", "b@mland.test");
        verify(accountFacade).listActiveManagerEmails();
        verifyNoMoreInteractions(accountFacade);
    }

    @Test
    @DisplayName("T094: no active Manager gives an empty list, not an error")
    void noActiveManager_isEmpty() {
        when(accountFacade.listActiveManagerEmails()).thenReturn(List.of());

        assertThat(source.activeManagerEmails()).isEmpty();
    }

    @Test
    @DisplayName("T094: a null answer is treated as no recipient")
    void nullAnswer_isEmpty() {
        when(accountFacade.listActiveManagerEmails()).thenReturn(null);

        assertThat(source.activeManagerEmails()).isEmpty();
    }
}
