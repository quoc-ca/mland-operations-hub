package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.io.InputStream;
import java.util.UUID;

/**
 * Lets another feature keep one of the uploaded catalogue images on its own record (003.2 FR-073). The owning
 * feature decides who may read the image; this boundary checks the stored object and the owner's reference.
 * Failures throw {@link SharedMediaException}.
 */
public interface SharedMediaFacade {

    record Content(String contentType, InputStream content) {
    }

    /** Joins the caller's transaction. The image must be READY and uploaded by {@code actorId}. */
    void attachOwned(String actorId, String ownerModule, String ownerRecordRef, UUID mediaId);

    /** Joins the caller's transaction. Releasing never deletes the object; catalogue cleanup decides later. */
    void release(String ownerModule, String ownerRecordRef, UUID mediaId);

    /** Opens the bytes of a READY image that the owner record actively references. */
    Content open(String ownerModule, String ownerRecordRef, UUID mediaId);
}
