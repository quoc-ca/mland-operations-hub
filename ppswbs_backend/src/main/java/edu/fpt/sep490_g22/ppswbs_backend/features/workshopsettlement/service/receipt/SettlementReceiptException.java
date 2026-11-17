package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt;

/**
 * A refused or failed receipt-image operation with the contract's named code (003.3 T154): {@code 415
 * RECEIPT_IMAGE_TYPE_UNSUPPORTED}, {@code 413 RECEIPT_IMAGE_TOO_LARGE}, {@code 503 RECEIPT_STORAGE_UNAVAILABLE}.
 */
public class SettlementReceiptException extends RuntimeException {

    public enum Failure {
        TYPE_UNSUPPORTED("RECEIPT_IMAGE_TYPE_UNSUPPORTED", 415),
        TOO_LARGE("RECEIPT_IMAGE_TOO_LARGE", 413),
        STORAGE_UNAVAILABLE("RECEIPT_STORAGE_UNAVAILABLE", 503);

        private final String code;
        private final int httpStatus;

        Failure(String code, int httpStatus) {
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String code() {
            return code;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    private final Failure failure;

    public SettlementReceiptException(Failure failure) {
        super(failure.code());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
