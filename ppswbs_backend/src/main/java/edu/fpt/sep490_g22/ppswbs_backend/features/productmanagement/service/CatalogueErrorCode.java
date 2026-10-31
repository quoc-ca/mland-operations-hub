package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import org.springframework.http.HttpStatus;

/**
 * Error codes of the productmanagement API, exactly as listed in the error contract of
 * {@code contracts/interfaces.md}. Messages are safe, generic texts.
 */
public enum CatalogueErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The request contains invalid values."),
    UNSUPPORTED_FILTER(HttpStatus.BAD_REQUEST, "The filter is not supported."),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "The sort option is not supported."),
    UNKNOWN_FIELD(HttpStatus.BAD_REQUEST, "The request contains an unknown or malformed field."),
    IMPORT_FILE_INVALID(HttpStatus.BAD_REQUEST, "The import file is not a valid template."),
    IMPORT_ROW_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "The import file has too many data rows."),

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Sign-in is required."),
    INVALID_CREDENTIAL(HttpStatus.UNAUTHORIZED, "The credential is invalid or expired."),

    CATALOGUE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission for this action."),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "The product was not found."),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "The size was not found."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "The category was not found."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "The image was not found."),
    PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "The price proposal was not found."),
    COMMAND_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "No confirmed result was found for this command."),
    IMPORT_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "The import batch was not found."),

    STALE_VERSION(HttpStatus.CONFLICT, "The data has changed. Reload and try again."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "A category with this name already exists."),
    PENDING_PRICE_PROPOSAL_EXISTS(HttpStatus.CONFLICT, "This size already has a pending price proposal."),
    REVIEW_NOT_CURRENT(HttpStatus.CONFLICT, "The review is no longer current."),
    PRICE_NOT_APPROVED(HttpStatus.CONFLICT, "The price has not been approved."),
    PRICE_TERMS_CHANGED(HttpStatus.CONFLICT, "The price terms have changed."),
    PRICE_APPLICATION_STALE(HttpStatus.CONFLICT, "A newer price has already been applied."),
    PUBLICATION_PREREQUISITES_MISSING(HttpStatus.CONFLICT, "The product does not meet the publication conditions."),
    QUANTITY_BELOW_HELD(HttpStatus.CONFLICT, "The quantity cannot be lower than the currently held quantity."),
    SIZE_LIST_IMMUTABLE(HttpStatus.CONFLICT, "Sizes cannot be added or changed after the product is saved."),
    PRODUCT_DELETED(HttpStatus.CONFLICT, "The product has been deleted."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "This command key was already used with different data."),
    IMPORT_HAS_ERRORS(HttpStatus.CONFLICT, "The import batch has errors and cannot be confirmed."),
    IMPORT_BATCH_EXPIRED(HttpStatus.CONFLICT, "The import preview has expired. Upload the file again."),
    IMPORT_PREVIEW_STALE(HttpStatus.CONFLICT, "The data changed since the preview. Validate the file again."),

    MEDIA_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The image is too large."),
    IMPORT_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The import file is too large."),

    MEDIA_TYPE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The image format is not supported."),
    IMPORT_FILE_TYPE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only .xlsx files are accepted."),

    IDENTITY_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification is temporarily unavailable."),
    AVAILABILITY_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Availability information is temporarily unavailable."),
    MEDIA_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is temporarily unavailable."),
    CATALOGUE_SAVE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The change could not be saved. Check the command result and retry with the same key."),
    CATALOGUE_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "The catalogue is busy. Retry with the same key.");

    private final HttpStatus status;
    private final String defaultMessage;

    CatalogueErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
