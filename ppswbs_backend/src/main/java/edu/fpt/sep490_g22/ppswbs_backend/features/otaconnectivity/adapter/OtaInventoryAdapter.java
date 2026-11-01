package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaDeliveryResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaOutboundChange;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;

/**
 * Provider-neutral inventory publication port.
 * Delivers schedule, availability, and sales-close changes to external OTAs.
 */
public interface OtaInventoryAdapter {

    OtaProvider supportedProvider();

    default boolean supports(OtaProvider provider) {
        return supportedProvider() == provider;
    }

    default OtaDeliveryResult deliver(OtaChannelMapping mapping, OtaOutboundChange change) {
        return deliver(mapping, change, 1);
    }

    OtaDeliveryResult deliver(OtaChannelMapping mapping, OtaOutboundChange change, int attemptNumber);
}
