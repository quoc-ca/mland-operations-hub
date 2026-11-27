package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SharedMediaException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SharedMediaFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** {@link SharedMediaFacade} over the media reference registry and the application-streamed media read. */
@Service
@RequiredArgsConstructor
public class SharedMediaService implements SharedMediaFacade {

    private final MediaReferenceService references;
    private final MediaReferenceRepository referenceRows;
    private final PublicMediaService media;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachOwned(String actorId, String ownerModule, String ownerRecordRef, UUID mediaId) {
        translate(() -> {
            references.attachOwnedMedia(actorId, ownerModule, ownerRecordRef, List.of(mediaId), now());
            return null;
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(String ownerModule, String ownerRecordRef, UUID mediaId) {
        translate(() -> {
            references.release(ownerModule, ownerRecordRef, List.of(mediaId), now());
            return null;
        });
    }

    @Override
    public Content open(String ownerModule, String ownerRecordRef, UUID mediaId) {
        return translate(() -> {
            PublicMediaService.PublicMedia image = media.openOwned(mediaId.toString(), () -> referenceRows
                    .findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(mediaId, ownerModule, ownerRecordRef)
                    .map(MediaReference::isActive)
                    .orElse(false));
            return new Content(image.contentType(), image.content());
        });
    }

    private static <T> T translate(Supplier<T> work) {
        try {
            return work.get();
        } catch (CatalogueException failure) {
            throw new SharedMediaException(failure.code() == CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE
                    ? SharedMediaException.Reason.DEPENDENCY_UNAVAILABLE
                    : SharedMediaException.Reason.NOT_FOUND, failure);
        }
    }

    private static Instant now() {
        return CatalogueValues.toStoredInstant(Instant.now());
    }
}
