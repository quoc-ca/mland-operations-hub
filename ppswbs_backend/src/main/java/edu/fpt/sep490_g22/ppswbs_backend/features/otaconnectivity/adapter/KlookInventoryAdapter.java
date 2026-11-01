package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChangeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class KlookInventoryAdapter extends BaseOtaInventoryAdapter {

    private static final String KLOOK_API_BASE = "https://api.klook.com/v1/supplier/inventory";

    @Autowired
    public KlookInventoryAdapter(OtaProviderTransport transport, ObjectMapper objectMapper) {
        super(transport, objectMapper);
    }

    public KlookInventoryAdapter(OtaProviderTransport transport, ObjectMapper objectMapper, int maxRetries) {
        super(transport, objectMapper, maxRetries);
    }

    @Override
    public OtaProvider supportedProvider() {
        return OtaProvider.KLOOK;
    }

    @Override
    protected String buildEndpointUrl(OtaChannelMapping mapping, OtaChangeKind changeKind) {
        String path = switch (changeKind) {
            case SCHEDULE -> "/schedule";
            case AVAILABILITY -> "/availability";
            case SALE_STATE -> "/sales-state";
        };
        return KLOOK_API_BASE + path;
    }
}

