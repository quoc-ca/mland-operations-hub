package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;

public record PublishedPackageSelectionQuery(String packageReference) {
    public PublishedPackageSelectionQuery {
        Objects.requireNonNull(packageReference, "packageReference must not be null");
        if (packageReference.isBlank()) {
            throw new IllegalArgumentException("packageReference must not be blank");
        }
    }
}
