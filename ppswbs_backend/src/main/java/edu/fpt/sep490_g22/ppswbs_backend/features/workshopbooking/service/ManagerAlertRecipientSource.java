package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import java.util.List;

/**
 * Who receives the internal late-payment alert (003.1 FR-040a, decision 2026-11-13): every account with role
 * MANAGER and status ACTIVE, Admin excluded, no branch filter. The source of truth is FE-001
 * {@code AccountFacade.listActiveManagerEmails()}, wired by {@link AccountManagerAlertRecipientSource}. An empty list
 * means no alert email. The list is used inside the caller's transaction only, never in events, audit or logs.
 */
public interface ManagerAlertRecipientSource {

    List<String> activeManagerEmails();
}
