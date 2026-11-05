package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

public record SettlementNotificationDeliveryPayload(String notificationKind, String storedRecipient,
                                                    String renderedSettlementContent,
                                                    String correlationReference) { }
