package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public record BookingIdempotencyClaim(
        Long recordId,
        Long claimVersion,
        boolean cachedOutcome,
        Integer statusCode,
        String responsePayload
) {
    public static BookingIdempotencyClaim cached(Integer statusCode, String responsePayload) {
        return new BookingIdempotencyClaim(null, null, true, statusCode, responsePayload);
    }

    public static BookingIdempotencyClaim live(Long recordId, Long claimVersion) {
        return new BookingIdempotencyClaim(recordId, claimVersion, false, null, null);
    }
}
