package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

public class WorkshopRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public WorkshopRateLimitException(String message) { this(message, 0); }

    /** @param retryAfterSeconds whole seconds until the window ends (at least 1), or 0 when unknown. */
    public WorkshopRateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
