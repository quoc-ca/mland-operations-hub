package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

public class RetailPickupException extends RuntimeException {
    public enum Reason { NOT_FOUND, INVALID_STATE }
    private final Reason reason;
    public RetailPickupException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
