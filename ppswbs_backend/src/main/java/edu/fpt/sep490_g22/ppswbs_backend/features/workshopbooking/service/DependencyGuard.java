package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;

import java.util.function.Supplier;

/**
 * Turns "Loyalty (006) or Operations (003.2) is unavailable" into the typed 503 {@code DEPENDENCY_UNAVAILABLE} of
 * 003.1 FR-031 (T132, T146, decided 2026-11-15). Unavailable means Loyalty answered {@code DEPENDENCY_UNAVAILABLE},
 * or the call failed on infrastructure (data access, transaction). Every business refusal, and any programming error,
 * passes through unchanged. Nothing is held: the callers run inside the transaction that this exception rolls back.
 */
@Slf4j
final class DependencyGuard {

    static final String CODE = "DEPENDENCY_UNAVAILABLE";

    private DependencyGuard() {
    }

    static <T> T call(String dependency, Supplier<T> action) {
        try {
            return action.get();
        } catch (LoyaltyException ex) {
            if (ex.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE) {
                throw unavailable(dependency, ex);
            }
            throw ex;
        } catch (DataAccessException | TransactionException ex) {
            throw unavailable(dependency, ex);
        }
    }

    private static WorkshopBookingProblemException unavailable(String dependency, RuntimeException cause) {
        log.warn("{} is unavailable: {}", dependency, cause.getClass().getSimpleName());
        return new WorkshopBookingProblemException(HttpStatus.SERVICE_UNAVAILABLE, CODE,
                "A required service is unavailable. Try again later.");
    }
}
