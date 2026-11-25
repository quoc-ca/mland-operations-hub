package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

/**
 * Named interface `accountmanagement :: invitationdelivery` exposed for FE-007.
 * Provides transient setup link materialization and receives attempt status callbacks.
 */
public interface InvitationDeliveryFacade {

    /**
     * Materializes the setup URL transiently for an outgoing SMTP attempt or ADMIN manual retrieval.
     */
    MaterializationOutcome materialize(MaterializationRequest request);

    /**
     * Idempotently reports delivery outcome without altering creation-based expiration.
     */
    void reportDeliveryState(InvitationDeliveryReport report);
}
