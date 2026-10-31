package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

/** Commercial state is derived from whether provider-confirmed price terms exist. */
public enum OtaCommercialStatus {
    PENDING_COMMERCIAL_CONFIRMATION,
    APPROVED;

    public static OtaCommercialStatus fromConfirmedSnapshot(boolean exists) {
        return exists ? APPROVED : PENDING_COMMERCIAL_CONFIRMATION;
    }
}
