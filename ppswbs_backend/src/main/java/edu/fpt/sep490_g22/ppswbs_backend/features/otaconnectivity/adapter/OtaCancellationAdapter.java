package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;

import java.util.Map;

/**
 * Provider-specific cancellation/refund event adapter interface.
 * Translates inbound provider webhook payloads into domain cancellation commands
 * without guessing cancellation policies or executing unevidenced side-effects.
 */
public interface OtaCancellationAdapter {

    OtaProvider supportedProvider();

    default boolean supports(OtaProvider provider) {
        return supportedProvider() == provider;
    }

    /**
     * Translates an incoming provider cancellation payload into a domain command.
     * Enforces default-deny and fail-closed behavior:
     * - If provider evidence is missing, returns Unmapped/Rejected.
     * - If payload has forbidden PII or invalid structure, returns Rejected.
     * - If event kind is unknown/unmapped, returns Unmapped.
     */
    OtaCancellationMappingResult mapCancellationEvent(
            OtaChannelConnection connection,
            String rawJsonPayload,
            Map<String, String> headers
    );
}

