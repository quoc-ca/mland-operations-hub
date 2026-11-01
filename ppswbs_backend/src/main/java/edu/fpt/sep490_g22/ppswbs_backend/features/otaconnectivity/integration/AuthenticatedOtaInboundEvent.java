package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;

/** Adapter output after provider authentication and payload allowlisting. Contains no raw payload. */
public record AuthenticatedOtaInboundEvent(
        OtaProvider provider,
        OtaEnvironment environment,
        String externalEventReference,
        String payloadFingerprint,
        String eventKind) {
}
