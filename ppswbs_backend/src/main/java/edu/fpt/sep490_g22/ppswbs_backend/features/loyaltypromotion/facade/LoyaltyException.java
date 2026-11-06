package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.util.List;

/**
 * Safe, typed failure crossing the loyalty boundary. The message never carries a stack trace, token,
 * member data or raw source proof.
 */
public class LoyaltyException extends RuntimeException {

    private final LoyaltyErrorCode code;
    private final List<FieldIssue> fieldIssues;

    public LoyaltyException(LoyaltyErrorCode code, String safeMessage) {
        this(code, safeMessage, List.of());
    }

    public LoyaltyException(LoyaltyErrorCode code, String safeMessage, List<FieldIssue> fieldIssues) {
        super(safeMessage);
        this.code = code;
        this.fieldIssues = fieldIssues == null ? List.of() : List.copyOf(fieldIssues);
    }

    public static LoyaltyException invalidField(String field, String safeMessage) {
        return new LoyaltyException(LoyaltyErrorCode.INVALID_INPUT, "The request contains an invalid value.",
                List.of(new FieldIssue(field, safeMessage)));
    }

    public LoyaltyErrorCode code() {
        return code;
    }

    public List<FieldIssue> fieldIssues() {
        return fieldIssues;
    }

    public record FieldIssue(String field, String message) {
    }
}
