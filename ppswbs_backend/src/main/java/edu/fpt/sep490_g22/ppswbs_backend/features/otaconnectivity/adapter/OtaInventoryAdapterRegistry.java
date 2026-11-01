package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OtaInventoryAdapterRegistry {

    private final Map<OtaProvider, OtaInventoryAdapter> adapters;

    public OtaInventoryAdapterRegistry(List<OtaInventoryAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(OtaInventoryAdapter::supportedProvider, Function.identity()));
    }

    public Optional<OtaInventoryAdapter> findAdapter(OtaProvider provider) {
        return Optional.ofNullable(adapters.get(provider));
    }

    public OtaInventoryAdapter getRequiredAdapter(OtaProvider provider) {
        return findAdapter(provider)
                .orElseThrow(() -> new IllegalArgumentException("No adapter registered for provider: " + provider));
    }
}

