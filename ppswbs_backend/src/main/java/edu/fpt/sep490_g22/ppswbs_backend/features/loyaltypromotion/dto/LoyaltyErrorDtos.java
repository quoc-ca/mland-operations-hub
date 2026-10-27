package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Transport mapping of the loyalty error codes (interfaces section 3). The body itself is the shared
 * {@code common.ErrorResponse} envelope: {@code code, message, correlationId, timestamp, fieldErrors}.
 * 401 INVALID_TOKEN is produced by the identity/security layer, not here.
 */
public final class LoyaltyErrorDtos {

    private LoyaltyErrorDtos() {
    }

    public static HttpStatus status(LoyaltyErrorCode code) {
        return switch (code) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case ACCESS_DENIED, PRIVILEGED_AUTH_REQUIRED -> HttpStatus.FORBIDDEN;
            case PROMOTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CODE_EXISTS, STALE_VERSION, IDEMPOTENCY_CONFLICT, QUOTA_LIMIT_CONFLICT, BENEFIT_STATE_CONFLICT,
                    QUOTE_CHANGED, POINTS_LIMIT_CHANGED -> HttpStatus.CONFLICT;
            case PROMOTION_INELIGIBLE, POINTS_USE_NOT_ALLOWED, WORKSHOP_NET_AMOUNT_INVALID ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case DEPENDENCY_UNAVAILABLE, RETRYABLE_CONTENTION -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    /** Safe generic text; never carries entity, member data, token or raw source proof. */
    public static String defaultMessage(LoyaltyErrorCode code) {
        return switch (code) {
            case INVALID_INPUT -> "The request contains an invalid value.";
            case ACCESS_DENIED -> "You do not have access to this resource.";
            case PRIVILEGED_AUTH_REQUIRED -> "Stronger authentication is required for this action.";
            case PROMOTION_NOT_FOUND -> "The promotion was not found.";
            case CODE_EXISTS -> "This voucher code has already been issued.";
            case STALE_VERSION -> "The data changed. Reload the latest version and try again.";
            case IDEMPOTENCY_CONFLICT -> "This command key was already used with a different request.";
            case QUOTA_LIMIT_CONFLICT -> "The limit cannot be lower than the amount already used or held.";
            case BENEFIT_STATE_CONFLICT -> "The benefit is not in a state that allows this action.";
            case QUOTE_CHANGED -> "The price or conditions changed. Review the new total and confirm again.";
            case POINTS_LIMIT_CHANGED -> "The usable points changed. Review and confirm again.";
            case PROMOTION_INELIGIBLE -> "The promotion cannot be applied to this purchase.";
            case POINTS_USE_NOT_ALLOWED -> "Points cannot be used with this selection.";
            case WORKSHOP_NET_AMOUNT_INVALID -> "The amount after discounts must be greater than zero.";
            case DEPENDENCY_UNAVAILABLE -> "A required service is unavailable. Try again later.";
            case RETRYABLE_CONTENTION -> "The system is busy. Retry with the same request.";
        };
    }
}
