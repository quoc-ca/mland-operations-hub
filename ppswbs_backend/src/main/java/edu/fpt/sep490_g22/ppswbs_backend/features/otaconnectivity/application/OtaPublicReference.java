package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import java.util.UUID;

final class OtaPublicReference {
    private OtaPublicReference() {}

    static String require(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("A public UUID reference is required");
        }
        return UUID.fromString(value).toString();
    }
}
