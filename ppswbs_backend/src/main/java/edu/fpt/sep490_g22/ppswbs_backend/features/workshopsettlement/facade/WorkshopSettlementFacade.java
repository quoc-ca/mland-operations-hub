package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

/** Public producer boundary owned by Workshop Settlement. */
public interface WorkshopSettlementFacade {
    CancellationHandoffOutcome openCancellationHandoff(OpenCancellationHandoffCommand command);

    SettlementPaymentEvidenceOutcome applyVerifiedSettlementPaymentEvidence(
            ApplyVerifiedSettlementPaymentEvidenceCommand command);

    /** Feature 007 calls this only after receiving the matching ready event. */
    SettlementNotificationDeliveryPayload getSettlementNotificationDeliveryPayload(
            SettlementNotificationDeliveryPayloadQuery query);
}
