package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;

/**
 * Counter cash of a store-pickup order, for the fulfilment module: Staff confirms that the customer paid exactly the
 * amount due (Session 2026-11-20: no cash-received or change entry). Runs in the caller's transaction, so the payment
 * and the handover recorded by the caller commit together or not at all. Idempotent per Staff account and key.
 */
public interface RetailCounterCashFacade {
    CashCollected collectExactAmount(CurrentAccountActor actor, String orderId, String idempotencyKey);

    record CashCollected(String orderId, String appliedAmount) { }

    /** Why the cash could not be recorded; {@code status} is the HTTP status to answer with. */
    final class Refused extends RuntimeException {
        private final int status;
        public Refused(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
