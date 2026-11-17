package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import java.util.Map;

public record OtaTransportResponse(
        int statusCode,
        String body,
        Map<String, String> headers
) {
    public OtaTransportResponse {
        if (headers == null) {
            headers = Map.of();
        }
    }
}

