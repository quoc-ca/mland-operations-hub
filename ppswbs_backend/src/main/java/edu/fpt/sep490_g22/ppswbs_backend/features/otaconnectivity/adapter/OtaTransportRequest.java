package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import java.util.Map;

public record OtaTransportRequest(
        String endpointUrl,
        String method,
        Map<String, String> headers,
        String bodyPayload
) {
    public OtaTransportRequest {
        if (headers == null) {
            headers = Map.of();
        }
    }
}

