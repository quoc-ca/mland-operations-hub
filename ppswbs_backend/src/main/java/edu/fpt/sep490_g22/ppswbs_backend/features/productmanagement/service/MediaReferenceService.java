package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Media reference registry (T016, data-model {@code catalogue_media_references}). Every method joins the
 * caller's transaction, locks the media rows in ascending ID order and refuses objects that are being
 * deleted. Releasing a reference never deletes the object; cleanup decides later from the registry.
 */
@Service
@RequiredArgsConstructor
public class MediaReferenceService {

    public static final String OWNER_MODULE = "productmanagement";

    private final CatalogueMediaRepository media;
    private final MediaReferenceRepository references;

    /** Owner reference of a product's current images, for example {@code product:123}. */
    public static String productRef(long productId) {
        return "product:" + productId;
    }

    /**
     * Attaches newly chosen images: each must be READY and uploaded by the acting user, so nobody can
     * attach another person's staged file.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachOwnedMedia(String actorId, String ownerModule, String ownerRecordRef,
                                 Collection<UUID> mediaIds, Instant at) {
        Map<UUID, CatalogueMediaObject> locked = lockAll(mediaIds);
        for (CatalogueMediaObject object : locked.values()) {
            if (!object.canBeAttached() || !object.getUploadedBy().equals(actorId)) {
                throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
            }
        }
        link(locked.values(), ownerModule, ownerRecordRef, at);
    }

    /**
     * Retains media for history (a review snapshot, or FE-08 through the facade); the uploader does not
     * matter, but an object being deleted cannot be retained.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retain(String ownerModule, String ownerRecordRef, Collection<UUID> mediaIds, Instant at) {
        Map<UUID, CatalogueMediaObject> locked = lockAll(mediaIds);
        for (CatalogueMediaObject object : locked.values()) {
            if (object.getState() == CatalogueMediaObject.State.DELETE_PENDING
                    || object.getState() == CatalogueMediaObject.State.DELETED
                    || object.getState() == CatalogueMediaObject.State.STAGED) {
                throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
            }
        }
        link(locked.values(), ownerModule, ownerRecordRef, at);
    }

    /** Releases the owner's references; an object with no active reference left starts its 30-day clock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(String ownerModule, String ownerRecordRef, Collection<UUID> mediaIds, Instant at) {
        Map<UUID, CatalogueMediaObject> locked = lockAll(mediaIds);
        for (CatalogueMediaObject object : locked.values()) {
            references.findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(object.getId(), ownerModule, ownerRecordRef)
                    .ifPresent(reference -> reference.release(at));
            references.flush();
            if (!references.existsByMediaObjectIdAndReleasedAtIsNull(object.getId())) {
                object.recordUnreferenced(at);
            }
        }
    }

    private void link(Collection<CatalogueMediaObject> objects, String ownerModule, String ownerRecordRef, Instant at) {
        for (CatalogueMediaObject object : objects) {
            MediaReference reference = references
                    .findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(object.getId(), ownerModule, ownerRecordRef)
                    .orElse(null);
            if (reference == null) {
                references.save(MediaReference.link(object.getId(), ownerModule, ownerRecordRef, at));
            } else {
                reference.reactivate(at);
            }
            if (object.canBeAttached()) {
                object.recordAttached(at);
            }
        }
    }

    private Map<UUID, CatalogueMediaObject> lockAll(Collection<UUID> mediaIds) {
        Objects.requireNonNull(mediaIds, "mediaIds");
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ascending = List.copyOf(new TreeSet<>(mediaIds));
        Map<UUID, CatalogueMediaObject> locked = media.lockAllByIdIn(ascending).stream()
                .collect(Collectors.toMap(CatalogueMediaObject::getId, Function.identity()));
        if (!locked.keySet().containsAll(ascending)) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_NOT_FOUND);
        }
        return locked;
    }
}
