package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link ManagerAlertRecipientSource} backed by FE-001 {@link AccountFacade#listActiveManagerEmails()} (contract
 * {@code 001-account-management/contracts/interfaces.md}): every ACTIVE Manager, Admin excluded. An empty answer
 * means no alert email; the review still waits in the Manager queue. The addresses are returned to the caller only
 * and are never logged here.
 */
@Component
@RequiredArgsConstructor
class AccountManagerAlertRecipientSource implements ManagerAlertRecipientSource {

    private final AccountFacade accountFacade;

    @Override
    public List<String> activeManagerEmails() {
        List<String> emails = accountFacade.listActiveManagerEmails();
        return emails == null ? List.of() : emails;
    }
}
