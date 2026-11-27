package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

/** Safe failure of {@link SharedMediaFacade}. */
public class SharedMediaException extends RuntimeException {

    public enum Reason {
        /** Unknown, not ready, being deleted, uploaded by someone else, or not referenced by this owner. */
        NOT_FOUND,
        /** The storage provider cannot be reached right now. */
        DEPENDENCY_UNAVAILABLE
    }

    private final Reason reason;

    public SharedMediaException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
