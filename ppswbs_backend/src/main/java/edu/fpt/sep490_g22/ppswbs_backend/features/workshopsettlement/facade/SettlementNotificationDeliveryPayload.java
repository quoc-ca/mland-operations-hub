package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

/**
 * Render-ready Settlement email for Feature 007. {@code renderedSubject} and {@code actionLink} are optional; the
 * link is a private path (for example the refund-account link) served only while it is still usable.
 */
public record SettlementNotificationDeliveryPayload(String notificationKind, String storedRecipient,
                                                    String renderedSettlementContent,
                                                    String correlationReference,
                                                    String renderedSubject,
                                                    String actionLink) {

    public SettlementNotificationDeliveryPayload(String notificationKind, String storedRecipient,
                                                 String renderedSettlementContent, String correlationReference) {
        this(notificationKind, storedRecipient, renderedSettlementContent, correlationReference, null, null);
    }
}
