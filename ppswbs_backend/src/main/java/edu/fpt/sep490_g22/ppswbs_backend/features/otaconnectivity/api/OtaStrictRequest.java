package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Each OTA request schema declares additionalProperties: false. */
public interface OtaStrictRequest {
    @JsonAnySetter
    default void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown request field");
    }
}
