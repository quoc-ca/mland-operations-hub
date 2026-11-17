package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry resolving the appropriate cancellation adapter for a given OTA provider.
 */
@Component
public class OtaCancellationAdapterRegistry {

    private final List<OtaCancellationAdapter> adapters;

    @Autowired
    public OtaCancellationAdapterRegistry(List<OtaCancellationAdapter> adapters) {
        this.adapters = adapters != null ? adapters : List.of();
    }

    public Optional<OtaCancellationAdapter> findAdapter(OtaProvider provider) {
        if (provider == null) return Optional.empty();
        return adapters.stream()
                .filter(a -> a.supports(provider))
                .findFirst();
    }

    public OtaCancellationAdapter getRequiredAdapter(OtaProvider provider) {
        return findAdapter(provider)
                .orElseThrow(() -> new IllegalArgumentException("No cancellation adapter registered for provider " + provider));
    }
}

